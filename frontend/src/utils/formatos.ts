// 3145728 -> "3,0 MB"
export function formatarTamanho(bytes: number = 0): string {
  if (bytes < 1024) return `${bytes} B`;
  const kb = bytes / 1024;
  if (kb < 1024) return `${kb.toLocaleString("pt-BR", { maximumFractionDigits: 1, minimumFractionDigits: 1 })} KB`;
  const mb = kb / 1024;
  return `${mb.toLocaleString("pt-BR", { maximumFractionDigits: 1, minimumFractionDigits: 1 })} MB`;
}

// "2026-10-18T12:00:00Z" -> "18/10/2026"
export function formatarData(iso: string = ""): string {
  const data = new Date(iso);
  if (isNaN(data.getTime())) return iso;
  return data.toLocaleDateString("pt-BR");
}

// 8 primeiros + … + 6 últimos
export function abreviarSha(sha: string = ""): string {
  if (sha.length <= 14) return sha || "—";
  return `${sha.slice(0, 8)}…${sha.slice(-6)}`;
}

export type TipoArquivo = "imagem" | "pdf" | "planilha" | "outro";

export function tipoDoArquivo(contentType: string = "", nome: string = ""): TipoArquivo {
  const extensao = nome.split(".").pop()?.toLowerCase() ?? "";
  if (contentType.startsWith("image/")) return "imagem";
  if (contentType === "application/pdf" || extensao === "pdf") return "pdf";
  if (contentType.includes("spreadsheet") || contentType.includes("excel") || ["xlsx", "xls", "csv"].includes(extensao)) {
    return "planilha";
  }
  return "outro";
}

// Agrupamento usado nos números do cartão do bucket: Imagens · Docs · Outros
export type CategoriaArquivo = "imagem" | "doc" | "outro";

const EXTENSOES_DOC = ["pdf", "doc", "docx", "odt", "txt", "rtf", "md", "xls", "xlsx", "ods", "csv", "ppt", "pptx", "odp"];

export function categoriaDoArquivo(contentType: string = "", nome: string = ""): CategoriaArquivo {
  const tipo = tipoDoArquivo(contentType, nome);
  if (tipo === "imagem") return "imagem";
  if (tipo === "pdf" || tipo === "planilha") return "doc";
  const extensao = nome.split(".").pop()?.toLowerCase() ?? "";
  if (
    contentType.startsWith("text/") ||
    contentType.includes("word") ||
    contentType.includes("document") ||
    contentType.includes("presentation") ||
    EXTENSOES_DOC.includes(extensao)
  ) {
    return "doc";
  }
  return "outro";
}

// Mesmo regex do back-end para nome de bucket
export const REGEX_NOME_BUCKET = /^[a-z0-9]([a-z0-9-]*[a-z0-9])?$/;
