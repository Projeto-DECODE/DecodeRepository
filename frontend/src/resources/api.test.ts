import { describe, expect, it, vi } from "vitest";
import { fetchFalso, logar, resposta } from "@/test/ajuda";
import { chamarApi } from "./api";
import { ErroApi, isSessaoExpirada, mensagemDeErro } from "./erro";

describe("chamarApi", () => {
  it("envia o token no cabeçalho Authorization", async () => {
    logar();
    const fetch = fetchFalso(resposta(200, []));

    await chamarApi("/api/buckets");

    const headers = fetch.mock.calls[0][1].headers as Headers;
    expect(headers.get("Authorization")).toBe("Bearer token-de-teste");
  });

  it("sem sessão válida, nem chama o servidor", async () => {
    const fetch = fetchFalso();

    await expect(chamarApi("/api/buckets")).rejects.toMatchObject({ codigo: "UNAUTHORIZED" });
    expect(fetch).not.toHaveBeenCalled();
  });

  it("rota pública (login) não envia token", async () => {
    const fetch = fetchFalso(resposta(200, {}));

    await chamarApi("/api/auth/login", { method: "POST" }, false);

    const headers = fetch.mock.calls[0][1].headers as Headers;
    expect(headers.has("Authorization")).toBe(false);
  });

  it("401 numa rota protegida vira sessão expirada", async () => {
    logar();
    fetchFalso(resposta(401, { code: "UNAUTHORIZED" }));

    const erro = await chamarApi("/api/buckets").catch((e) => e);

    expect(isSessaoExpirada(erro)).toBe(true);
  });

  it("401 no login é senha errada (não sessão expirada)", async () => {
    fetchFalso(resposta(401, { code: "INVALID_CREDENTIALS", message: "Credenciais inválidas" }));

    const erro = await chamarApi("/api/auth/login", { method: "POST" }, false).catch((e) => e);

    expect(erro).toBeInstanceOf(ErroApi);
    expect(erro.codigo).toBe("INVALID_CREDENTIALS");
    expect(isSessaoExpirada(erro)).toBe(false);
  });

  it("lê o código de erro do JSON do back-end", async () => {
    logar();
    fetchFalso(resposta(409, { code: "BUCKET_NOT_EMPTY", message: "Bucket não está vazio" }));

    await expect(chamarApi("/api/buckets/1", { method: "DELETE" })).rejects.toMatchObject({
      codigo: "BUCKET_NOT_EMPTY",
    });
  });

  it("servidor fora do ar vira SEM_CONEXAO", async () => {
    logar();
    vi.stubGlobal("fetch", vi.fn().mockRejectedValue(new TypeError("Failed to fetch")));

    await expect(chamarApi("/api/buckets")).rejects.toMatchObject({ codigo: "SEM_CONEXAO" });
  });

  it("erro 5xx sem JSON também vira SEM_CONEXAO", async () => {
    logar();
    fetchFalso(new Response("Bad Gateway", { status: 502 }));

    await expect(chamarApi("/api/buckets")).rejects.toMatchObject({ codigo: "SEM_CONEXAO" });
  });

  it("erro 4xx sem JSON vira HTTP_ERROR", async () => {
    logar();
    fetchFalso(new Response("nada", { status: 404 }));

    await expect(chamarApi("/api/x")).rejects.toMatchObject({ codigo: "HTTP_ERROR" });
  });
});

describe("mensagemDeErro", () => {
  it("traduz os códigos do back-end", () => {
    expect(mensagemDeErro(new ErroApi("BUCKET_NOT_EMPTY"))).toBe("Esvazie o bucket antes de excluí-lo.");
    expect(mensagemDeErro(new ErroApi("INVALID_CREDENTIALS"))).toBe("E-mail ou senha incorretos.");
    expect(mensagemDeErro(new ErroApi("SEM_CONEXAO"))).toBe("Não foi possível conectar ao servidor.");
  });

  it("usa a mensagem do servidor para códigos desconhecidos", () => {
    expect(mensagemDeErro(new ErroApi("CODIGO_NOVO", "Mensagem do servidor"))).toBe("Mensagem do servidor");
  });

  it("tem uma mensagem genérica para erros inesperados", () => {
    expect(mensagemDeErro(new Error("x"))).toBe("Algo deu errado. Tente de novo.");
  });
});
