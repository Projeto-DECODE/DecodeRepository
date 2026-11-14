// Erro devolvido pelo back-end: { code, message, status, details }
export class ErroApi extends Error {
  codigo: string;

  constructor(codigo: string, mensagem?: string) {
    super(mensagem ?? codigo);
    this.codigo = codigo;
  }
}

// Códigos que não vêm do back-end, só do site
export const SEM_CONEXAO = "SEM_CONEXAO";
export const ARQUIVO_GRANDE = "ARQUIVO_GRANDE";

const MENSAGENS: Record<string, string> = {
  SEM_CONEXAO: "Não foi possível conectar ao servidor.",
  ARQUIVO_GRANDE: "Arquivo maior que 10 MB.",
  VALIDATION_ERROR: "Verifique os dados informados.",
  UNAUTHORIZED: "Sua sessão expirou. Entre novamente.",
  FORBIDDEN: "Você não tem permissão para isso.",
  INVALID_CREDENTIALS: "E-mail ou senha incorretos.",
  EMAIL_ALREADY_IN_USE: "Este e-mail já está cadastrado.",
  BUCKET_NAME_ALREADY_EXISTS: "Já existe um bucket com esse nome.",
  BUCKET_NOT_FOUND: "Bucket não encontrado.",
  BUCKET_NOT_EMPTY: "Esvazie o bucket antes de excluí-lo.",
  BUCKET_NOT_WRITABLE: "Este bucket não aceita novos arquivos no momento.",
  OBJECT_NOT_FOUND: "Arquivo não encontrado.",
  EMPTY_FILE: "O arquivo está vazio.",
  PAYLOAD_TOO_LARGE: "Arquivo grande demais.",
  DEPENDENCY_UNAVAILABLE: "Serviço temporariamente indisponível. Tente mais tarde.",
  STORAGE_ERROR: "Erro ao acessar o armazenamento.",
  INTERNAL_ERROR: "Erro inesperado no servidor.",
};

// Converte qualquer erro em uma frase em português
export function mensagemDeErro(erro: unknown): string {
  if (erro instanceof ErroApi) {
    return MENSAGENS[erro.codigo] ?? (erro.message || "Algo deu errado. Tente de novo.");
  }
  return "Algo deu errado. Tente de novo.";
}

export function isSessaoExpirada(erro: unknown): boolean {
  return erro instanceof ErroApi && erro.codigo === "UNAUTHORIZED";
}
