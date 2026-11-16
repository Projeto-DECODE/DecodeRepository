# Exemplos de chamadas

Todos os valores são fictícios. Requer `curl` e `jq`. Suba o ambiente antes (`docker compose up -d --build`).

## Roteiro completo (também serve como teste end-to-end manual)

```bash
# 1. Cadastro
curl -s -X POST localhost:8081/api/auth/register -H 'Content-Type: application/json' \
  -d '{"name":"Maria Silva","email":"maria@example.com","password":"uma-senha-forte-123"}'
# 201 {"id":"...","name":"Maria Silva","email":"maria@example.com","createdAt":"..."}

# 2. Login e captura do token
TOKEN=$(curl -s -X POST localhost:8081/api/auth/login -H 'Content-Type: application/json' \
  -d '{"email":"maria@example.com","password":"uma-senha-forte-123"}' | jq -r .accessToken)
# resposta: {"accessToken":"...","tokenType":"Bearer","expiresIn":900}

# 3. Criar bucket
BUCKET=$(curl -s -X POST localhost:8082/api/buckets -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' -d '{"name":"documentos"}' | jq -r .id)

# 4. Listar buckets
curl -s localhost:8082/api/buckets -H "Authorization: Bearer $TOKEN"

# 5. Upload
echo "conteudo de teste" > /tmp/exemplo.txt
OBJ=$(curl -s -X POST localhost:8083/api/buckets/$BUCKET/objects \
  -H "Authorization: Bearer $TOKEN" -F "file=@/tmp/exemplo.txt" | jq -r .id)

# 6. Metadados
curl -s localhost:8083/api/buckets/$BUCKET/objects/$OBJ -H "Authorization: Bearer $TOKEN"

# 7. Listagem paginada
curl -s "localhost:8083/api/buckets/$BUCKET/objects?page=0&size=20" -H "Authorization: Bearer $TOKEN"

# 8. Download (compare o resultado com o arquivo original)
curl -s -o /tmp/baixado.txt localhost:8083/api/buckets/$BUCKET/objects/$OBJ/download \
  -H "Authorization: Bearer $TOKEN" && diff /tmp/exemplo.txt /tmp/baixado.txt && echo "idêntico"

# 9. Tentar excluir o bucket com objeto dentro: 409 BUCKET_NOT_EMPTY
curl -s -i -X DELETE localhost:8082/api/buckets/$BUCKET -H "Authorization: Bearer $TOKEN"

# 10. Excluir o objeto e depois o bucket (204)
curl -s -i -X DELETE localhost:8083/api/buckets/$BUCKET/objects/$OBJ -H "Authorization: Bearer $TOKEN"
curl -s -i -X DELETE localhost:8082/api/buckets/$BUCKET -H "Authorization: Bearer $TOKEN"
```

## Verificações de segurança (devem falhar como indicado)

```bash
# Sem token: 401
curl -s -i localhost:8082/api/buckets

# Segundo usuário não enxerga o bucket do primeiro: 404
curl -s -X POST localhost:8081/api/auth/register -H 'Content-Type: application/json' \
  -d '{"name":"Bruno","email":"bruno@example.com","password":"outra-senha-forte-1"}'
TOKEN_B=$(curl -s -X POST localhost:8081/api/auth/login -H 'Content-Type: application/json' \
  -d '{"email":"bruno@example.com","password":"outra-senha-forte-1"}' | jq -r .accessToken)
curl -s -i localhost:8082/api/buckets/$BUCKET -H "Authorization: Bearer $TOKEN_B"            # 404
curl -s -i localhost:8083/api/buckets/$BUCKET/objects -H "Authorization: Bearer $TOKEN_B"    # 404

# Token de usuário em rota interna: 403
curl -s -i localhost:8083/internal/buckets/$BUCKET/objects/count -H "Authorization: Bearer $TOKEN"

# Campo de dono no corpo é ignorado (o dono vem do token)
curl -s -X POST localhost:8082/api/buckets -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' \
  -d '{"name":"tentativa","ownerUserId":"00000000-0000-0000-0000-000000000000"}'

# Nome de arquivo malicioso: só vira metadado sanitizado ("passwd")
curl -s -X POST localhost:8083/api/buckets/$BUCKET/objects -H "Authorization: Bearer $TOKEN" \
  -F 'file=@/tmp/exemplo.txt;filename=../../etc/passwd'

# Arquivo vazio: 400 EMPTY_FILE
: > /tmp/vazio.txt
curl -s -i -X POST localhost:8083/api/buckets/$BUCKET/objects -H "Authorization: Bearer $TOKEN" -F "file=@/tmp/vazio.txt"
```

## Expiração do token

O token expira em até 30 minutos (padrão 15). Para observar a expiração rapidamente, suba com `JWT_USER_TTL=1m` no `.env`, faça login, espere mais de 1 minuto e repita uma chamada: o resultado é `401 {"code":"UNAUTHORIZED"}`. Não há tolerância de relógio.

## Bucket-service indisponível (falha fechada)

```bash
docker compose stop bucket-service
curl -s -i localhost:8083/api/buckets/$BUCKET/objects -H "Authorization: Bearer $TOKEN"   # 503
docker compose start bucket-service
```

## Persistência após reinício

```bash
docker compose restart
# os dados e arquivos continuam; faça novo login (tokens antigos ainda valem até expirar)
curl -s localhost:8083/api/buckets/$BUCKET/objects/$OBJ/download -H "Authorization: Bearer $TOKEN" | head -c 100
```

## Formato de erro

```json
{
  "code": "BUCKET_NOT_FOUND",
  "message": "Bucket não encontrado",
  "status": 404,
  "timestamp": "2026-10-18T12:00:00Z",
  "traceId": "0b1c0c42-..."
}
```

Erros de validação incluem `details` com `field` e `message` (nunca o valor enviado).
