# Segurança

## O que está implementado

| Área | Medida |
|---|---|
| Senhas | Argon2id (`Argon2PasswordEncoder`, BouncyCastle). Nunca em texto puro, nunca nas respostas. |
| Login | Resposta única `401 INVALID_CREDENTIALS` para e-mail inexistente e senha errada. Para e-mail inexistente, uma verificação contra um hash fictício é executada, para não vazar a existência da conta por tempo de resposta. |
| JWT | RS256, `kid`, claims mínimas (`sub`, `iat`, `exp`, `iss`, `aud`, `scope`, `jti`). Sem dados pessoais. |
| Expiração | Padrão 15 min. Acima de 30 min o auth-service não inicia, e a emissão também limita a 30 min. Os serviços validam `exp` sem tolerância de relógio. |
| Validação | Assinatura, `exp`, `iss`, `aud`, presença de `sub` e de `exp`. Algoritmo fixado em RS256. |
| Chaves | Privada só no auth-service, como arquivo montado, fora da imagem e do Git. JWKS expõe só a parte pública. |
| Escopos | `api` (usuário) e `internal` (serviço), com audiências distintas. |
| Autorização | `ownerUserId` sempre vem de `jwt.sub`. Busca por par (recurso, dono/bucket). `404` para recurso alheio. |
| Entrada | Bean Validation em todos os DTOs. Nome de bucket: 3 a 63 caracteres `[a-z0-9-]`. |
| Arquivos | Caminho físico só com UUIDs do servidor; `normalize()` e `startsWith(raiz)`; temporário + `ATOMIC_MOVE`; sem sobrescrita; limite de tamanho; arquivo vazio rejeitado. |
| Download | `Content-Type: application/octet-stream` fixo, `Content-Disposition: attachment` com nome sanitizado, `X-Content-Type-Options: nosniff`, `Cache-Control: no-store`. O `Content-Type` do cliente é tratado como não confiável e nunca é reenviado. |
| Erros | Formato único, sem stack trace, caminho, token ou valor rejeitado. |
| Banco | Roles com privilégio mínimo, uma por schema. PostgreSQL sem porta publicada. |
| Rede | Portas publicadas só em `127.0.0.1`. |
| Container | Usuário não-root. |
| Logs | Sem senhas, hashes, tokens ou conteúdo de arquivos. `traceId` por requisição. |
| Rotas | `anyRequest().denyAll()`: só o que está listado é acessível. Públicos: login, cadastro, JWKS, health e Swagger. |

## Modelo de ameaça resumido

- **Usuário A acessa recurso de B:** bloqueado por busca por par (recurso, dono) e pelo `404` uniforme.
- **Token forjado ou de outra chave:** rejeitado pela assinatura.
- **Token de usuário em rota interna (ou o inverso):** `403`/`401` por escopo e audiência.
- **Path traversal via nome de arquivo:** o nome nunca entra no caminho físico.
- **Upload gigante:** limite em streaming e limite do multipart, alinhados.
- **Bucket-service fora do ar:** `503`, sem liberar acesso.

## Confused deputy e o `aud` compartilhado

O token do usuário é encaminhado do object-service ao bucket-service. É seguro no contexto: ele só vale para o próprio usuário e expira em minutos. O custo é que `aud=sos-api` é o mesmo nos dois serviços. Um serviço comprometido poderia reutilizar o token recebido contra o outro, dentro da janela de validade. Aceitável em contexto educacional. Em produção, use audiências por serviço ou troca de token (token exchange).

## Adequado ao ambiente educacional vs. necessário em produção

| Tema | Aqui | Em produção |
|---|---|---|
| Transporte | HTTP local | TLS em todas as conexões (inclusive entre serviços) |
| Segredos | `.env` e arquivo de chave | Cofre (Vault, KMS, secrets do orquestrador) e rotação |
| Chaves JWT | Uma chave fixa | Rotação por `kid`, JWKS com mais de uma chave |
| Revogação | Não existe | Lista de revogação por `jti`, refresh token com rotação |
| Brute force | Não existe | Rate limit por IP e por conta, bloqueio progressivo |
| Rede | Portas em `127.0.0.1`, `/internal` alcançável | Gateway, rede privada, `/internal` fora do alcance externo |
| Arquivos | Sem varredura | Antivírus, cota por usuário, criptografia em repouso |
| Observabilidade | Logs com `traceId` | Tracing distribuído, alertas, auditoria |
| CSRF | Desabilitado (API stateless sem cookies) | Manter assim, ou reavaliar se houver cookies |
| CORS | Não configurado (mesma origem do Swagger) | Política explícita por origem |

## Comportamento da sessão expirada

O access token expira em até 30 minutos. Depois disso, qualquer chamada protegida retorna `401` com `{"code":"UNAUTHORIZED"}` e o cliente deve fazer login novamente. Não há refresh token nesta versão.
