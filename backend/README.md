# simple-object-storage

Clone educacional e simplificado de um armazenamento de objetos (inspirado no MinIO), dividido em três microsserviços com autenticação JWT e autorização por proprietário.

> **Estado da validação:** o código foi escrito em um ambiente sem acesso ao Maven Central e sem daemon Docker. Isso significa que **a compilação, os testes e a subida do Compose ainda não foram executados**. A seção [Validação](#validação-o-que-foi-e-o-que-não-foi-verificado) lista exatamente o que foi e o que não foi verificado, e os comandos para fechar essa lacuna na sua máquina.

## 1. Objetivos educacionais

- Separar responsabilidades em microsserviços independentes, cada um dono dos seus dados.
- Entender autenticação com JWT assimétrico (RS256), JWKS e validação por Resource Server.
- Praticar autorização por recurso e a prevenção de IDOR/BOLA.
- Trabalhar com upload em streaming, armazenamento em disco e consistência entre banco e arquivos.
- Conhecer os limites de uma arquitetura simples antes de levá-la a produção.

## 2. Tecnologias

Java 21, Spring Boot 3.5, Maven, Spring Web, Spring Data JPA, PostgreSQL 16, Flyway, Spring Security (OAuth2 Resource Server), Springdoc OpenAPI, JUnit 5, Mockito, Testcontainers, Docker e Docker Compose.

## 3. Arquitetura

| Serviço | Porta | Responsabilidade | Não faz |
|---|---|---|---|
| `auth-service` | 8081 | usuários, hash de senha (Argon2id), emissão de JWT, JWKS, token de serviço | não conhece buckets nem objetos |
| `bucket-service` | 8082 | buckets e propriedade (`ownerUserId`), status do bucket | não toca em arquivos |
| `object-service` | 8083 | metadados e bytes dos objetos | não decide propriedade: pergunta ao bucket-service |

```
                 Cliente (Swagger / curl)
              |            |              |
      POST /api/auth/*  /api/buckets/**  /api/buckets/{id}/objects/**
              v            v              v
       +--------------+ +---------------+ +----------------+
       | auth-service | | bucket-service| | object-service |
       |    :8081     | |     :8082     | |     :8083      |
       +------+-------+ +---+-------+---+ +---+--------+---+
   chave      |  JWKS (chave pública)  |         |        |
   privada    +<-----------+-----------+---------+        |
   (arquivo)                |                    |        |
                            |  token do usuário  |        |
                            |<-------------------+        |
                            |  (object -> bucket)         |
                            |                             |
                            +---------------------------->+
                              token de serviço            |
                              (bucket -> object)     +----v-----+
                                                     | volume   |
       +--------------------------------------+      | objects_ |
       | PostgreSQL (1 instância, não exposta)|      | data     |
       | schemas: auth | bucket | objects     |      +----------+
       | 3 roles, 1 por schema                |
       +--------------------------------------+
```

Os três projetos Maven são independentes, sem POM pai e sem biblioteca compartilhada. DTOs de erro e filtros pequenos são duplicados de propósito, para manter a independência. O custo é a repetição.

### 3.1. Autenticação (JWT RS256)

1. `POST /api/auth/register` cria o usuário (senha com Argon2id).
2. `POST /api/auth/login` valida as credenciais e devolve um access token.
3. O token tem `sub` (UUID do usuário), `iat`, `exp`, `iss`, `aud=sos-api`, `scope=api`, `jti` e header `kid`. Não contém e-mail, nome nem senha.
4. O `auth-service` assina com a chave privada. Os demais serviços buscam a chave pública em `/.well-known/jwks.json` e validam assinatura, `exp` (sem tolerância de relógio), `iss` e `aud`.
5. O TTL padrão é 15 minutos. **Acima de 30 minutos o `auth-service` não inicia** (validado no construtor da configuração e novamente na emissão).
6. Não há refresh token. Token expirado resulta em `401`, e o cliente faz login de novo.

### 3.2. Comunicação entre serviços

- **object -> bucket:** encaminha o **token do usuário** para `GET /api/buckets/{id}`. O bucket-service aplica a regra de propriedade, então a lógica de autorização não é duplicada.
- **bucket -> object:** usa um **token de serviço** (client credentials, `aud=sos-internal`, `scope=internal`) em `GET /internal/buckets/{id}/objects/count`. Essa rota não consulta o bucket-service de volta, o que evita o ciclo de chamadas na exclusão.
- Rotas `/api/**` exigem `scope=api`. Rotas `/internal/**` exigem `scope=internal`. Token de usuário em rota interna recebe `403`.
- Falha fechada: se o bucket-service estiver fora do ar, o object-service responde `503`, nunca libera o acesso. Não há cache de autorização.

### 3.3. Fluxo de criação de bucket

`POST /api/buckets` com `{"name": "documentos"}`. O proprietário é sempre o `sub` do token. O corpo não tem campo de dono, e um campo extra enviado pelo cliente é ignorado. A unicidade é `UNIQUE(owner_user_id, name)`.

### 3.4. Fluxo de upload e download

Upload (`POST /api/buckets/{id}/objects`, campo `file`):
1. Valida o JWT e consulta o bucket no bucket-service com o token do usuário. `404` se o bucket não existe ou é de outro usuário. `409` se o bucket está em `DELETING`.
2. Faz streaming do arquivo para um temporário, calculando SHA-256 e aplicando o limite de tamanho.
3. Move o arquivo para o destino final (`ATOMIC_MOVE`) e grava os metadados. Se o insert falhar, apaga o arquivo.

Download: valida o bucket, busca o objeto por `(objectId, bucketId)` e devolve o conteúdo com `Content-Type: application/octet-stream`, `Content-Disposition: attachment` com nome sanitizado e `X-Content-Type-Options: nosniff`.

### 3.5. Exclusão de bucket (e a corrida com uploads)

O bucket tem status `ACTIVE | DELETING`. A exclusão marca `DELETING` (o object-service passa a recusar uploads com `409`), pergunta a contagem ao object-service e só então exclui. Se houver objetos, volta para `ACTIVE` e responde `409`. Se o object-service estiver indisponível, volta para `ACTIVE` e responde `503`.

**Limitação real:** resta uma janela mínima, de um upload que validou o bucket (`ACTIVE`) um instante antes da marcação `DELETING` e que grava o objeto depois da contagem. Ela foi reduzida, **não eliminada**. Eliminá-la exigiria uma reserva transacional entre os serviços (por exemplo, um padrão de saga ou lease), o que foge do escopo educacional.

## 4. Persistência e armazenamento

**Banco:** uma instância PostgreSQL com três schemas (`auth`, `bucket`, `objects`) e três roles, cada uma com privilégios somente no próprio schema. O schema do object-service se chama `objects` (e não `object`) para evitar conflito com palavra reservada. O Flyway de cada serviço roda com a role dona do schema. O PostgreSQL **não** publica porta no host.

**Arquivos:** volume Docker `objects_data` montado em `/data/objects` no object-service. Caminho: `/data/objects/{bucketId}/{objectId[0..2]}/{objectId}`. O `objectId` é um UUID gerado no servidor, e o nome original do cliente é só metadado no banco. Todo caminho resolvido passa por `normalize()` e por verificação de `startsWith(raiz)`.

**Consistência banco/disco:**
- Upload: arquivo primeiro, metadados depois. Falha no insert apaga o arquivo.
- Exclusão: remove a linha e depois o arquivo. Se apagar o arquivo falhar, sobra um arquivo órfão (lixo sem referência, nunca dado exposto), registrado em log. Uma queda de processo entre os passos também pode deixar órfãos. A reconciliação periódica é uma evolução recomendada.

## 5. Pré-requisitos

- Docker e Docker Compose v2
- `openssl` (para gerar as chaves)
- Para rodar testes localmente fora do Docker: JDK 21 e Maven 3.9+. Os testes `*IT` usam Testcontainers e **exigem Docker**.

## 6. Configuração

```bash
cp .env.example .env          # edite e troque todos os valores "change-me"
./scripts/generate-keys.sh    # gera ./keys/private.pem e ./keys/public.pem
```

Gere segredos fortes com `openssl rand -base64 36`.

| Variável | Uso |
|---|---|
| `POSTGRES_ADMIN_USER` / `POSTGRES_ADMIN_PASSWORD` | superusuário do PostgreSQL (só para inicialização) |
| `AUTH_DB_PASSWORD`, `BUCKET_DB_PASSWORD`, `OBJECT_DB_PASSWORD` | senha da role de cada serviço |
| `BUCKET_SERVICE_CLIENT_SECRET` | segredo do client credentials (mínimo 32 caracteres) |
| `JWT_USER_TTL` | TTL do token de usuário (padrão `15m`, máximo `30m`) |
| `MAX_FILE_SIZE` / `MAX_REQUEST_SIZE` | limites de upload (padrão `50MB` / `51MB`) |

### 6.1. Chaves de assinatura

- A chave privada (`keys/private.pem`) é montada **apenas** no `auth-service`, como arquivo somente leitura via `secrets` do Compose. Não entra na imagem nem no repositório (`keys/` e `*.pem` estão no `.gitignore`).
- O diretório `keys/` tem permissão `700` e o arquivo `644`, porque o container roda como usuário não-root e precisa lê-lo. Em um host compartilhado, avalie isso. Em produção, use um cofre de segredos.
- Bucket-service e object-service só conhecem a chave pública, obtida via JWKS.
- Para trocar a chave: `./scripts/generate-keys.sh --force` e reinicie. Todos os tokens emitidos antes deixam de valer.

## 7. Executando

```bash
docker compose build                  # constrói os serviços
docker compose up -d                  # inicia tudo
docker compose ps                     # estado e health checks
docker compose logs -f                # logs de todos
docker compose logs -f object-service # logs de um serviço
docker compose stop                   # para sem remover
docker compose restart                # reinicia (dados preservados)
docker compose down                   # remove containers, MANTÉM volumes
```

O `up` espera o PostgreSQL e o `auth-service` ficarem saudáveis. A primeira subida leva mais tempo por causa do build Maven.

### 7.1. Endereços

As portas são publicadas apenas em `127.0.0.1`.

| Serviço | API | Swagger UI | Health |
|---|---|---|---|
| auth-service | http://localhost:8081 | http://localhost:8081/swagger-ui.html | `/actuator/health` |
| bucket-service | http://localhost:8082 | http://localhost:8082/swagger-ui.html | `/actuator/health` |
| object-service | http://localhost:8083 | http://localhost:8083/swagger-ui.html | `/actuator/health` |

O Swagger UI é público para que você possa abrir a documentação, mas **as APIs continuam protegidas**. Use o botão **Authorize** com o token obtido no login (somente o token, sem o prefixo `Bearer`).

## 8. Exemplo de uso

Exemplos completos em [`docs/api-examples.md`](docs/api-examples.md). Resumo:

```bash
curl -s -X POST localhost:8081/api/auth/register -H 'Content-Type: application/json' \
  -d '{"name":"Maria Silva","email":"maria@example.com","password":"uma-senha-forte-123"}'

TOKEN=$(curl -s -X POST localhost:8081/api/auth/login -H 'Content-Type: application/json' \
  -d '{"email":"maria@example.com","password":"uma-senha-forte-123"}' | jq -r .accessToken)

curl -s -X POST localhost:8082/api/buckets -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' -d '{"name":"documentos"}'
```

## 9. Testes

```bash
# Unitários (não exigem Docker):
mvn -f auth-service/pom.xml test
mvn -f bucket-service/pom.xml test
mvn -f object-service/pom.xml test

# Unitários + integração com PostgreSQL via Testcontainers (exigem Docker):
mvn -f auth-service/pom.xml verify
mvn -f bucket-service/pom.xml verify
mvn -f object-service/pom.xml verify
```

Cobertura resumida:
- **auth:** cadastro, e-mail inválido/duplicado, hash Argon2id, login, claims do JWT, TTL máximo de 30 min, token expirado, assinatura inválida, JWKS sem parte privada, token de serviço.
- **bucket:** criação, listagem só do dono, `404` para bucket alheio, nomes inválidos e duplicados, exclusão de bucket vazio/não vazio, falha fechada com object-service fora do ar, validação de token (expirado, chave errada, audiência errada, sem escopo).
- **object:** upload, limite de tamanho (`413`), arquivo vazio (`400`), path traversal, listagem paginada, metadados, download, exclusão, objeto de outro bucket, bucket alheio, rota interna só com token de serviço, persistência após "reinício" (nova instância sobre o mesmo diretório), cliente HTTP real contra servidor HTTP de teste (timeout, 500, recusa de conexão).

**Lacuna conhecida nos testes:** não há um teste automatizado que suba os três serviços juntos. Os testes de integração isolam o serviço vizinho (por mock do cliente ou servidor HTTP de teste). O fluxo real entre os três é verificado manualmente com o roteiro de [`docs/api-examples.md`](docs/api-examples.md) depois do `docker compose up`.

## 10. Logs

```bash
docker compose logs -f auth-service bucket-service object-service
docker compose logs --since 10m object-service | grep 'op=upload'
```

Cada linha tem o serviço e um `traceId` (cabeçalho `X-Request-Id`, propagado entre serviços e devolvido na resposta e no corpo de erro). Para investigar uma falha, pegue o `traceId` do erro e filtre por ele em todos os serviços. Os logs não contêm senhas, hashes, tokens nem conteúdo de arquivos.

## 11. Dados, reinício e limpeza

- `docker compose restart` e `docker compose down` **preservam** os dados (volumes `pgdata` e `objects_data`).
- `docker compose down -v` **apaga todos os dados**: usuários, buckets, metadados e arquivos. É irreversível.
- Os scripts de inicialização do banco só rodam na primeira vez (volume `pgdata` vazio). Se mudar as senhas do banco no `.env` depois disso, as roles existentes continuam com as senhas antigas. Altere-as com `ALTER ROLE` ou recrie o volume (perdendo os dados).
- Faça backup antes de remover volumes. Os dois volumes precisam ser salvos juntos, porque banco e arquivos se referenciam.

## 12. Validação: o que foi e o que não foi verificado

**Verificado neste ambiente:**
- Sintaxe Java dos três serviços (parse, sem resolver dependências).
- `docker compose config` válido.
- Sintaxe dos scripts shell e geração da chave (formato PKCS#8, que é o que o `auth-service` lê).

**NÃO verificado (o ambiente não tinha Maven Central nem daemon Docker):**
- Compilação com Java 21 e resolução das dependências. As versões usadas (Spring Boot 3.5.6, springdoc 2.8.13, BouncyCastle 1.80) foram escolhidas de memória e podem precisar de ajuste.
- Execução de **todos** os testes, unitários e de integração.
- Build das imagens e subida do Compose, incluindo o script de init do PostgreSQL, as migrations Flyway e os health checks.
- O fluxo end-to-end completo.

Para fechar a lacuna na sua máquina, nesta ordem:

```bash
mvn -f auth-service/pom.xml verify && mvn -f bucket-service/pom.xml verify && mvn -f object-service/pom.xml verify
cp .env.example .env && ./scripts/generate-keys.sh   # ajuste o .env antes
docker compose up -d --build
```

Depois, rode o roteiro de `docs/api-examples.md`. Erros de compilação ou de teste devem ser esperados em uma primeira execução de código que ainda não passou por um compilador, e são rápidos de corrigir.

## 13. Limitações conhecidas

- Sem revogação de token: um token roubado vale até expirar (no máximo 30 min).
- Sem rate limit nem bloqueio de tentativas no login.
- `/internal/**` é alcançável nas portas publicadas (não há gateway). A proteção é o escopo do token, não a topologia de rede.
- `aud=sos-api` é compartilhado entre bucket-service e object-service: um serviço comprometido poderia reutilizar o token de um usuário contra o outro.
- Janela residual na exclusão de bucket (ver 3.5).
- Arquivos órfãos possíveis em falha parcial (ver 4).
- Sem verificação de vírus, sem cota por usuário, sem versionamento, sem multipart upload resumível.
- Tráfego entre serviços e para o cliente em HTTP simples (sem TLS) no ambiente local.
- Uma única instância de cada serviço, sem alta disponibilidade.

## 14. Decisões arquiteturais

Detalhadas em [`docs/architecture.md`](docs/architecture.md) e [`docs/security.md`](docs/security.md).

## 15. Evolução recomendada

TLS e gateway de API; refresh token com rotação e revogação; rate limiting; cofre de segredos e rotação de chaves via `kid`; reconciliação periódica de arquivos órfãos; armazenamento em S3 compatível; saga para a exclusão de buckets; tracing distribuído (OpenTelemetry); CI com testes end-to-end sobre o Compose.
