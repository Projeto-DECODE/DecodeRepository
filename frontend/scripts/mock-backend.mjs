// Back-end de TESTE (mock) do DECODE.
// Imita as rotas do simple-object-storage nas portas 8081, 8082 e 8083,
// com dados de exemplo em memória. Use só para testar o site sem Docker.
//
//   npm run mock
//
// Conta de teste: teste@teste.com / 12345678
// Os dados somem quando o mock é encerrado (Ctrl+C).

import http from "node:http";
import crypto from "node:crypto";
import zlib from "node:zlib";

const TTL_SEGUNDOS = 900;
const usuarios = new Map(); // email -> { id, name, email, password, createdAt }
const tokens = new Map(); // token -> { userId, expiraEm }
const buckets = new Map(); // id -> { id, ownerId, name, status, createdAt }
const objetos = new Map(); // id -> { meta, bytes }

// ---------- Utilidades ----------

function agora(diasAtras = 0) {
  return new Date(Date.now() - diasAtras * 86400000).toISOString();
}

function sha256(bytes) {
  return crypto.createHash("sha256").update(bytes).digest("hex");
}

function erro(res, status, code, message) {
  enviarJson(res, status, { code, message, status, timestamp: agora(), traceId: crypto.randomUUID() });
}

function enviarJson(res, status, corpo) {
  res.writeHead(status, { "Content-Type": "application/json" });
  res.end(JSON.stringify(corpo));
}

async function lerCorpo(req) {
  const partes = [];
  for await (const parte of req) partes.push(parte);
  return Buffer.concat(partes);
}

async function lerJson(req) {
  try {
    return JSON.parse((await lerCorpo(req)).toString("utf8") || "{}");
  } catch {
    return {};
  }
}

function usuarioDoToken(req) {
  const cabecalho = req.headers.authorization ?? "";
  const token = cabecalho.startsWith("Bearer ") ? cabecalho.slice(7) : "";
  const sessao = tokens.get(token);
  if (!sessao || sessao.expiraEm < Date.now()) return null;
  return sessao.userId;
}

// PNG de uma cor só, para as imagens de exemplo terem prévia
function gerarPng(largura, altura, [r, g, b]) {
  const crcTabela = Array.from({ length: 256 }, (_, n) => {
    let c = n;
    for (let k = 0; k < 8; k++) c = c & 1 ? 0xedb88320 ^ (c >>> 1) : c >>> 1;
    return c >>> 0;
  });
  const crc = (buf) => {
    let c = 0xffffffff;
    for (const byte of buf) c = crcTabela[(c ^ byte) & 0xff] ^ (c >>> 8);
    return (c ^ 0xffffffff) >>> 0;
  };
  const bloco = (tipo, dados) => {
    const tamanho = Buffer.alloc(4);
    tamanho.writeUInt32BE(dados.length);
    const tipoBuf = Buffer.from(tipo, "ascii");
    const crcBuf = Buffer.alloc(4);
    crcBuf.writeUInt32BE(crc(Buffer.concat([tipoBuf, dados])));
    return Buffer.concat([tamanho, tipoBuf, dados, crcBuf]);
  };
  const ihdr = Buffer.alloc(13);
  ihdr.writeUInt32BE(largura, 0);
  ihdr.writeUInt32BE(altura, 4);
  ihdr[8] = 8;
  ihdr[9] = 2;
  const linha = Buffer.alloc(1 + largura * 3);
  for (let x = 0; x < largura; x++) linha.set([r, g, b], 1 + x * 3);
  const bruto = Buffer.concat(Array.from({ length: altura }, () => linha));
  return Buffer.concat([
    Buffer.from([0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a]),
    bloco("IHDR", ihdr),
    bloco("IDAT", zlib.deflateSync(bruto)),
    bloco("IEND", Buffer.alloc(0)),
  ]);
}

function adicionarObjeto(bucketId, nome, contentType, bytes, diasAtras = 0) {
  const meta = {
    id: crypto.randomUUID(),
    bucketId,
    originalFilename: nome,
    sizeBytes: bytes.length,
    contentType,
    sha256: sha256(bytes),
    createdAt: agora(diasAtras),
  };
  objetos.set(meta.id, { meta, bytes });
  return meta;
}

function criarBucket(ownerId, name, diasAtras = 0) {
  const bucket = { id: crypto.randomUUID(), ownerId, name, status: "ACTIVE", createdAt: agora(diasAtras) };
  buckets.set(bucket.id, bucket);
  return bucket;
}

function respostaBucket(b) {
  return { id: b.id, name: b.name, status: b.status, createdAt: b.createdAt };
}

// ---------- Dados de exemplo ----------

