"use client";

import { useEffect, useState } from "react";
import { Arquivo, LIMITE_PREVIA_BYTES, useArquivoService } from "@/resources";
import { abreviarSha, formatarData, formatarTamanho, tipoDoArquivo } from "@/utils/formatos";
import { Botao } from "./Botao";
import { COR_POR_TIPO, ICONE_POR_TIPO } from "./ArquivoCard";
import { Icone } from "./Icone";

interface PainelDetalheProps {
  arquivo: Arquivo;
  nomeBucket: string;
  baixando: boolean;
  onBaixar: () => void;
  onExcluir: () => void;
  onFechar: () => void;
  onErro: (erro: unknown) => void;
}

// Painel que entra pela direita (no celular, ocupa a tela toda).
// Use com key={arquivo.id} para recomeçar a cada arquivo.
export const PainelDetalhe: React.FC<PainelDetalheProps> = ({
  arquivo,
  nomeBucket,
  baixando,
  onBaixar,
  onExcluir,
  onFechar,
  onErro,
}: PainelDetalheProps) => {
  const service = useArquivoService();
  const tipo = tipoDoArquivo(arquivo.contentType, arquivo.originalFilename);
  const temPrevia = tipo === "imagem" && (arquivo.sizeBytes ?? 0) <= LIMITE_PREVIA_BYTES;
  const [urlPrevia, setUrlPrevia] = useState<string>("");
  const [falhouPrevia, setFalhouPrevia] = useState<boolean>(false);

  // Baixa a imagem com o token e mostra a prévia
  useEffect(() => {
    if (!temPrevia) return;
    let url = "";
    let cancelado = false;
    service
      .baixar(arquivo.bucketId ?? "", arquivo.id ?? "")
      .then((blob) => {
        if (cancelado) return;
        url = URL.createObjectURL(blob);
        setUrlPrevia(url);
      })
      .catch((erro) => {
        if (cancelado) return;
        setFalhouPrevia(true);
        onErro(erro);
      });
    return () => {
      cancelado = true;
      if (url) URL.revokeObjectURL(url);
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [arquivo.id]);

  // Fecha com a tecla Esc
  useEffect(() => {
    const aoTeclar = (event: KeyboardEvent) => {
      if (event.key === "Escape") onFechar();
    };
    window.addEventListener("keydown", aoTeclar);
    return () => window.removeEventListener("keydown", aoTeclar);
  }, [onFechar]);

  const carregandoPrevia = temPrevia && !urlPrevia && !falhouPrevia;

  const renderLinha = (rotulo: string, valor: string, mono = false) => (
    <div className="flex justify-between gap-4 border-b border-borda py-3 text-sm">
      <dt className="text-texto-secundario">{rotulo}</dt>
      <dd className={`text-right break-all ${mono ? "font-mono" : ""}`}>{valor}</dd>
    </div>
  );

  return (
    <>
      <div className="fixed inset-0 z-30 bg-black/50 backdrop-blur-sm" onClick={onFechar} aria-hidden="true" />
      <aside
        role="dialog"
        aria-modal="true"
        aria-labelledby="detalhe-titulo"
        className="painel-entrada fixed inset-y-0 right-0 z-40 flex w-full flex-col overflow-y-auto border-l border-white/10 bg-cartao shadow-2xl md:w-[440px]"
      >
        <div className="flex items-center justify-between border-b border-borda px-5 py-4">
          <h2 className="text-lg font-semibold">Detalhes do arquivo</h2>
          <button
            type="button"
            onClick={onFechar}
            aria-label="Fechar detalhes"
            className="flex h-11 w-11 items-center justify-center rounded-md text-texto-secundario hover:bg-white/5 hover:text-texto"
          >
            <Icone nome="fechar" />
          </button>
        </div>

        <div className="flex flex-col gap-6 p-5">
          <div className="flex h-64 items-center justify-center overflow-hidden rounded-lg border border-white/5 bg-fundo">
            {urlPrevia ? (
              // eslint-disable-next-line @next/next/no-img-element
              <img src={urlPrevia} alt={`Prévia de ${arquivo.originalFilename}`} className="h-full w-full object-contain" />
            ) : carregandoPrevia ? (
              <div className="h-1 w-32 overflow-hidden rounded-full bg-cartao" role="status" aria-label="Carregando prévia">
                <div className="progresso-indeterminado h-full w-1/3 bg-acento" />
              </div>
            ) : (
              <span className={`flex h-28 w-28 items-center justify-center rounded-lg ${COR_POR_TIPO[tipo]}`}>
                <Icone nome={ICONE_POR_TIPO[tipo]} tamanho={56} />
              </span>
            )}
          </div>

          <h3 id="detalhe-titulo" className="break-all text-xl font-semibold">
            {arquivo.originalFilename}
          </h3>

          <dl>
            {renderLinha("Tipo", arquivo.contentType || "—")}
            {renderLinha("Tamanho", formatarTamanho(arquivo.sizeBytes))}
            {renderLinha("Enviado em", formatarData(arquivo.createdAt))}
            {renderLinha("Bucket", nomeBucket)}
            {renderLinha("SHA-256", abreviarSha(arquivo.sha256), true)}
          </dl>

          <div className="flex flex-col gap-3">
            <Botao icone="download" larguraTotal desabilitado={baixando} onClick={onBaixar}>
              {baixando ? "Baixando…" : "Baixar"}
            </Botao>
            <Botao icone="lixeira" variante="perigo" larguraTotal onClick={onExcluir}>
              Excluir
            </Botao>
          </div>
        </div>
      </aside>
    </>
  );
};
