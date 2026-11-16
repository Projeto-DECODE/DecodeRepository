import { chamarApi } from "../api";
import { categoriaDoArquivo, TipoArquivo, tipoDoArquivo } from "@/utils/formatos";
import { Arquivo, Pagina, ResumoBucket } from "./arquivo.resource";

export const TAMANHO_PAGINA = 20;
const PAGINA_MAXIMA_BACKEND = 100; // limite do object-service
const LIMITE_PAGINAS_RESUMO = 10; // conta no máximo 1.000 arquivos por bucket
export const LIMITE_UPLOAD_BYTES = 10 * 1024 * 1024; // 10 MB
export const LIMITE_PREVIA_BYTES = 10 * 1024 * 1024; // 10 MB

class ArquivoService {
  baseURL: string = "/api/buckets";

  async listar(bucketId: string, page: number = 0, size: number = TAMANHO_PAGINA): Promise<Pagina<Arquivo>> {
    const url = `${this.baseURL}/${bucketId}/objects?page=${page}&size=${size}`;
    const response = await chamarApi(url);
    return await response.json();
  }

  // Quantidade de arquivos do bucket (pede uma página de 1 item e lê o total)
  async contar(bucketId: string): Promise<number> {
    const pagina = await this.listar(bucketId, 0, 1);
    return pagina.totalElements ?? 0;
  }

  // Conta os arquivos por tipo (Imagens · Docs · Outros) e descobre o tipo mais comum
  async resumir(bucketId: string): Promise<ResumoBucket> {
    const resumo = new ResumoBucket();
    const porTipo: Record<TipoArquivo, number> = { imagem: 0, pdf: 0, planilha: 0, outro: 0 };
    let pagina = 0;
    let totalPaginas = 1;
    while (pagina < totalPaginas && pagina < LIMITE_PAGINAS_RESUMO) {
      const resultado = await this.listar(bucketId, pagina, PAGINA_MAXIMA_BACKEND);
      resumo.total = resultado.totalElements ?? 0;
      totalPaginas = resultado.totalPages ?? 0;
      for (const arquivo of resultado.content ?? []) {
        const categoria = categoriaDoArquivo(arquivo.contentType, arquivo.originalFilename);
        if (categoria === "imagem") resumo.imagens++;
        else if (categoria === "doc") resumo.docs++;
        else resumo.outros++;
        porTipo[tipoDoArquivo(arquivo.contentType, arquivo.originalFilename)]++;
      }
      pagina++;
    }
    const [maisComum, quantidade] = Object.entries(porTipo).sort((a, b) => b[1] - a[1])[0];
    resumo.tipoPrincipal = quantidade > 0 ? (maisComum as TipoArquivo) : null;
    return resumo;
  }

  // Envio em multipart/form-data, campo "file" (o navegador monta o cabeçalho sozinho)
  async enviar(bucketId: string, arquivo: File): Promise<Arquivo> {
    const dados = new FormData();
    dados.append("file", arquivo);
    const response = await chamarApi(`${this.baseURL}/${bucketId}/objects`, {
      method: "POST",
      body: dados,
    });
    return await response.json();
  }

  // Bytes do arquivo. O download exige o token, por isso não dá para usar só window.open.
  async baixar(bucketId: string, arquivoId: string): Promise<Blob> {
    const response = await chamarApi(`${this.baseURL}/${bucketId}/objects/${arquivoId}/download`);
    return await response.blob();
  }

  async excluir(bucketId: string, arquivoId: string): Promise<void> {
    await chamarApi(`${this.baseURL}/${bucketId}/objects/${arquivoId}`, { method: "DELETE" });
  }
}

export const useArquivoService = () => new ArquivoService();
