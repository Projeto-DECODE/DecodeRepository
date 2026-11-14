import { useSyncExternalStore } from "react";

// Sessão do usuário guardada no navegador (localStorage).
// Mesma ideia do Sessao.java do app Android. Nunca escrever o token no console.

// Avisa as telas abertas que a sessão mudou (entrou ou saiu)
const EVENTO_SESSAO = "decode-sessao";
function avisarMudanca() {
  window.dispatchEvent(new Event(EVENTO_SESSAO));
}

const CHAVE_TOKEN = "decode.token";
const CHAVE_EXPIRA_EM = "decode.expiraEm";
const CHAVE_EMAIL = "decode.email";
const CHAVE_NOME = "decode.nome";

function ler(chave: string): string | null {
  try {
    return window.localStorage.getItem(chave);
  } catch {
    return null;
  }
}

function gravar(chave: string, valor: string) {
  try {
    window.localStorage.setItem(chave, valor);
  } catch {
    // Navegador sem acesso ao armazenamento: segue sem salvar
  }
}

function apagar(chave: string) {
  try {
    window.localStorage.removeItem(chave);
  } catch {
    // Nada a fazer
  }
}

export class Sessao {
  // expiresInSegundos vem do back-end (900 = 15 minutos)
  salvarLogin(token: string, expiresInSegundos: number, email: string) {
    // Entrou outra pessoa: o nome guardado não é dela
    if (email.toLowerCase() !== this.getEmail().toLowerCase()) {
      apagar(CHAVE_NOME);
    }
    gravar(CHAVE_TOKEN, token);
    gravar(CHAVE_EXPIRA_EM, String(Date.now() + expiresInSegundos * 1000));
    gravar(CHAVE_EMAIL, email);
    avisarMudanca();
  }

  // O token não traz o nome: ele só é conhecido no cadastro
  salvarNome(nome: string) {
    gravar(CHAVE_NOME, nome);
    avisarMudanca();
  }

  // Existe um token salvo (mesmo que vencido)?
  temToken(): boolean {
    return ler(CHAVE_TOKEN) !== null;
  }

  isValida(): boolean {
    const expiraEm = Number(ler(CHAVE_EXPIRA_EM) ?? 0);
    return ler(CHAVE_TOKEN) !== null && Date.now() < expiraEm;
  }

  // Valor do cabeçalho Authorization
  getCabecalho(): string {
    return `Bearer ${ler(CHAVE_TOKEN) ?? ""}`;
  }

  getEmail(): string {
    return ler(CHAVE_EMAIL) ?? "";
  }

  getNome(): string {
    return ler(CHAVE_NOME) ?? "";
  }

  // Nome, ou o e-mail se o nome não estiver salvo
  getNomeExibicao(): string {
    return this.getNome() || this.getEmail();
  }

  // Remove o token. E-mail e nome ficam para quando a mesma pessoa entrar de novo.
  limpar() {
    apagar(CHAVE_TOKEN);
    apagar(CHAVE_EXPIRA_EM);
    avisarMudanca();
  }
}

export const useSessao = () => new Sessao();

function assinar(callback: () => void) {
  window.addEventListener(EVENTO_SESSAO, callback);
  window.addEventListener("storage", callback); // outra aba entrou ou saiu
  return () => {
    window.removeEventListener(EVENTO_SESSAO, callback);
    window.removeEventListener("storage", callback);
  };
}

// Nome de quem está logado ("" se ninguém). Atualiza a tela sozinho quando a sessão muda.
// No servidor não existe localStorage, então lá o valor é sempre "".
export function useUsuarioLogado(): string {
  return useSyncExternalStore(
    assinar,
    () => {
      const sessao = new Sessao();
      return sessao.isValida() ? sessao.getNomeExibicao() : "";
    },
    () => ""
  );
}
