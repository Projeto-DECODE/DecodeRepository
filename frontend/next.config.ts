import type { NextConfig } from "next";

// Endereços dos 3 serviços do back-end (simple-object-storage).
// Em produção, troque pelas variáveis de ambiente (veja .env.example).
const urlAuth = process.env.BACKEND_AUTH_URL ?? "http://localhost:8081";
const urlBuckets = process.env.BACKEND_BUCKET_URL ?? "http://localhost:8082";
const urlArquivos = process.env.BACKEND_OBJECT_URL ?? "http://localhost:8083";

const nextConfig: NextConfig = {
  // Gera uma versão enxuta do site (.next/standalone) para rodar no Docker
  output: "standalone",

  // O navegador chama sempre o próprio site (localhost:3000/api/...)
  // e o Next.js repassa para o serviço certo. Assim não há erro de CORS
  // e não é preciso alterar o back-end.
  async rewrites() {
    return [
      { source: "/api/auth/:caminho*", destination: `${urlAuth}/api/auth/:caminho*` },
      // Arquivos vêm antes de buckets, porque a rota é mais específica
      {
        source: "/api/buckets/:bucketId/objects",
        destination: `${urlArquivos}/api/buckets/:bucketId/objects`,
      },
      {
        source: "/api/buckets/:bucketId/objects/:caminho*",
        destination: `${urlArquivos}/api/buckets/:bucketId/objects/:caminho*`,
      },
      { source: "/api/buckets", destination: `${urlBuckets}/api/buckets` },
      { source: "/api/buckets/:bucketId", destination: `${urlBuckets}/api/buckets/:bucketId` },
    ];
  },
};

export default nextConfig;
