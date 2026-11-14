import { act, renderHook } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import { ErroApi, Sessao } from "@/resources";
import { logar } from "@/test/ajuda";
import { navegacao } from "@/test/setup";
import {
  salvarUltimoBucket,
  useAviso,
  useModoExibicao,
  useProtegerPagina,
  useTratarErro,
  useUltimoBucket,
} from "./hooks";

describe("useAviso", () => {
  it("mostra o aviso e esconde sozinho depois do tempo", () => {
    vi.useFakeTimers();
    const { result } = renderHook(() => useAviso());

    act(() => result.current[1]({ mensagem: "Bucket criado" }, 3000));
    expect(result.current[0]?.mensagem).toBe("Bucket criado");

    act(() => vi.advanceTimersByTime(3000));
    expect(result.current[0]).toBeNull();
  });

  it("fechar esconde na hora", () => {
    const { result } = renderHook(() => useAviso());
    act(() => result.current[1]({ mensagem: "x" }));
    act(() => result.current[2]());
    expect(result.current[0]).toBeNull();
  });
});

describe("useProtegerPagina", () => {
  it("sem login, manda para /login", () => {
    renderHook(() => useProtegerPagina());
    expect(navegacao.replace).toHaveBeenCalledWith("/login");
  });

  it("sessão vencida manda para /login?expirou=1", () => {
    vi.useFakeTimers();
    logar();
    vi.advanceTimersByTime(16 * 60 * 1000);
    renderHook(() => useProtegerPagina());
    expect(navegacao.replace).toHaveBeenCalledWith("/login?expirou=1");
  });

  it("logado, fica na página", () => {
    logar();
    renderHook(() => useProtegerPagina());
    expect(navegacao.replace).not.toHaveBeenCalled();
  });
});

describe("useTratarErro", () => {
  it("sessão expirada limpa o login e volta ao login", () => {
    logar();
    const { result } = renderHook(() => useTratarErro());

    expect(result.current(new ErroApi("UNAUTHORIZED"))).toBeNull();
    expect(new Sessao().isValida()).toBe(false);
    expect(navegacao.replace).toHaveBeenCalledWith("/login?expirou=1");
  });

  it("outros erros viram mensagem em português", () => {
    const { result } = renderHook(() => useTratarErro());
    expect(result.current(new ErroApi("BUCKET_NAME_ALREADY_EXISTS"))).toBe("Já existe um bucket com esse nome.");
  });
});

describe("modo de exibição e último bucket (lembrados no navegador)", () => {
  it("modo começa no rotativo e guarda a escolha", () => {
    const { result } = renderHook(() => useModoExibicao());
    expect(result.current[0]).toBe("rotativo");

    act(() => result.current[1]("lista"));
    expect(result.current[0]).toBe("lista");
    expect(window.localStorage.getItem("decode.modoBuckets")).toBe("lista");
  });

  it("guarda o último bucket aberto", () => {
    salvarUltimoBucket("b9");
    const { result } = renderHook(() => useUltimoBucket());
    expect(result.current).toBe("b9");
  });
});
