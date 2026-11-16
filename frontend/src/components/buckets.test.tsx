import { fireEvent, render, screen } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import { Bucket, ResumoBucket } from "@/resources";
import { BucketCard } from "./BucketCard";
import { BucketCarrossel } from "./BucketCarrossel";

function resumo(dados: Partial<ResumoBucket>): ResumoBucket {
  return Object.assign(new ResumoBucket(), dados);
}

const acoes = () => ({ onExcluir: vi.fn(), onSoltarArquivos: vi.fn() });

describe("BucketCard", () => {
  it("mostra nome, data, os números Imagens · Docs · Outros e o link para o bucket", () => {
    render(
      <BucketCard
        id="b1"
        nome="fotos-obra"
        dataCriacao="15/10/2026"
        resumo={resumo({ total: 27, imagens: 25, docs: 1, outros: 1, tipoPrincipal: "imagem" })}
        {...acoes()}
      />
    );

    expect(screen.getByRole("link")).toHaveAttribute("href", "/buckets/b1");
    expect(screen.getByText("Criado em 15/10/2026")).toBeInTheDocument();
    expect(screen.getByTitle("Imagens")).toHaveTextContent("25");
    expect(screen.getByTitle("Documentos")).toHaveTextContent("1");
    expect(screen.getByTitle("Outros")).toHaveTextContent("1");
  });

  it("mostra — enquanto os números carregam", () => {
    render(<BucketCard id="b1" nome="docs" dataCriacao="01/01/2026" {...acoes()} />);
    expect(screen.getByTitle("Imagens")).toHaveTextContent("—");
  });

  it("Excluir chama a ação certa", () => {
    const a = acoes();
    render(<BucketCard id="b1" nome="docs" dataCriacao="01/01/2026" {...a} />);

    fireEvent.click(screen.getByRole("button", { name: "Excluir o bucket docs" }));

    expect(a.onExcluir).toHaveBeenCalled();
  });

  it("bucket sendo apagado fica sem link e mostra Excluindo…", () => {
    render(<BucketCard id="b1" nome="backups" dataCriacao="01/01/2026" excluindo {...acoes()} />);

    expect(screen.getByText("Excluindo…")).toBeInTheDocument();
    expect(screen.queryByRole("link")).not.toBeInTheDocument();
    expect(screen.queryByRole("button")).not.toBeInTheDocument();
  });

  it("soltar arquivos em cima envia para o bucket", () => {
    const a = acoes();
    const arquivo = new File(["x"], "nota.txt");
    const { container } = render(<BucketCard id="b1" nome="docs" dataCriacao="01/01/2026" {...a} />);
    const cartao = container.firstElementChild as HTMLElement;

    fireEvent.dragOver(cartao, { dataTransfer: { types: ["Files"], files: [] } });
    expect(screen.getByText("Solte para enviar a docs")).toBeInTheDocument();

    fireEvent.drop(cartao, { dataTransfer: { types: ["Files"], files: [arquivo] } });
    expect(a.onSoltarArquivos).toHaveBeenCalledWith([arquivo]);
  });

  it("durante o envio mostra Enviando… e não aceita outro arquivo", () => {
    const a = acoes();
    const { container } = render(<BucketCard id="b1" nome="docs" dataCriacao="01/01/2026" enviando {...a} />);

    expect(screen.getByText("Enviando…")).toBeInTheDocument();
    fireEvent.drop(container.firstElementChild as HTMLElement, {
      dataTransfer: { types: ["Files"], files: [new File(["x"], "a.txt")] },
    });
    expect(a.onSoltarArquivos).not.toHaveBeenCalled();
  });

  it("no modo lista mostra as mesmas informações", () => {
    render(<BucketCard id="b1" nome="docs" dataCriacao="01/01/2026" modo="lista" ativo {...acoes()} />);
    expect(screen.getByRole("link")).toHaveTextContent("docs");
  });
});

describe("BucketCarrossel", () => {
  const buckets: Bucket[] = ["documentos", "fotos-obra", "contratos", "backups"].map((nome, i) => ({
    id: `b${i}`,
    name: nome,
    status: "ACTIVE",
    createdAt: "2026-10-01T10:00:00Z",
  }));

  function montar(indiceAtivo = 1) {
    const props = {
      buckets,
      resumos: {},
      indiceAtivo,
      enviandoId: "",
      onMudar: vi.fn(),
      onAbrir: vi.fn(),
      onExcluir: vi.fn(),
      onSoltarArquivos: vi.fn(),
    };
    render(<BucketCarrossel {...props} />);
    return props;
  }

  it("só o cartão do centro tem link; os dos lados trazem para o centro", () => {
    const props = montar(1);

    // Os cartões longe do centro ficam escondidos de leitores de tela
    expect(screen.getAllByRole("link")).toHaveLength(3);
    // Só o do centro recebe foco pelo teclado
    const focaveis = screen.getAllByRole("link").filter((link) => link.getAttribute("tabindex") !== "-1");
    expect(focaveis).toHaveLength(1);
    expect(focaveis[0]).toHaveAttribute("href", "/buckets/b1");
    fireEvent.click(screen.getByRole("button", { name: "Mostrar contratos" }));
    expect(props.onMudar).toHaveBeenCalledWith(2);
    expect(screen.queryByRole("button", { name: "Mostrar fotos-obra" })).not.toBeInTheDocument();
  });

  it("setas da tela e do teclado andam um bucket", () => {
    const props = montar(1);

    fireEvent.click(screen.getByRole("button", { name: "Próximo bucket" }));
    fireEvent.click(screen.getByRole("button", { name: "Bucket anterior" }));
    const regiao = screen.getByRole("region");
    fireEvent.keyDown(regiao, { key: "ArrowRight" });
    fireEvent.keyDown(regiao, { key: "ArrowLeft" });
    fireEvent.keyDown(regiao, { key: "End" });
    fireEvent.keyDown(regiao, { key: "Home" });

    expect(props.onMudar.mock.calls.map((c) => c[0])).toEqual([2, 0, 2, 0, 3, 0]);
  });

  it("não passa do primeiro nem do último", () => {
    const props = montar(0);

    fireEvent.keyDown(screen.getByRole("region"), { key: "ArrowLeft" });

    expect(props.onMudar).not.toHaveBeenCalled();
    expect(screen.getByRole("button", { name: "Bucket anterior" })).toBeDisabled();
  });

  it("bolinhas levam direto ao bucket", () => {
    const props = montar(0);

    fireEvent.click(screen.getByRole("button", { name: "Ir para backups" }));

    expect(props.onMudar).toHaveBeenCalledWith(3);
    expect(screen.getByRole("button", { name: "Ir para documentos" })).toHaveAttribute("aria-current", "true");
  });

  it("arrastar para o lado troca de bucket e não abre o do centro", () => {
    const props = montar(1);
    const regiao = screen.getByRole("region");

    fireEvent.pointerDown(regiao, { clientX: 500 });
    fireEvent.pointerUp(regiao, { clientX: 300 });
    fireEvent.click(screen.getAllByRole("link")[1]);

    expect(props.onMudar).toHaveBeenCalledWith(2);
    expect(props.onAbrir).not.toHaveBeenCalled();
  });

  it("toque curto não conta como arraste", () => {
    const props = montar(1);
    const regiao = screen.getByRole("region");

    fireEvent.pointerDown(regiao, { clientX: 500 });
    fireEvent.pointerUp(regiao, { clientX: 490 });

    expect(props.onMudar).not.toHaveBeenCalled();
  });
});
