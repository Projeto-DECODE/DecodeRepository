# frontend (site do DECODE)

Site do DECODE em **Next.js 16.3.8 + React + TypeScript + Tailwind CSS**, no mesmo padrão das aulas de Aplicações Web.
Ligado ao back-end `simple-object-storage` (auth 8081, buckets 8082, arquivos 8083).

## Como rodar

```
npm install
npm run dev
```

Abra http://localhost:3000.

O site precisa de um back-end ligado. Escolha **um**:

| Opção | Comando | Quando usar |
|---|---|---|
| Back-end real | `docker compose up -d` na pasta `backend/` | Teste de verdade |
| Back-end de teste (mock) | `npm run mock` em outro terminal | Sem Docker. Conta: `teste@teste.com` / `12345678` |

> Os dois usam as mesmas portas (8081, 8082 e 8083). Não ligue os dois juntos.

## Testes automáticos

```
npm test                  # roda todos os testes
npm run test:cobertura    # mostra quanto do código os testes cobrem (meta: acima de 80%)
```

O relatório completo fica em `coverage/index.html` (não vai para o GitHub).

## Docker

```
docker build -t decode-front .
docker run -p 3000:3000 decode-front
```

Os endereços do back-end entram na hora do `docker build`, com `--build-arg`
(veja os exemplos no começo do `Dockerfile`). Sem eles, o site procura o
back-end nas portas 8081, 8082 e 8083 do próprio computador.

## Telas

| Rota | Tela |
|---|---|
| `/` | Início, com os recursos |
| `/login` | Entrar |
| `/cadastro` | Criar conta (entra sozinho depois) |
| `/buckets` | Meus buckets: lista, criar, excluir com "Desfazer", perfil e sair |
| `/buckets/[bucketId]` | Arquivos: envio por arrastar e soltar, pesquisa, "Carregar mais", detalhe com prévia, baixar e excluir |

## Organização

```
src/
├── app/                  páginas (cada pasta com page.tsx é uma rota)
├── components/           componentes reutilizáveis + index.ts (barrel)
├── resources/            comunicação com a API (um .resource.ts e um .service.ts por API)
│   ├── auth/  bucket/  arquivo/
│   ├── api.ts            fetch com o token e tratamento de erros
│   ├── sessao.ts         login guardado no navegador (localStorage)
│   └── erro.ts           código do back-end -> mensagem em português
└── utils/                formatação (tamanho, data) e hooks
scripts/mock-backend.mjs  back-end de teste
```

## Sem erro de CORS

O navegador chama sempre `localhost:3000/api/...` e o Next.js repassa para o serviço certo (veja `rewrites` no `next.config.ts`).
Por isso o back-end não precisa liberar CORS. Para apontar para outro endereço, copie `.env.example` para `.env.local`.

## Visual

Cores definidas uma vez em `src/app/globals.css` (`@theme`):

| Uso | Cor |
|---|---|
| Fundo | `#1A1A1E` |
| Cartões | `#212328` |
| Destaque (botões, foco, item ativo) | verde neon `#39FF14`, apoio `#76FF03` |
| Erro | `#EF476A` |
| Ícones por tipo | imagem `#4285F4`, PDF `#EA4335`, planilha `#76FF03`, outros `#FF6F00` |

Fonte Inter, cantos levemente arredondados (6 a 12 px), brilho verde no cartão em foco e no último bucket aberto, transições de 0,3 s.
