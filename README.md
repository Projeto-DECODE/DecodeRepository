# DECODE

Plataforma SaaS de Gestão Inteligente de Documentos Não Estruturados: recebe PDFs, imagens e textos, extrai o conteúdo com OCR, indexa e oferece busca avançada e consulta conversacional com IA.

Projeto Integrador IV — SENAI-SP, 4º semestre de Análise e Desenvolvimento de Sistemas (2026).

## Estrutura do repositório

| Pasta | Conteúdo |
|---|---|
| `backend/` | API REST, autenticação JWT, filas (Redis), indexação (Elasticsearch), logs e auditoria |
| `frontend/` | Aplicação web responsiva (upload, busca, visualizador, dashboard) |
| `ai-worker/` | OCR (Tesseract), NLP, embeddings, busca vetorial e chat com LLM |
| `infra/` | Docker/Compose, Nginx, scripts de deploy e infraestrutura na AWS |
| `docs/` | Arquitetura, referência da API, guia de implantação e registro de decisões |
| `.github/` | Template de PR, CODEOWNERS e workflows de CI/CD |

## Como contribuir

Leia o [CONTRIBUTING.md](CONTRIBUTING.md). Resumo: nada vai direto na `main`; toda mudança entra por Pull Request com 1 aprovação.

## Estado

Estrutura em construção. As instruções de execução local e de implantação serão adicionadas em `docs/` e neste README conforme os componentes forem integrados.

## Equipe

- Robson Schram (gestor)
- Helio do Nascimento Filho
- Gabriel Henrique Hausmann
- Eduardo Mosti
