import { describe, expect, it } from "vitest";
import {
  abreviarSha,
  categoriaDoArquivo,
  formatarData,
  formatarTamanho,
  REGEX_NOME_BUCKET,
  tipoDoArquivo,
} from "./formatos";

describe("formatarTamanho", () => {
  it("mostra bytes, KB e MB com vírgula", () => {
    expect(formatarTamanho(512)).toBe("512 B");
    expect(formatarTamanho(1536)).toBe("1,5 KB");
    expect(formatarTamanho(3 * 1024 * 1024)).toBe("3,0 MB");
  });

  it("trata o valor ausente como zero", () => {
    expect(formatarTamanho()).toBe("0 B");
  });
});

describe("formatarData", () => {
  it("converte ISO para dd/mm/aaaa", () => {
    expect(formatarData("2026-10-18T15:00:00Z")).toBe("18/10/2026");
  });

  it("devolve o texto original quando não é uma data", () => {
    expect(formatarData("ontem")).toBe("ontem");
  });
});

describe("abreviarSha", () => {
  it("mostra 8 primeiros e 6 últimos caracteres", () => {
    expect(abreviarSha("9f2c1a7b0011223344556677e4d3c2")).toBe("9f2c1a7b…e4d3c2");
  });

  it("mantém textos curtos e trata vazio", () => {
    expect(abreviarSha("abc")).toBe("abc");
    expect(abreviarSha("")).toBe("—");
  });
});

describe("tipoDoArquivo", () => {
  it("reconhece imagem, PDF, planilha e outros", () => {
    expect(tipoDoArquivo("image/png", "a.png")).toBe("imagem");
    expect(tipoDoArquivo("application/pdf", "a.pdf")).toBe("pdf");
    expect(tipoDoArquivo("application/octet-stream", "contrato.PDF")).toBe("pdf");
    expect(tipoDoArquivo("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", "a.xlsx")).toBe("planilha");
    expect(tipoDoArquivo("", "dados.csv")).toBe("planilha");
    expect(tipoDoArquivo("video/mp4", "aula.mp4")).toBe("outro");
  });
});

describe("categoriaDoArquivo", () => {
  it("agrupa em Imagens, Docs e Outros", () => {
    expect(categoriaDoArquivo("image/jpeg", "foto.jpg")).toBe("imagem");
    expect(categoriaDoArquivo("application/pdf", "a.pdf")).toBe("doc");
    expect(categoriaDoArquivo("", "planilha.xlsx")).toBe("doc");
    expect(categoriaDoArquivo("text/plain", "nota.txt")).toBe("doc");
    expect(categoriaDoArquivo("application/msword", "carta.doc")).toBe("doc");
    expect(categoriaDoArquivo("", "apresentacao.pptx")).toBe("doc");
    expect(categoriaDoArquivo("video/mp4", "aula.mp4")).toBe("outro");
  });
});

describe("REGEX_NOME_BUCKET (mesma regra do back-end)", () => {
  it("aceita minúsculas, números e hífen no meio", () => {
    expect(REGEX_NOME_BUCKET.test("fotos-obra")).toBe(true);
    expect(REGEX_NOME_BUCKET.test("relatorios2026")).toBe(true);
  });

  it("recusa maiúsculas, espaços e hífen nas pontas", () => {
    expect(REGEX_NOME_BUCKET.test("Fotos")).toBe(false);
    expect(REGEX_NOME_BUCKET.test("fotos obra")).toBe(false);
    expect(REGEX_NOME_BUCKET.test("-fotos")).toBe(false);
    expect(REGEX_NOME_BUCKET.test("fotos-")).toBe(false);
  });
});
