import { act, fireEvent, render, screen } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import { AreaUpload } from "./AreaUpload";
import { ArquivoCard } from "./ArquivoCard";
import { Aviso } from "./Aviso";
import { Botao } from "./Botao";
import { Campo } from "./Campo";
import { Carregando, Mensagem } from "./Estado";
import { Icone } from "./Icone";
import { Modal } from "./Modal";

describe("Botao", () => {
  it("chama o onClick e respeita o desabilitado", () => {
    const clique = vi.fn();
    const { rerender } = render(<Botao onClick={clique}>Salvar</Botao>);

    fireEvent.click(screen.getByRole("button", { name: "Salvar" }));
    expect(clique).toHaveBeenCalledTimes(1);

    rerender(
      <Botao onClick={clique} desabilitado>
        Salvar
      </Botao>
    );
    expect(screen.getByRole("button", { name: "Salvar" })).toBeDisabled();
  });

  it("botão só com ícone usa o rótulo para leitores de tela", () => {
    render(<Botao icone="lixeira" variante="perigo" rotulo="Excluir" />);
    expect(screen.getByRole("button", { name: "Excluir" })).toBeInTheDocument();
  });
});

describe("Campo", () => {
  it("avisa cada letra digitada e liga o rótulo ao campo", () => {
    const mudou = vi.fn();
    render(<Campo id="email" rotulo="E-mail" valor="" onChange={mudou} />);

    fireEvent.change(screen.getByLabelText("E-mail"), { target: { value: "a@a.com" } });

    expect(mudou).toHaveBeenCalledWith("a@a.com");
  });

  it("mostra o erro no lugar da dica e marca o campo como inválido", () => {
    const { rerender } = render(<Campo id="s" rotulo="Senha" valor="" onChange={() => {}} dica="Mínimo de 8" />);
    expect(screen.getByText("Mínimo de 8")).toBeInTheDocument();

    rerender(<Campo id="s" rotulo="Senha" valor="" onChange={() => {}} dica="Mínimo de 8" erro="Obrigatório" />);
    expect(screen.getByText("Obrigatório")).toBeInTheDocument();
    expect(screen.queryByText("Mínimo de 8")).not.toBeInTheDocument();
    expect(screen.getByLabelText("Senha")).toHaveAttribute("aria-invalid", "true");
  });
});

describe("Modal", () => {
  it("não aparece fechado", () => {
    render(
      <Modal titulo="Novo bucket" aberto={false} onFechar={() => {}}>
        conteúdo
      </Modal>
    );
    expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
  });

  it("fecha com Esc, com o X e clicando fora, mas não clicando dentro", () => {
    const fechar = vi.fn();
    render(
      <Modal titulo="Novo bucket" aberto onFechar={fechar}>
        <p>conteúdo</p>
      </Modal>
    );
    const janela = screen.getByRole("dialog", { name: "Novo bucket" });

    fireEvent.click(screen.getByText("conteúdo"));
    expect(fechar).not.toHaveBeenCalled();

    fireEvent.keyDown(window, { key: "Escape" });
    fireEvent.click(screen.getByRole("button", { name: "Fechar" }));
    fireEvent.click(janela.parentElement as HTMLElement);
    expect(fechar).toHaveBeenCalledTimes(3);
  });
});

describe("Aviso", () => {
  it("mostra a mensagem e a ação Desfazer", () => {
    const desfazer = vi.fn();
    render(<Aviso mensagem="Bucket excluído" acao="Desfazer" onAcao={desfazer} />);

    expect(screen.getByRole("status")).toHaveTextContent("Bucket excluído");
    fireEvent.click(screen.getByRole("button", { name: "Desfazer" }));
    expect(desfazer).toHaveBeenCalled();
  });

  it("sem ação, não mostra botão", () => {
    render(<Aviso mensagem="Arquivo enviado" tipo="sucesso" />);
    expect(screen.queryByRole("button")).not.toBeInTheDocument();
  });
});

describe("Estados (carregando, vazio, erro)", () => {
  it("Carregando e Mensagem com botão", () => {
    const tentar = vi.fn();
    render(
      <>
        <Carregando texto="Buscando…" />
        <Mensagem icone="nuvem" erro texto="Sem conexão" botao="Tentar de novo" onBotao={tentar} />
      </>
    );
    expect(screen.getByText("Buscando…")).toBeInTheDocument();
    fireEvent.click(screen.getByRole("button", { name: "Tentar de novo" }));
    expect(tentar).toHaveBeenCalled();
  });
});

describe("Icone", () => {
  it("é decorativo (escondido de leitores de tela)", () => {
    const { container } = render(<Icone nome="pasta" tamanho={30} />);
    const svg = container.querySelector("svg");
    expect(svg).toHaveAttribute("aria-hidden", "true");
    expect(svg).toHaveAttribute("width", "30");
  });
});

describe("ArquivoCard", () => {
  it("mostra nome, tamanho e data, e separa abrir de excluir", () => {
    const abrir = vi.fn();
    const excluir = vi.fn();
    render(
      <ArquivoCard nome="foto.png" tamanho="1,6 MB" dataUpload="22/10/2026" tipo="imagem" onAbrir={abrir} onExcluir={excluir} />
    );

    expect(screen.getByText("1,6 MB · 22/10/2026")).toBeInTheDocument();
    fireEvent.click(screen.getByText("foto.png"));
    fireEvent.click(screen.getByRole("button", { name: "Excluir o arquivo foto.png" }));
    expect(abrir).toHaveBeenCalledTimes(1);
    expect(excluir).toHaveBeenCalledTimes(1);
  });
});

describe("AreaUpload", () => {
  it("envia os arquivos escolhidos no seletor", () => {
    const recebeu = vi.fn();
    const { container } = render(<AreaUpload enviando={false} onArquivos={recebeu} />);
    const arquivo = new File(["x"], "nota.txt");

    fireEvent.change(container.querySelector("input[type=file]") as HTMLInputElement, { target: { files: [arquivo] } });

    expect(recebeu).toHaveBeenCalledWith([arquivo]);
  });

  it("aceita arquivos soltos na área e ignora durante o envio", () => {
    const recebeu = vi.fn();
    const arquivo = new File(["x"], "nota.txt");
    const { container, rerender } = render(<AreaUpload enviando={false} onArquivos={recebeu} />);
    const area = container.firstElementChild as HTMLElement;

    fireEvent.dragOver(area);
    fireEvent.drop(area, { dataTransfer: { files: [arquivo] } });
    expect(recebeu).toHaveBeenCalledTimes(1);

    rerender(<AreaUpload enviando onArquivos={recebeu} />);
    fireEvent.drop(area, { dataTransfer: { files: [arquivo] } });
    expect(recebeu).toHaveBeenCalledTimes(1);
    expect(screen.getByRole("progressbar")).toBeInTheDocument();
  });
});

describe("Modal com timers", () => {
  it("remove o ouvinte de teclado ao fechar", () => {
    const fechar = vi.fn();
    const { rerender } = render(
      <Modal titulo="X" aberto onFechar={fechar}>
        a
      </Modal>
    );
    rerender(
      <Modal titulo="X" aberto={false} onFechar={fechar}>
        a
      </Modal>
    );
    act(() => {
      fireEvent.keyDown(window, { key: "Escape" });
    });
    expect(fechar).not.toHaveBeenCalled();
  });
});
