import { act, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import { Arquivo, Sessao } from "@/resources";
import { fetchFalso, logar, resposta } from "@/test/ajuda";
import { navegacao } from "@/test/setup";
import { FormLogin } from "./FormLogin";
import { PainelDetalhe } from "./PainelDetalhe";
import { Template } from "./Template";

describe("Template (cabeçalho)", () => {
  it("visitante vê Início, Recursos, Log in e Criar conta", () => {
    render(<Template>conteúdo</Template>);

    expect(screen.getAllByRole("link", { name: "Log in" })[0]).toHaveAttribute("href", "/login");
    expect(screen.getAllByRole("link", { name: "Criar conta" })[0]).toHaveAttribute("href", "/cadastro");
    expect(screen.getByText("conteúdo")).toBeInTheDocument();
  });

  it("logado vê Olá, Perfil e Sair; Sair volta ao login", () => {
    logar("maria@teste.com");
    render(<Template>conteúdo</Template>);

    expect(screen.getByText("Olá, maria@teste.com")).toBeInTheDocument();
    fireEvent.click(screen.getAllByRole("button", { name: /Sair/ })[0]);

    expect(new Sessao().isValida()).toBe(false);
    expect(navegacao.push).toHaveBeenCalledWith("/login");
  });

  it("Perfil abre com nome e e-mail", () => {
    logar("maria@teste.com");
    new Sessao().salvarNome("Maria");
    render(<Template>x</Template>);

    fireEvent.click(screen.getAllByRole("button", { name: /Perfil/ })[0]);

    const janela = screen.getByRole("dialog", { name: "Meu perfil" });
    expect(janela).toHaveTextContent("Maria");
    expect(janela).toHaveTextContent("maria@teste.com");
    fireEvent.click(screen.getByRole("button", { name: "OK" }));
    expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
  });

  it("menu do celular abre e fecha", () => {
    render(<Template>x</Template>);

    fireEvent.click(screen.getByRole("button", { name: "Abrir menu" }));
    expect(screen.getByRole("button", { name: "Fechar menu" })).toHaveAttribute("aria-expanded", "true");
    fireEvent.click(screen.getAllByRole("link", { name: "Recursos" })[1]);
    expect(screen.getByRole("button", { name: "Abrir menu" })).toBeInTheDocument();
  });
});

describe("FormLogin", () => {
  it("campos vazios mostram Obrigatório e não chamam o servidor", () => {
    const fetch = fetchFalso();
    render(<FormLogin />);

    fireEvent.click(screen.getByRole("button", { name: "Entrar" }));

    expect(screen.getAllByText("Obrigatório")).toHaveLength(2);
    expect(fetch).not.toHaveBeenCalled();
  });

  it("senha errada mostra a mensagem do servidor traduzida", async () => {
    fetchFalso(resposta(401, { code: "INVALID_CREDENTIALS" }));
    render(<FormLogin />);

    fireEvent.change(screen.getByLabelText("E-mail"), { target: { value: "a@a.com" } });
    fireEvent.change(screen.getByLabelText("Senha"), { target: { value: "errada123" } });
    fireEvent.click(screen.getByRole("button", { name: "Entrar" }));

    expect(await screen.findByRole("alert")).toHaveTextContent("E-mail ou senha incorretos.");
    expect(screen.getByRole("button", { name: "Entrar" })).toBeEnabled();
  });

  it("login certo salva a sessão e vai para os buckets", async () => {
    fetchFalso(resposta(200, { accessToken: "tok", tokenType: "Bearer", expiresIn: 900 }));
    render(<FormLogin />);

    fireEvent.change(screen.getByLabelText("E-mail"), { target: { value: " a@a.com " } });
    fireEvent.change(screen.getByLabelText("Senha"), { target: { value: "12345678" } });
    fireEvent.click(screen.getByRole("button", { name: "Entrar" }));

    await waitFor(() => expect(navegacao.push).toHaveBeenCalledWith("/buckets"));
    expect(new Sessao().getEmail()).toBe("a@a.com"); // sem os espaços
  });

  it("avisa quando a sessão expirou", () => {
    render(<FormLogin sessaoExpirou />);
    expect(screen.getByRole("status")).toHaveTextContent("Sua sessão expirou");
  });
});

describe("PainelDetalhe", () => {
  const imagem: Arquivo = {
    id: "a1",
    bucketId: "b1",
    originalFilename: "foto.png",
    sizeBytes: 2048,
    contentType: "image/png",
    sha256: "9f2c1a7b0011223344556677e4d3c2",
    createdAt: "2026-10-22T10:00:00Z",
  };

  function montar(arquivo: Arquivo) {
    const props = {
      arquivo,
      nomeBucket: "fotos-obra",
      baixando: false,
      onBaixar: vi.fn(),
      onExcluir: vi.fn(),
      onFechar: vi.fn(),
      onErro: vi.fn(),
    };
    render(<PainelDetalhe {...props} />);
    return props;
  }

  it("mostra as informações e a prévia da imagem", async () => {
    logar();
    // Resposta falsa com o conteúdo da imagem (o jsdom não lê Blob dentro de Response)
    vi.stubGlobal("fetch", vi.fn().mockResolvedValue({ ok: true, status: 200, blob: async () => new Blob(["png"]) }));
    URL.createObjectURL = vi.fn(() => "blob:previa");
    URL.revokeObjectURL = vi.fn();
    montar(imagem);

    expect(screen.getByText("2,0 KB")).toBeInTheDocument();
    expect(screen.getByText("fotos-obra")).toBeInTheDocument();
    expect(screen.getByText("9f2c1a7b…e4d3c2")).toBeInTheDocument();
    expect(await screen.findByAltText("Prévia de foto.png")).toHaveAttribute("src", "blob:previa");
  });

  it("arquivo que não é imagem não baixa prévia", () => {
    const fetch = fetchFalso();
    montar({ ...imagem, originalFilename: "ata.pdf", contentType: "application/pdf" });

    expect(fetch).not.toHaveBeenCalled();
    expect(screen.queryByRole("img")).not.toBeInTheDocument();
  });

  it("imagem acima de 10 MB não baixa prévia", () => {
    const fetch = fetchFalso();
    montar({ ...imagem, sizeBytes: 11 * 1024 * 1024 });
    expect(fetch).not.toHaveBeenCalled();
  });

  it("falha na prévia avisa a tela", async () => {
    logar();
    vi.stubGlobal("fetch", vi.fn().mockRejectedValue(new TypeError("offline")));
    const props = montar(imagem);

    await waitFor(() => expect(props.onErro).toHaveBeenCalled());
  });

  it("botões Baixar, Excluir, fechar e tecla Esc", () => {
    fetchFalso();
    const props = montar({ ...imagem, contentType: "text/plain", originalFilename: "nota.txt" });

    fireEvent.click(screen.getByRole("button", { name: "Baixar" }));
    fireEvent.click(screen.getByRole("button", { name: "Excluir" }));
    fireEvent.click(screen.getByRole("button", { name: "Fechar detalhes" }));
    act(() => {
      fireEvent.keyDown(window, { key: "Escape" });
    });

    expect(props.onBaixar).toHaveBeenCalled();
    expect(props.onExcluir).toHaveBeenCalled();
    expect(props.onFechar).toHaveBeenCalledTimes(2);
  });
});