function semear() {
  const id = crypto.randomUUID();
  usuarios.set("teste@teste.com", { id, name: "Usuário Teste", email: "teste@teste.com", password: "12345678", createdAt: agora(60) });

  const documentos = criarBucket(id, "documentos", 40);
  adicionarObjeto(documentos.id, "ata-reuniao.pdf", "application/pdf", Buffer.from("%PDF-1.4 ata de exemplo"), 2);
  adicionarObjeto(
    documentos.id,
    "orcamento-2026.xlsx",
    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
    Buffer.from("planilha de exemplo"),
    10
  );
  adicionarObjeto(documentos.id, "relatorio-mensal-com-um-nome-bem-comprido.pdf", "application/pdf", Buffer.from("%PDF-1.4 relatório"), 30);

  const fotos = criarBucket(id, "fotos-obra", 30);
  const cores = [[29, 78, 216], [4, 120, 87], [180, 83, 9], [124, 58, 237], [14, 116, 144]];
  for (let i = 25; i >= 1; i--) {
    const png = gerarPng(400, 300, cores[i % cores.length]);
    adicionarObjeto(fotos.id, `foto-${String(i).padStart(3, "0")}.png`, "image/png", png, 26 - i);
  }

  const contratos = criarBucket(id, "contratos", 20);
  adicionarObjeto(contratos.id, "contrato-fornecedor.pdf", "application/pdf", Buffer.from("%PDF-1.4 contrato"), 15);

  const relatorios = criarBucket(id, "relatorios-mensais", 12);
  const XLSX = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
  for (const mes of ["janeiro", "fevereiro", "marco", "abril", "maio", "junho"]) {
    adicionarObjeto(relatorios.id, `relatorio-${mes}.xlsx`, XLSX, Buffer.from(`planilha ${mes}`), 8);
  }
  adicionarObjeto(relatorios.id, "resumo-semestre.pdf", "application/pdf", Buffer.from("%PDF-1.4 resumo"), 7);

  const videos = criarBucket(id, "videos-treinamento", 9);
  adicionarObjeto(videos.id, "integracao-nr18.mp4", "video/mp4", Buffer.from("video de exemplo 1"), 6);
  adicionarObjeto(videos.id, "uso-de-epi.mp4", "video/mp4", Buffer.from("video de exemplo 2"), 6);
  adicionarObjeto(videos.id, "capa.png", "image/png", gerarPng(400, 300, [16, 185, 129]), 6);

  criarBucket(id, "backups", 5); // vazio: pode ser excluído
}

// ---------- auth-service (8081) ----------

async function rotasAuth(req, res, url) {
  if (req.method === "POST" && url.pathname === "/api/auth/register") {
    const { name = "", email = "", password = "" } = await lerJson(req);
    if (!name.trim() || name.length > 100 || !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email) || password.length < 8 || password.length > 128) {
      return erro(res, 400, "VALIDATION_ERROR", "Dados inválidos");
    }
    const chave = email.toLowerCase();
    if (usuarios.has(chave)) return erro(res, 409, "EMAIL_ALREADY_IN_USE", "E-mail já cadastrado");
    const usuario = { id: crypto.randomUUID(), name: name.trim(), email, password, createdAt: agora() };
    usuarios.set(chave, usuario);
    return enviarJson(res, 201, { id: usuario.id, name: usuario.name, email: usuario.email, createdAt: usuario.createdAt });
  }

  if (req.method === "POST" && url.pathname === "/api/auth/login") {
    const { email = "", password = "" } = await lerJson(req);
    const usuario = usuarios.get(email.toLowerCase());
    if (!usuario || usuario.password !== password) return erro(res, 401, "INVALID_CREDENTIALS", "Credenciais inválidas");
    const token = `mock-${crypto.randomUUID()}`;
    tokens.set(token, { userId: usuario.id, expiraEm: Date.now() + TTL_SEGUNDOS * 1000 });
    return enviarJson(res, 200, { accessToken: token, tokenType: "Bearer", expiresIn: TTL_SEGUNDOS });
  }

  return erro(res, 404, "HTTP_ERROR", "Rota não encontrada");
}

// ---------- bucket-service (8082) ----------

