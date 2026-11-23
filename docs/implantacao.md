# Guia de implantação

Como o DECODE é implantado e operado na AWS: arquitetura do ambiente, fluxo de CI/CD, implantação manual, operação do dia a dia e limitações conhecidas.

> Este documento não contém segredos. Endereços, senhas e chaves ficam fora do Git (veja [Segredos](#segredos)).

## 1. Visão geral

```
 Desenvolvedor
      | Pull Request (1 aprovação + 5 checks)
      v
 GitHub (main) --push--> GitHub Actions
                          |  1. testa (CI)
                          |  2. aprovação manual (Environment "production")
                          |  3. build das imagens e push para o Amazon ECR (tag = SHA do commit)
                          |  4. SSM Run Command na EC2
                          v
 AWS (us-east-2)
 +---------------------------------------------------------+
 | EC2 "decode-app"  (Docker Compose)                      |
 |   Nginx :80 -> frontend :3000 -> auth / bucket / object |
 |                                      |                  |
 |                                      v                  |
 |                          RDS PostgreSQL (privado)       |
 +---------------------------------------------------------+
   ECR (imagens)    S3 (bucket criado, uso planejado)
```

Só a porta 80 do Nginx fica exposta. Os serviços e o banco ficam em rede interna.

## 2. Recursos na AWS

Região: **us-east-2 (Ohio)**.

| Recurso | Configuração | Observações |
|---|---|---|
| EC2 `decode-app` | Ubuntu 24.04, `m7i-flex.large`, Elastic IP | acesso **somente** por SSM Session Manager (sem SSH) |
| RDS `decode-db` | PostgreSQL 16, `db.t4g.micro`, privado, criptografado | proteção contra exclusão ligada; backup automático de 1 dia (limite do plano gratuito) |
| ECR | repositórios `decode/auth-service`, `decode/bucket-service`, `decode/object-service`, `decode/frontend` | imagens identificadas pelo SHA do commit |
| S3 | bucket privado, versionado, criptografia SSE-S3 | criado, mas o `object-service` ainda grava em disco (veja [Limitações](#7-limitações-conhecidas)) |
| IAM | função `decode-ec2-role` (instância) e usuário `decode-ci-deploy` (pipeline) | privilégio mínimo, veja [Segurança](#3-segurança-do-ambiente) |

### Rede

- O security group do banco aceita a porta 5432 **apenas** do security group da EC2.
- O banco não tem IP público.
- A conexão com o banco exige TLS (`sslmode=require`).

## 3. Segurança do ambiente

- **Sem SSH.** O acesso ao servidor é por SSM Session Manager, autenticado pelo IAM e auditável.
- **Função da instância (`decode-ec2-role`):** permite SSM, leitura de imagens no ECR e acesso ao bucket S3. A EC2 se autentica no ECR por essa função, sem chaves gravadas no servidor.
- **Usuário do pipeline (`decode-ci-deploy`):** só pode publicar nos repositórios do ECR e enviar comandos SSM **para a instância `decode-app`** com o documento `AWS-RunShellScript`. Não administra nada além disso.
- **Environment `production` no GitHub:** exige aprovação manual de um revisor e só aceita a branch `main`. As chaves do usuário do pipeline ficam apenas nele, como *secrets*.
- Nunca criar chaves de acesso para o usuário raiz da conta.

## 4. Arquivos no servidor

Tudo fica em `/opt/decode` na EC2:

| Arquivo | Função |
|---|---|
| `docker-compose.yml` | cópia de `infra/docker-compose.prod.yml` (a versão da `main` é a fonte da verdade) |
| `.env` | variáveis do ambiente: registry, endereço do banco, senhas das roles e segredo entre serviços. Só root lê. **Nunca versionado.** |
| `keys/private.pem` | chave privada de assinatura dos JWT. **Nunca versionada.** |
| `.last_tag` | tag (SHA) da última implantação bem-sucedida, usada no rollback |

## 5. CI/CD

### 5.1 Integração contínua (`ci.yml`)

Em todo Pull Request e em todo push na `main`, roda em paralelo: testes dos 3 serviços Java (com Testcontainers), lint, testes e build do frontend, e testes unitários do app Android. A `main` só aceita merge com os 5 checks verdes e 1 aprovação.

### 5.2 Implantação contínua (`deploy.yml`)

Dispara no push na `main` quando mudam `backend/`, `frontend/`, `infra/` ou o próprio workflow (e também sob demanda, por `workflow_dispatch`). Alterações fora dessas pastas (por exemplo `docs/`, `ai-worker/` e o `README.md`) **não** implantam. Atenção: qualquer arquivo dentro de `backend/` ou `frontend/`, mesmo documentação como o `backend/.env.example`, dispara o workflow (que ainda pede aprovação antes de agir).

1. O job **publicar e implantar** pede a **aprovação** do revisor do Environment `production`.
2. Após aprovado, constrói as 4 imagens e publica no ECR com a tag do commit.
3. Envia à EC2, por **SSM Run Command**, o `docker-compose.prod.yml` e o `infra/deploy.sh` (o conteúdo vai no próprio comando, sem depender de acesso ao repositório a partir do servidor).
4. O `deploy.sh`, na EC2:
   1. autentica no ECR com a função da instância;
   2. guarda o compose atual como `docker-compose.prev.yml`;
   3. aplica o novo compose e executa `docker compose pull` e `docker compose up -d --wait` (até 240 s);
   4. testa a resposta HTTP local;
   5. se tudo passar, grava a tag em `.last_tag` e termina com sucesso.
5. O workflow lê o resultado do comando SSM e falha se a implantação falhou.

### 5.3 Rollback

Se qualquer etapa do passo 4 falhar, o `deploy.sh` restaura o compose anterior e sobe de novo a tag registrada em `.last_tag`, e o workflow termina em erro. O ambiente volta ao último estado que funcionava.

Rollback manual para uma versão específica: veja [6.2](#62-versão-específica-ou-rollback-manual).

### 5.4 Variáveis do Environment `production`

| Nome | Tipo | Uso |
|---|---|---|
| `AWS_REGION` | variável | região (us-east-2) |
| `ECR_REGISTRY` | variável | endereço do registry do ECR |
| `EC2_INSTANCE_ID` | variável | instância que recebe o deploy |
| chaves do usuário `decode-ci-deploy` | secrets | autenticação do pipeline na AWS |

## 6. Operação

Para acessar o servidor: console da AWS → **Systems Manager** → **Session Manager** → selecione `decode-app` → **Iniciar sessão**. Depois:

```
sudo -i
cd /opt/decode
export TAG=$(cat .last_tag)
```

### 6.1 Comandos do dia a dia

| Objetivo | Comando |
|---|---|
| Estado dos serviços | `docker compose ps` |
| Logs de um serviço | `docker compose logs --tail 50 auth-service` |
| Reiniciar um serviço | `docker compose restart bucket-service` |
| Versão em execução | `cat .last_tag` |

Todos os serviços devem aparecer como `Up (healthy)`, com a tag do commit nas imagens.

### 6.2 Versão específica (ou rollback manual)

1. `export TAG=<sha-completo-do-commit>`
2. Autenticar no ECR: `aws ecr get-login-password --region us-east-2 | docker login --username AWS --password-stdin <ECR_REGISTRY>`
3. `docker compose pull && docker compose up -d --wait`
4. Se funcionou: `echo $TAG > .last_tag`

(Se `aws` não for encontrado, use o caminho completo `/snap/bin/aws`.)

### 6.3 Ligar e desligar o ambiente

A EC2 gera custo enquanto está ligada. Fora dos períodos de uso, **pare** a instância pelo console (EC2 → Instâncias → Estado da instância → Parar). Os contêineres sobem sozinhos ao ligar (`restart: unless-stopped`) e o Elastic IP é mantido.

Atenção: com a instância parada, o deploy automático falha, porque o SSM não alcança o servidor. Ligue a EC2 antes de aprovar um deploy.

### 6.4 Backup

O RDS mantém backup automático de 1 dia. Antes de marcos de entrega, gere também um snapshot manual do banco (RDS → Snapshots) e, se necessário, um dump lógico (`pg_dump`).

## 7. Limitações conhecidas

| Limitação | Impacto | Plano |
|---|---|---|
| Tráfego em HTTP (sem TLS) | credenciais e tokens trafegam sem criptografia entre cliente e servidor | HTTPS com domínio próprio |
| Arquivos gravados em volume da EC2, não no S3 | perda do disco implica perda dos arquivos; sem escala horizontal | integrar o `object-service` ao S3 |
| Instância única, sem alta disponibilidade | indisponibilidade durante deploy, reinício ou falha | fora do escopo desta versão |
| Backup de 1 dia no RDS | janela curta de recuperação | snapshots manuais antes das entregas |
| Plano gratuito da AWS | limites de serviço e de crédito | acompanhar o consumo |

## 8. Segredos

- Senhas, chaves e o arquivo `.env` **nunca** entram no repositório (que é público).
- Os valores de produção ficam no `.env` da EC2 e em um gerenciador de senhas da equipe.
- Se um segredo vazar: revogar ou rotacionar na hora. Apagar o commit não basta.
- Ao fim do projeto, **excluir as chaves de acesso do usuário `decode-ci-deploy`** e remover os recursos que não forem mais necessários.

## 9. Chatbot do Telegram (`ai-worker/chatbot`)

Assistente do projeto no Telegram: um bot (`bot.py`) que chama uma API FastAPI (`app.py`) com RAG sobre a base de conhecimento do DECODE e um modelo da Groq. Roda na mesma EC2 do site, **fora do Docker**, como dois serviços do systemd. O deploy automático (seção 5) **não** cobre esta parte: a instalação e as atualizações são manuais.

### 9.1 Como está instalado

| Item | Valor |
|---|---|
| Serviços | `decode-api` (uvicorn) e `decode-bot` |
| Usuário | `decode-chatbot`, de sistema, sem login e sem senha |
| Pasta | `/opt/decode-chatbot` (código em `repo/`, ambiente Python em `.venv/`) |
| Rede | a API escuta só em `127.0.0.1:8000`; **nenhuma porta nova** foi aberta no Security Group |
| Memória | `decode-api` limitada a 2,5 GB e `decode-bot` a 400 MB, para o chatbot nunca competir com o site |
| Prioridade | CPU com prioridade menor que a do site |
| Segredos | `repo/ai-worker/chatbot/.env` (permissão 600): `GROQ_API_KEY`, `TELEGRAM_TOKEN` e `API_URL`. **Nunca versionado** |
| Base vetorial | pasta `banco_chroma/`, gerada a partir de `DECODE_Base_Conhecimento.pdf` (não versionada) |

Um token do Telegram só pode ter **uma** instância ligada. Se o mesmo token rodar em outro lugar, o Telegram devolve erro 409 e as duas caem.

### 9.2 Operação

| Objetivo | Comando |
|---|---|
| Estado dos serviços | `sudo systemctl status decode-api decode-bot --no-pager` |
| Log do bot | `sudo journalctl -u decode-bot -f` |
| Log da API | `sudo journalctl -u decode-api -f` |
| Reiniciar | `sudo systemctl restart decode-api decode-bot` |
| Parar e desligar de vez | `sudo systemctl disable --now decode-bot decode-api` |

Parar o chatbot não afeta o site.

### 9.3 Atualizar o código

```
sudo -H -u decode-chatbot git -C /opt/decode-chatbot/repo pull
sudo systemctl restart decode-api decode-bot
```

Se mudaram as dependências, instale de novo com o `pip` do `.venv`.

### 9.4 Atualizar a base de conhecimento

Quando o PDF da base mudar, o banco vetorial precisa ser refeito (sem apagar antes, os trechos ficam duplicados):

```
sudo systemctl stop decode-api
sudo rm -rf /opt/decode-chatbot/repo/ai-worker/chatbot/banco_chroma
sudo systemd-run --wait --pipe --collect -p MemoryMax=2500M -p User=decode-chatbot -p Nice=19 -p WorkingDirectory=/opt/decode-chatbot/repo/ai-worker/chatbot -p Environment=HOME=/opt/decode-chatbot /opt/decode-chatbot/.venv/bin/python banco_vetorial.py
sudo systemctl start decode-api
```

### 9.5 Trocar uma chave ou o token

Se uma chave vazar: revogue na hora (console da Groq; `/revoke` no BotFather), crie outra, edite o `.env` e reinicie os dois serviços. Apagar a mensagem onde ela apareceu não basta.

### 9.6 Limitações conhecidas

| Limitação | Impacto |
|---|---|
| Sem job de Python no CI e sem deploy automático | erros só aparecem em produção; atualização manual |
| API sem autenticação | protegida apenas por escutar em `127.0.0.1` |
| Histórico das conversas só na memória do bot | reiniciar o bot zera as conversas |
| Modelo externo (Groq) com cota gratuita | o bot para de responder se a cota acabar |
| O modelo pode afirmar o que o DECODE não faz | a base e o prompt precisam deixar claro o que não existe na versão atual |
