# 0001 — Versionamento e proteção da `main`

- **Data:** 11/11/2026
- **Status:** em vigor
- **Responsável:** Eduardo Mosti (infraestrutura e versionamento)

## Contexto

O Termo de Abertura exige controle de versão em Git/GitHub com revisão de código entre os integrantes antes da integração. O Projeto Integrador IV exige repositório GitHub com histórico de desenvolvimento e pipeline de deploy. A equipe tem 4 integrantes, com cerca de 10 horas semanais cada, e o prazo fixo termina em 05/12/2026. A organização usa o plano gratuito do GitHub.

## Decisões

1. **Monorepo** (`DecodeRepository`), com as pastas `backend/`, `frontend/`, `ai-worker/`, `infra/` e `docs/`.
2. **GitHub Flow**: `main` protegida e sempre implantável, branches curtas e Pull Requests. Releases por tag.
3. **Proteção da `main`** por ruleset: PR obrigatório com 1 aprovação, aprovações descartadas quando há novos commits, sem force push, sem deleção e sem lista de bypass.
4. **Merge por squash como padrão**, com exclusão automática das branches após o merge. O merge por rebase permanece habilitado para importar blocos que já tenham histórico local sem perder os commits originais. Merge commits estão desabilitados.
5. **Repositório público até a apresentação final (05/12/2026).** O plano gratuito não aplica rulesets em repositório privado. A visibilidade será reavaliada após a entrega.
6. **Segurança do repositório:** secret scanning, push protection e Dependabot alerts ativos.
7. **Checagens de CI obrigatórias** serão adicionadas ao ruleset quando os primeiros workflows existirem. A meta de cobertura de 80% entra como aviso até medirmos a cobertura real e passa a bloqueante depois.

## Alternativas consideradas

| Alternativa | Motivo da rejeição |
|---|---|
| Repositório privado com fork e PR | Mais atrito para um time pequeno e prazo curto, e não impõe segundo revisor. |
| Plano GitHub Team | Custo fora do orçamento previsto no Termo. |
| Privado, apenas por convenção | Sem enforcement, enfraquece a evidência de revisão exigida pelo Termo. |
| GitFlow com `develop` | Mais cerimônia, e só se paga com ambiente de homologação separado, que o orçamento não prevê. |

## Consequências

- O código fica visível publicamente. Segredos e dados pessoais reais jamais podem entrar no repositório (ver `CONTRIBUTING.md`).
- A exigência de 1 aprovação cria dependência da disponibilidade dos revisores. Mitigações: `CODEOWNERS` solicita revisão automaticamente e o prazo de resposta combinado é de 24 horas.
- Os PRs e as revisões ficam como evidência do processo de desenvolvimento.