async function rotasBuckets(req, res, url) {
  const userId = usuarioDoToken(req);
  if (!userId) return erro(res, 401, "UNAUTHORIZED", "Token ausente ou inválido");

  if (url.pathname === "/api/buckets") {
    if (req.method === "GET") {
      const lista = [...buckets.values()].filter((b) => b.ownerId === userId).map(respostaBucket);
      return enviarJson(res, 200, lista);
    }
    if (req.method === "POST") {
      const { name = "" } = await lerJson(req);
      if (name.length < 3 || name.length > 63 || !/^[a-z0-9]([a-z0-9-]*[a-z0-9])?$/.test(name)) {
        return erro(res, 400, "VALIDATION_ERROR", "Nome inválido");
      }
      const repetido = [...buckets.values()].some((b) => b.ownerId === userId && b.name === name);
      if (repetido) return erro(res, 409, "BUCKET_NAME_ALREADY_EXISTS", "Nome já existe");
      return enviarJson(res, 201, respostaBucket(criarBucket(userId, name)));
    }
  }

  const id = url.pathname.split("/")[3];
  const bucket = buckets.get(id);
  if (!bucket || bucket.ownerId !== userId) return erro(res, 404, "BUCKET_NOT_FOUND", "Bucket não encontrado");

  if (req.method === "GET") return enviarJson(res, 200, respostaBucket(bucket));
  if (req.method === "DELETE") {
    const temArquivos = [...objetos.values()].some((o) => o.meta.bucketId === id);
    if (temArquivos) return erro(res, 409, "BUCKET_NOT_EMPTY", "Bucket não está vazio");
    buckets.delete(id);
    res.writeHead(204);
    return res.end();
  }
  return erro(res, 404, "HTTP_ERROR", "Rota não encontrada");
}

// ---------- object-service (8083) ----------

async function rotasObjetos(req, res, url) {
  const userId = usuarioDoToken(req);
  if (!userId) return erro(res, 401, "UNAUTHORIZED", "Token ausente ou inválido");

  // /api/buckets/{bucketId}/objects[/{objectId}[/download]]
  const [, , , bucketId, , objectId, extra] = url.pathname.split("/");
  const bucket = buckets.get(bucketId);
  if (!bucket || bucket.ownerId !== userId) return erro(res, 404, "BUCKET_NOT_FOUND", "Bucket não encontrado");

  if (!objectId) {
    if (req.method === "GET") {
      const page = Number(url.searchParams.get("page") ?? 0);
      const size = Number(url.searchParams.get("size") ?? 20);
      const todos = [...objetos.values()]
        .map((o) => o.meta)
        .filter((m) => m.bucketId === bucketId)
        .sort((a, b) => b.createdAt.localeCompare(a.createdAt));
      return enviarJson(res, 200, {
        content: todos.slice(page * size, page * size + size),
        page,
        size,
        totalElements: todos.length,
        totalPages: Math.ceil(todos.length / size),
      });
    }
    if (req.method === "POST") {
      const corpo = await lerCorpo(req);
      if (corpo.length > 51 * 1024 * 1024) return erro(res, 413, "PAYLOAD_TOO_LARGE", "Arquivo grande demais");
      const requisicao = new Request("http://mock/", { method: "POST", headers: req.headers, body: corpo });
      const formulario = await requisicao.formData();
      const arquivo = formulario.get("file");
      if (!arquivo || typeof arquivo === "string") return erro(res, 400, "VALIDATION_ERROR", "Campo file ausente");
      const bytes = Buffer.from(await arquivo.arrayBuffer());
      if (bytes.length === 0) return erro(res, 400, "EMPTY_FILE", "Arquivo vazio");
      const meta = adicionarObjeto(bucketId, arquivo.name, arquivo.type || "application/octet-stream", bytes);
      return enviarJson(res, 201, meta);
    }
  }

  const objeto = objetos.get(objectId);
  if (!objeto || objeto.meta.bucketId !== bucketId) return erro(res, 404, "OBJECT_NOT_FOUND", "Arquivo não encontrado");

  if (req.method === "GET" && extra === "download") {
    res.writeHead(200, {
      "Content-Type": "application/octet-stream",
      "Content-Disposition": `attachment; filename="${encodeURIComponent(objeto.meta.originalFilename)}"`,
      "X-Content-Type-Options": "nosniff",
    });
    return res.end(objeto.bytes);
  }
  if (req.method === "GET") return enviarJson(res, 200, objeto.meta);
  if (req.method === "DELETE") {
    objetos.delete(objectId);
    res.writeHead(204);
    return res.end();
  }
  return erro(res, 404, "HTTP_ERROR", "Rota não encontrada");
}

// ---------- Servidores ----------

function servidor(porta, nome, rotas) {
  http
    .createServer(async (req, res) => {
      const url = new URL(req.url ?? "/", `http://localhost:${porta}`);
      try {
        await rotas(req, res, url);
      } catch (e) {
        console.error(nome, e);
        erro(res, 500, "INTERNAL_ERROR", "Erro no mock");
      }
      console.log(`[${nome}] ${req.method} ${url.pathname}${url.search} -> ${res.statusCode}`);
    })
    .listen(porta, "127.0.0.1", () => console.log(`${nome} (mock) em http://localhost:${porta}`));
}

semear();
servidor(8081, "auth-service", rotasAuth);
servidor(8082, "bucket-service", rotasBuckets);
servidor(8083, "object-service", rotasObjetos);
console.log("Conta de teste: teste@teste.com / 12345678");
