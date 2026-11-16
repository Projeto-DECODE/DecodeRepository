import { ErroApi, SEM_CONEXAO } from "./erro";
import { Sessao } from "./sessao";

// Faz a chamada com fetch, como nas aulas, e trata os erros num lugar só.
// protegida = true: envia o token no cabeçalho Authorization.
export async function chamarApi(
  url: string,
  opcoes: RequestInit = {},
  protegida: boolean = true
): Promise<Response> {
  const headers = new Headers(opcoes.headers);
  if (protegida) {
    const sessao = new Sessao();
    // Sessão vencida (passou dos 15 min): nem chama o servidor
    if (!sessao.isValida()) {
      throw new ErroApi("UNAUTHORIZED");
    }
    headers.set("Authorization", sessao.getCabecalho());
  }

  let response: Response;
  try {
    response = await fetch(url, { ...opcoes, headers });
  } catch {
    throw new ErroApi(SEM_CONEXAO);
  }

  if (response.ok) {
    return response;
  }

  // 401 numa rota protegida = token vencido ou inválido
  if (protegida && response.status === 401) {
    throw new ErroApi("UNAUTHORIZED");
  }

  // O back-end devolve { code, message }
  try {
    const corpo = await response.json();
    throw new ErroApi(corpo.code ?? "HTTP_ERROR", corpo.message);
  } catch (erro) {
    if (erro instanceof ErroApi) throw erro;
    // Sem JSON (ex.: proxy sem resposta do serviço)
    throw new ErroApi(response.status >= 500 ? SEM_CONEXAO : "HTTP_ERROR");
  }
}
