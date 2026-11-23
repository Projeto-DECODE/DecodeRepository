# DECODE

[![CI](https://github.com/Projeto-DECODE/DecodeRepository/actions/workflows/ci.yml/badge.svg)](https://github.com/Projeto-DECODE/DecodeRepository/actions/workflows/ci.yml)

Plataforma SaaS de **Gestão Inteligente de Documentos Não Estruturados**: o usuário cria sua conta, organiza o acervo em *buckets* e envia, lista, baixa e exclui arquivos, com integridade verificada por SHA-256. A camada de OCR e IA (busca avançada e consulta conversacional) está prevista para a próxima versão.

Projeto Integrador IV, SENAI-SP, 4º semestre de Análise e Desenvolvimento de Sistemas (2026).

## O que já funciona (v1)

- Cadastro e login com JWT (RS256) e senhas com Argon2id.
- Buckets por usuário, com isolamento de dados entre contas.
- Upload, listagem, download e exclusão de arquivos (até 10 MB pela interface), com SHA-256 calculado no envio.
- Site web (Next.js) e aplicativo Android.
- Implantação na AWS (EC2, RDS PostgreSQL e ECR) a partir de imagens Docker publicadas pelo CI.

## Arquitetura

```
 Navegador / App Android
          |
        Nginx (:80)
          |
   Frontend Next.js (:3000)  -- /api/* -->  auth-service   (:8081)
                                            bucket-service (:8082)
                                            object-service (:8083)
                                                   |
                                      PostgreSQL (1 schema por serviço)
```

| Componente | Tecnologia | Responsabilidade |
|---|---|---|
| `auth-service` | Java 21, Spring Boot 3.5 | usuários, emissão de JWT, JWKS |
| `bucket-service` | Java 21, Spring Boot 3.5 | buckets e propriedade |
| `object-service` | Java 21, Spring Boot 3.5 | metadados e bytes dos arquivos |
| `frontend` | Next.js 16, TypeScript, Tailwind | interface web |
| `mobile` | Android (Java) | aplicativo "MeuStorage" |
| Banco | PostgreSQL 16 (RDS) | persistência, migrações com Flyway |
| Infra | Docker, GitHub Actions, AWS | empacotamento, CI/CD e hospedagem |

Detalhes de arquitetura e segurança: [`backend/docs/`](backend/docs/).

## Estrutura do repositório

| Pasta | Conteúdo |
|---|---|
| `backend/` | os 3 microsserviços, scripts de chaves e Compose local |
| `frontend/` | aplicação web |
| `mobile/` | aplicativo Android |
| `infra/` | Compose de produção e script de implantação |
| `ai-worker/` | chatbot do Telegram (RAG com Groq); OCR e embeddings reservados para a v2.0 |
| `docs/` | registro de decisões e documentação técnica |
| `.github/` | CI/CD, CODEOWNERS e template de PR |

## Como rodar localmente

Pré-requisitos: Docker com Compose v2, `openssl` e Node.js 22.

1. **Back-end:** siga a seção 6 de [`backend/README.md`](backend/README.md) (criar o `.env`, gerar as chaves e rodar `docker compose up -d --build` dentro de `backend/`).
2. **Front-end:** em `frontend/`, rode `npm install` e `npm run dev`. Abra http://localhost:3000.
3. **Mobile:** abra a pasta `mobile/` no Android Studio (veja [`mobile/LEIA-ME.md`](mobile/LEIA-ME.md)).

## Testes

```
cd backend/auth-service && mvn verify      # idem bucket-service e object-service (exige Docker)
cd frontend && npm test
cd mobile && ./gradlew testDebugUnitTest
```

## CI/CD

- **CI** (`.github/workflows/ci.yml`): em todo Pull Request e na `main`, compila e testa backend, frontend e mobile.
- **Deploy** (`.github/workflows/deploy.yml`): após merge na `main`, publica as imagens no Amazon ECR com a tag do commit e atualiza a EC2.

A `main` é protegida: PR com 1 aprovação e os checks do CI verdes. Veja [`CONTRIBUTING.md`](CONTRIBUTING.md) e a [decisão 0001](docs/decisoes/0001-versionamento-e-protecao-da-main.md).

## Estado e próximos passos

| Item | Situação |
|---|---|
| Autenticação, buckets e arquivos (API, web) | pronto |
| Implantação na AWS com CI/CD | pronto |
| Integração do app Android com a API real | em andamento |
| Armazenamento de arquivos no Amazon S3 | planejado (hoje em volume do servidor) |
| HTTPS e domínio próprio | planejado |
| OCR, busca avançada e chat com IA | v2.0 |

## Equipe

| Integrante | Atuação |
|---|---|
| Robson Schram | gestão do projeto |
| Eduardo Mosti | infraestrutura e versionamento |
| Gabriel Henrique Hausmann | back-end |
| Helio do Nascimento Filho | aplicativo móvel |
