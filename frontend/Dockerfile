# Site DECODE (Next.js) em container
#
# Montar:  docker build -t decode-front .
# Rodar:   docker run -p 3000:3000 decode-front
#
# Os endereços do back-end entram na hora de MONTAR a imagem (--build-arg),
# porque o Next.js grava os redirecionamentos /api/... durante o build. Ex.:
#   docker build -t decode-front \
#     --build-arg BACKEND_AUTH_URL=http://auth-service:8081 \
#     --build-arg BACKEND_BUCKET_URL=http://bucket-service:8082 \
#     --build-arg BACKEND_OBJECT_URL=http://object-service:8083 .

# ---------- 1) Instala as dependências ----------
FROM node:22-alpine AS dependencias
WORKDIR /app
COPY package.json package-lock.json ./
RUN npm ci

# ---------- 2) Monta o site ----------
FROM node:22-alpine AS build
WORKDIR /app
COPY --from=dependencias /app/node_modules ./node_modules
COPY . .

ARG BACKEND_AUTH_URL=http://host.docker.internal:8081
ARG BACKEND_BUCKET_URL=http://host.docker.internal:8082
ARG BACKEND_OBJECT_URL=http://host.docker.internal:8083
ENV BACKEND_AUTH_URL=$BACKEND_AUTH_URL \
    BACKEND_BUCKET_URL=$BACKEND_BUCKET_URL \
    BACKEND_OBJECT_URL=$BACKEND_OBJECT_URL \
    NEXT_TELEMETRY_DISABLED=1

RUN npm run build

# ---------- 3) Imagem final, só com o necessário ----------
FROM node:22-alpine AS final
WORKDIR /app
ENV NODE_ENV=production \
    NEXT_TELEMETRY_DISABLED=1 \
    PORT=3000 \
    HOSTNAME=0.0.0.0

# Roda sem ser administrador (mais seguro)
RUN addgroup -S decode && adduser -S decode -G decode

COPY --from=build --chown=decode:decode /app/public ./public
COPY --from=build --chown=decode:decode /app/.next/standalone ./
COPY --from=build --chown=decode:decode /app/.next/static ./.next/static

USER decode
EXPOSE 3000
CMD ["node", "server.js"]
