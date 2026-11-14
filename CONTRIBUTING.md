# Guia de contribuição

## Fluxo (GitHub Flow)

1. Atualize a `main`: `git switch main && git pull`.
2. Crie uma branch curta a partir dela: `git switch -c feat/nome-curto`.
3. Faça commits pequenos e abra um Pull Request para a `main`.
4. Aguarde 1 aprovação de outro integrante e o CI verde (quando existir).
5. Faça **Squash and merge**. A branch é apagada automaticamente.

A `main` é protegida: não aceita push direto, force push nem deleção. Ela deve estar sempre em estado implantável.

## Nomes de branch

`feat/…`, `fix/…`, `chore/…`, `docs/…`, `test/…`, `hotfix/…`, em minúsculas e com hífens (ex.: `feat/upload-drag-drop`).

## Commits (Conventional Commits, em português)

Formato: `tipo: descrição curta no imperativo`.
Tipos: `feat`, `fix`, `chore`, `docs`, `test`, `refactor`, `ci`.
Exemplo: `feat: adiciona upload de documentos com drag and drop`.

## Pull Requests

- Pequenos e focados: uma mudança lógica por PR.
- Preencha o template. Quem abre o PR não o aprova.
- Revise com foco em correção, segurança (OWASP Top 10), testes e legibilidade.
- Responda a pedidos de revisão em até 24 horas.

## Regras sem exceção

- **Nunca commitar segredos**: chaves de API, senhas, `.env`, certificados, credenciais da AWS. O repositório é público. Use `.env.example` com valores fictícios.
- **Nunca commitar dados pessoais reais** (LGPD). Use amostras públicas, sintéticas ou anonimizadas.
- **Se um segredo vazar**, avise o grupo na hora e **revogue ou rotacione a credencial**. Apagar o commit não basta.

## Definição de pronto

Código revisado e aprovado, testes passando, cobertura sem regressão (meta do projeto: acima de 80%) e documentação atualizada quando houver mudança de comportamento ou de API.

## Versões

As entregas dos checkpoints são marcadas com tag na `main` (ex.: `v0.9-cp4`, `v1.0-cp5`).
