#!/bin/bash
# Executado pelo entrypoint do PostgreSQL apenas na PRIMEIRA inicialização do volume (pgdata vazio).
# Cria um schema e uma role por serviço. Cada role só enxerga e altera o próprio schema.
# As senhas vêm de variáveis de ambiente (arquivo .env), nunca do repositório.
set -euo pipefail

: "${AUTH_DB_PASSWORD:?AUTH_DB_PASSWORD não definida}"
: "${BUCKET_DB_PASSWORD:?BUCKET_DB_PASSWORD não definida}"
: "${OBJECT_DB_PASSWORD:?OBJECT_DB_PASSWORD não definida}"

psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" \
  -v auth_pw="$AUTH_DB_PASSWORD" \
  -v bucket_pw="$BUCKET_DB_PASSWORD" \
  -v object_pw="$OBJECT_DB_PASSWORD" <<'EOSQL'
-- Ninguém além dos donos usa o schema public nem se conecta sem permissão explícita.
REVOKE ALL ON SCHEMA public FROM PUBLIC;
REVOKE ALL ON DATABASE sos FROM PUBLIC;

CREATE ROLE auth_svc   LOGIN PASSWORD :'auth_pw';
CREATE ROLE bucket_svc LOGIN PASSWORD :'bucket_pw';
CREATE ROLE object_svc LOGIN PASSWORD :'object_pw';

GRANT CONNECT ON DATABASE sos TO auth_svc, bucket_svc, object_svc;

-- Cada schema pertence à role do serviço (o Flyway roda com essa role e cria as tabelas nele).
CREATE SCHEMA auth    AUTHORIZATION auth_svc;
CREATE SCHEMA bucket  AUTHORIZATION bucket_svc;
CREATE SCHEMA objects AUTHORIZATION object_svc;

ALTER ROLE auth_svc   SET search_path = auth;
ALTER ROLE bucket_svc SET search_path = bucket;
ALTER ROLE object_svc SET search_path = objects;
EOSQL
