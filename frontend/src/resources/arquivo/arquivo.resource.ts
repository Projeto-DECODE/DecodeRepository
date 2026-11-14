// Espelho do ObjectResponse do object-service
export class Arquivo {
  id?: string;
  bucketId?: string;
  originalFilename?: string;
  sizeBytes?: number;
  contentType?: string;
  sha256?: string;
  createdAt?: string;
}

// Espelho do PageResponse (lista paginada)
export class Pagina<T> {
  content?: T[];
  page?: number;
  size?: number;
  totalElements?: number;
  totalPages?: number;
}

// Resumo de um bucket, calculado no site a partir da lista de arquivos
export class ResumoBucket {
  total: number = 0;
  imagens: number = 0;
  docs: number = 0;
  outros: number = 0;
  // Tipo que mais aparece (define o ícone do cartão). null = bucket vazio
  tipoPrincipal: "imagem" | "pdf" | "planilha" | "outro" | null = null;
}
