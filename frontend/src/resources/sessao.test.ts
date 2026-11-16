import { act, renderHook } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import { Sessao, useUsuarioLogado } from "./sessao";

describe("Sessao", () => {
  it("fica válida depois do login e guarda o e-mail", () => {
    const sessao = new Sessao();
    expect(sessao.isValida()).toBe(false);

    sessao.salvarLogin("abc", 900, "maria@teste.com");

    expect(sessao.isValida()).toBe(true);
    expect(sessao.temToken()).toBe(true);
    expect(sessao.getCabecalho()).toBe("Bearer abc");
    expect(sessao.getEmail()).toBe("maria@teste.com");
  });

  it("vence depois do tempo do token (expiresIn)", () => {
    vi.useFakeTimers();
    const sessao = new Sessao();
    sessao.salvarLogin("abc", 900, "maria@teste.com");

    vi.advanceTimersByTime(899 * 1000);
    expect(sessao.isValida()).toBe(true);

    vi.advanceTimersByTime(2 * 1000);
    expect(sessao.isValida()).toBe(false);
    expect(sessao.temToken()).toBe(true); // vencida, mas o token ainda está salvo
  });

  it("mostra o nome, ou o e-mail quando não há nome", () => {
    const sessao = new Sessao();
    sessao.salvarLogin("abc", 900, "maria@teste.com");
    expect(sessao.getNomeExibicao()).toBe("maria@teste.com");

    sessao.salvarNome("Maria");
    expect(sessao.getNomeExibicao()).toBe("Maria");
  });

  it("apaga o nome quando entra outra pessoa", () => {
    const sessao = new Sessao();
    sessao.salvarLogin("abc", 900, "maria@teste.com");
    sessao.salvarNome("Maria");

    sessao.salvarLogin("def", 900, "joao@teste.com");

    expect(sessao.getNome()).toBe("");
  });

  it("limpar remove o token, mas mantém o e-mail", () => {
    const sessao = new Sessao();
    sessao.salvarLogin("abc", 900, "maria@teste.com");

    sessao.limpar();

    expect(sessao.isValida()).toBe(false);
    expect(sessao.temToken()).toBe(false);
    expect(sessao.getEmail()).toBe("maria@teste.com");
  });

  it("não quebra se o navegador bloquear o armazenamento", () => {
    vi.spyOn(Storage.prototype, "getItem").mockImplementation(() => {
      throw new Error("bloqueado");
    });
    vi.spyOn(Storage.prototype, "setItem").mockImplementation(() => {
      throw new Error("bloqueado");
    });
    vi.spyOn(Storage.prototype, "removeItem").mockImplementation(() => {
      throw new Error("bloqueado");
    });
    const sessao = new Sessao();

    expect(() => sessao.salvarLogin("abc", 900, "a@a.com")).not.toThrow();
    expect(() => sessao.limpar()).not.toThrow();
    expect(sessao.isValida()).toBe(false);
  });
});

describe("useUsuarioLogado", () => {
  it("atualiza sozinho quando a pessoa entra e sai", () => {
    const { result } = renderHook(() => useUsuarioLogado());
    expect(result.current).toBe("");

    act(() => new Sessao().salvarLogin("abc", 900, "maria@teste.com"));
    expect(result.current).toBe("maria@teste.com");

    act(() => new Sessao().limpar());
    expect(result.current).toBe("");
  });
});
