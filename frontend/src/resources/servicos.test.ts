import { describe, expect, it } from "vitest";
import { fetchFalso, logar, resposta } from "@/test/ajuda";
import { useArquivoService } from "./arquivo/arquivo.service";
import { useAuthService } from "./auth/auth.service";
import { useBucketService } from "./bucket/bucket.service";
import { Sessao } from "./sessao";

const corpoDe = (fetch: ReturnType<typeof fetchFalso>, chamada = 0) =>
  JSON.parse(fetch.mock.calls[chamada][1].body as string);

describe("AuthService", () => {
  it("cadastrar envia name, email e password", async () => {
    const fetch = fetchFalso(resposta(201, { id: "1", name: "Maria", email: "m@t.com" }));

    const usuario = await useAuthService().cadastrar("Maria", "m@t.com", "12345678");

    expect(fetch.mock.calls[0][0]).toBe("/api/auth/register");
    expect(corpoDe(fetch)).toEqual({ name: "Maria", email: "m@t.com", password: "12345678" });
    expect(usuario.name).toBe("Maria");
  });

  it("login salva a sessão com o token e a validade", async () => {
    fetchFalso(resposta(200, { accessToken: "tok", tokenType: "Bearer", expiresIn: 900 }));

    await useAuthService().login("m@t.com", "12345678");

    const sessao = new Sessao();
    expect(sessao.isValida()).toBe(true);
    expect(sessao.getCabecalho()).toBe("Bearer tok");
    expect(sessao.getEmail()).toBe("m@t.com");
  });
});

describe("BucketService", () => {
  it("lista, busca, cria e exclui nas rotas certas", async () => {
    logar();
    const fetch = fetchFalso(
      resposta(200, [{ id: "1", name: "docs" }]),
      resposta(200, { id: "1", name: "docs" }),
      resposta(201, { id: "2", name: "novo" }),
      resposta(204)
    );
    const service = useBucketService();

    expect(await service.listar()).toHaveLength(1);
    expect((await service.buscar("1")).name).toBe("docs");
    expect((await service.criar("novo")).id).toBe("2");
    await service.excluir("2");

    expect(fetch.mock.calls.map((c) => [c[0], c[1].method ?? "GET"])).toEqual([
      ["/api/buckets", "GET"],
      ["/api/buckets/1", "GET"],
      ["/api/buckets", "POST"],
      ["/api/buckets/2", "DELETE"],
    ]);
    expect(corpoDe(fetch, 2)).toEqual({ name: "novo" });
  });
});

describe("ArquivoService", () => {
  const arquivo = (nome: string, tipo: string) => ({ id: nome, originalFilename: nome, contentType: tipo, sizeBytes: 10 });

  it("lista com página e tamanho na URL", async () => {
    logar();
    const fetch = fetchFalso(resposta(200, { content: [], totalElements: 0, totalPages: 0 }));

    await useArquivoService().listar("b1", 2);

    expect(fetch.mock.calls[0][0]).toBe("/api/buckets/b1/objects?page=2&size=20");
  });

  it("contar lê o total de uma página de 1 item", async () => {
    logar();
    const fetch = fetchFalso(resposta(200, { content: [], totalElements: 42, totalPages: 42 }));

    expect(await useArquivoService().contar("b1")).toBe(42);
    expect(fetch.mock.calls[0][0]).toBe("/api/buckets/b1/objects?page=0&size=1");
  });

  it("envia o arquivo no campo file (multipart)", async () => {
    logar();
    const fetch = fetchFalso(resposta(201, { id: "x", originalFilename: "nota.txt" }));
    const file = new File(["olá"], "nota.txt", { type: "text/plain" });

    await useArquivoService().enviar("b1", file);

    const [url, opcoes] = fetch.mock.calls[0];
    expect(url).toBe("/api/buckets/b1/objects");
    expect(opcoes.method).toBe("POST");
    expect((opcoes.body as FormData).get("file")).toBeInstanceOf(File);
  });

  it("baixa os bytes com o token e exclui", async () => {
    logar();
    const fetch = fetchFalso(new Response("conteudo", { status: 200 }), resposta(204));
    const service = useArquivoService();

    const blob = await service.baixar("b1", "a1");
    await service.excluir("b1", "a1");

    expect(await blob.text()).toBe("conteudo");
    expect(fetch.mock.calls[0][0]).toBe("/api/buckets/b1/objects/a1/download");
    expect(fetch.mock.calls[1][1].method).toBe("DELETE");
  });

  it("resumir soma todas as páginas e acha o tipo mais comum", async () => {
    logar();
    const fetch = fetchFalso(
      resposta(200, {
        content: [arquivo("a.png", "image/png"), arquivo("b.pdf", "application/pdf"), arquivo("c.xlsx", "")],
        totalElements: 5,
        totalPages: 2,
      }),
      resposta(200, {
        content: [arquivo("d.xlsx", ""), arquivo("e.mp4", "video/mp4")],
        totalElements: 5,
        totalPages: 2,
      })
    );

    const resumo = await useArquivoService().resumir("b1");

    expect(fetch).toHaveBeenCalledTimes(2);
    expect(fetch.mock.calls[0][0]).toBe("/api/buckets/b1/objects?page=0&size=100");
    expect(resumo).toMatchObject({ total: 5, imagens: 1, docs: 3, outros: 1, tipoPrincipal: "planilha" });
  });

  it("resumo de bucket vazio não tem tipo principal", async () => {
    logar();
    fetchFalso(resposta(200, { content: [], totalElements: 0, totalPages: 0 }));

    const resumo = await useArquivoService().resumir("b1");

    expect(resumo.total).toBe(0);
    expect(resumo.tipoPrincipal).toBeNull();
  });
});
