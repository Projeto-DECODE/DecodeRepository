# Arquitetura

## Visão geral

Três aplicações Spring Boot independentes e um PostgreSQL. Cada serviço tem seus próprios controllers, serviços, repositórios, entidades e migrations. Nenhuma entidade JPA é compartilhada, e nenhum serviço acessa tabelas de outro (as roles do banco impedem isso).

## Propriedade dos dados

| Serviço | Schema | Role | Guarda |
|---|---|---|---|
| auth-service | `auth` | `auth_svc` | usuários e hash de senha |
| bucket-service | `bucket` | `bucket_svc` | buckets, dono, status |
| object-service | `objects` | `object_svc` | metadados de objetos (e os bytes no volume) |

`ownerUserId` no bucket-service é o `sub` do JWT, sem chave estrangeira para `auth.users`. É o preço da independência: o bucket-service confia na assinatura do token, não em uma consulta à tabela de usuários.

## Decisões e justificativas

### 1. JWT RS256 com JWKS e `kid`
A chave privada fica só no auth-service. Os demais validam com a pública. O `kid` permite rotacionar chaves no futuro sem mudar os consumidores.

### 2. Sentido assimétrico nas chamadas entre serviços
O object-service precisa do bucket-service (existência e dono). O bucket-service precisa do object-service (bucket vazio antes de excluir). Se ambos encaminhassem o token do usuário, a exclusão geraria `bucket -> object -> bucket`, com risco de travamento.

- **object -> bucket** usa o token do usuário e a rota pública `GET /api/buckets/{id}`. A regra de propriedade vive só no bucket-service.
- **bucket -> object** usa um token de serviço em `/internal/**`. Essa rota só conta registros e não chama ninguém de volta.

### 3. Token de serviço (client credentials)
O bucket-service obtém no auth-service um token com `sub=bucket-service`, `aud=sos-internal`, `scope=internal` e TTL curto (5 min por padrão). Escopos separam as superfícies: `/api/**` exige `api`, `/internal/**` exige `internal`. Foi preferido a um segredo compartilhado simples porque o token expira, é verificável por assinatura e não trafega o segredo em cada chamada.

### 4. 404 em vez de 403 para recursos de outro usuário
Bucket ou objeto alheio é indistinguível de inexistente, o que evita enumeração de IDs. O `403` fica reservado para escopo incorreto (por exemplo, token de usuário em rota interna).

### 5. Status `ACTIVE | DELETING` no bucket
Reduz a corrida entre upload e exclusão. Não a elimina (ver README, seção 3.5).

### 6. Busca sempre por par de chaves
Objetos são buscados por `(objectId, bucketId)` e buckets por `(bucketId, ownerUserId)`. Os repositórios não expõem busca só por ID nesses fluxos, o que torna o IDOR/BOLA estruturalmente difícil.

### 7. Falha fechada e sem cache de autorização
Qualquer erro ao consultar o bucket-service (rede, timeout, 5xx, resposta vazia) vira `503`. Nunca se presume "tem acesso" nem "bucket vazio". Não há cache da resposta, para não manter permissão obsoleta. O custo é uma chamada HTTP por operação.

### 8. Armazenamento em arquivo com streaming
Upload em streaming para temporário com SHA-256 calculado em linha, depois `ATOMIC_MOVE`. Nada vai inteiro para a memória. O caminho físico usa só UUIDs gerados no servidor.

### 9. Serviços sem `@Transactional` onde há I/O externo
`BucketService.delete` e `ObjectService` não seguram transação de banco durante chamadas HTTP ou operações de disco. Cada transição de estado é atômica e confirmada individualmente.

### 10. Módulos Maven independentes
Sem POM pai nem biblioteca comum. Duplicar ApiError, filtros e validadores é aceito para que cada serviço possa ser construído e evoluído separadamente.

## Vantagens e limitações desta abordagem educacional

**Vantagens:** fronteiras claras, fácil de ler, cada serviço testável de forma isolada, modelo de segurança explícito.

**Limitações:** duplicação de código; uma chamada HTTP extra por operação no object-service; consistência entre serviços é "melhor esforço" (ver limitações no README); instância única de tudo; sem gateway nem TLS.

## Estrutura do repositório

```
simple-object-storage/
├── auth-service/     (controller, service, domain, repository, dto, security, config, exception)
├── bucket-service/   (idem + client/ para o object-service)
├── object-service/   (idem + client/ para o bucket-service + storage/)
├── docker/postgres/init-databases.sh
├── docker-compose.yml, .env.example, .gitignore, README.md
├── docs/ (architecture.md, api-examples.md, security.md)
└── scripts/generate-keys.sh
```
