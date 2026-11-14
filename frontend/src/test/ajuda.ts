import { vi } from "vitest";
import { Sessao } from "@/resources/sessao";

// Resposta falsa do fetch, no formato do back-end
export function resposta(status: number, corpo?: unknown): Response {
  const texto = corpo === undefined ? "" : JSON.stringify(corpo);
  return new Response(status === 204 ? null : texto, {
    status,
    headers: { "Content-Type": "application/json" },
  });
}

// Troca o fetch do navegador por um falso que devolve as respostas na ordem
export function fetchFalso(...respostas: Response[]) {
  const fn = vi.fn();
  for (const r of respostas) fn.mockResolvedValueOnce(r);
  vi.stubGlobal("fetch", fn);
  return fn;
}

// Deixa um usuário logado (token válido por 15 min)
export function logar(email = "teste@teste.com") {
  new Sessao().salvarLogin("token-de-teste", 900, email);
}
