#!/usr/bin/env bash
# Implanta o DECODE na EC2.  Uso: deploy.sh <TAG> <ECR_REGISTRY>
# Executado como root pelo SSM (workflow Deploy), dentro de /opt/decode.
# Espera o novo compose em docker-compose.new.yml. Se falhar, volta à versão anterior.
set -euo pipefail
export HOME=/root
export PATH="$PATH:/snap/bin"

TAG="${1:?informe a tag}"
REG="${2:?informe o registry}"
cd /opt/decode

echo "== login no ECR"
aws ecr get-login-password --region us-east-2 \
  | docker login --username AWS --password-stdin "$REG"

PREV=""
if [ -f .last_tag ]; then PREV="$(cat .last_tag)"; fi
cp -f docker-compose.yml docker-compose.prev.yml
cp -f docker-compose.new.yml docker-compose.yml
export TAG ECR_REGISTRY="$REG"

# O Nginx guarda o IP do frontend quando sobe. Quando o frontend é recriado
# com outro IP e o Nginx continua rodando, ele devolve 502. Por isso o Nginx é
# reiniciado depois de cada "up" e só então o site é testado (com tentativas).
verificar_site() {
  docker compose restart nginx
  local i
  for i in $(seq 1 15); do
    if curl -fsS -o /dev/null http://127.0.0.1/; then
      return 0
    fi
    sleep 2
  done
  return 1
}

echo "== implantando $TAG (anterior: ${PREV:-nenhuma})"
if docker compose pull \
  && docker compose up -d --wait --wait-timeout 240 \
  && verificar_site; then
  echo "$TAG" > .last_tag
  echo "== DEPLOY OK: $TAG"
  exit 0
fi

echo "== FALHA no deploy de $TAG"
if [ -n "$PREV" ]; then
  echo "== revertendo para $PREV"
  cp -f docker-compose.prev.yml docker-compose.yml
  export TAG="$PREV"
  docker compose up -d --wait --wait-timeout 240
  if verificar_site; then
    echo "== ROLLBACK OK: site respondendo na versão $PREV"
  else
    echo "== ATENÇÃO: rollback concluído, mas o site NÃO responde. Verifique a EC2."
  fi
fi
exit 1
