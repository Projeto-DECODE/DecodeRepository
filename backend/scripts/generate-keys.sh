#!/usr/bin/env bash
# Gera o par RSA usado para assinar (auth-service) e validar (demais serviços) os JWTs.
# Uso: ./scripts/generate-keys.sh [--force]
#
# Saída em ./keys/ (ignorada pelo Git):
#   private.pem  chave privada PKCS#8, montada SOMENTE no auth-service
#   public.pem   chave pública (informativa; os serviços a obtêm via JWKS)
#
# Somente para desenvolvimento local. Em produção, use um cofre de segredos (KMS/Vault).
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
KEYS_DIR="$ROOT_DIR/keys"

command -v openssl >/dev/null 2>&1 || { echo "Erro: openssl não encontrado." >&2; exit 1; }

if [[ -e "$KEYS_DIR/private.pem" && "${1:-}" != "--force" ]]; then
  echo "Já existe $KEYS_DIR/private.pem. Use --force para substituir." >&2
  echo "Atenção: trocar a chave invalida todos os tokens já emitidos." >&2
  exit 1
fi

umask 077
mkdir -p "$KEYS_DIR"
chmod 700 "$KEYS_DIR"

# -traditional não é usado: queremos PKCS#8 ("BEGIN PRIVATE KEY"), formato lido pelo auth-service.
openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:2048 -out "$KEYS_DIR/private.pem"
openssl pkey -in "$KEYS_DIR/private.pem" -pubout -out "$KEYS_DIR/public.pem"

# O container roda como usuário não-root e precisa ler o arquivo montado.
# O diretório keys/ (700) continua impedindo o acesso de outros usuários do host.
chmod 644 "$KEYS_DIR/private.pem" "$KEYS_DIR/public.pem"

echo "Chaves geradas em $KEYS_DIR"
echo "  private.pem -> somente auth-service (nunca versionar, nunca copiar para a imagem)"
echo "  public.pem  -> informativa"
