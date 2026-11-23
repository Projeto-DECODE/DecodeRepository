"use client";

import { useEffect, useState } from "react";
import { Arquivo, LIMITE_PREVIA_BYTES, useArquivoService } from "@/resources";
import { abreviarSha, formatarData, formatarTamanho, tipoDoArquivo } from "@/utils/formatos";
import { Botao } from "./Botao";
import { COR_POR_TIPO, ICONE_POR_TIPO } from "./ArquivoCard";
import { Icone, NomeIcone } from "./Icone";

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
  const [copiou, setCopiou] = useState<boolean>(false);

  // Copia o SHA-256 completo (serve para conferir se o arquivo é o mesmo)
  function copiarSha() {
    if (!arquivo.sha256 || !navigator.clipboard) return;
    navigator.clipboard.writeText(arquivo.sha256).then(() => {
      setCopiou(true);
      setTimeout(() => setCopiou(false), 1500);
    });
  }

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

  const renderLinha = (icone: NomeIcone, rotulo: string, valor: string, extra?: React.ReactNode) => (
    <div className="flex items-center gap-3 border-b border-borda py-3.5 text-sm">
      <Icone nome={icone} tamanho={20} className="shrink-0 text-texto-secundario" />
      <dt className="text-texto-secundario">{rotulo}</dt>
      <dd className="ml-auto flex min-w-0 items-center gap-2 text-right">
        <span className="truncate" title={valor}>
          {valor}
        </span>
        {extra}
      </dd>
    </div>
  );

  return (
    <>
      <div className="fixed inset-0 z-30 bg-black/50 backdrop-blur-sm" onClick={onFechar} aria-hidden="true" />
      <aside
        role="dialog"
        aria-modal="true"
        aria-labelledby="detalhe-titulo"
        className="painel-entrada fixed inset-y-0 right-0 z-40 flex w-full flex-col overflow-y-auto border-l border-borda bg-fundo shadow-2xl md:w-[440px]"
      >
        <div className="flex items-center justify-between px-6 py-5">
          <h2 className="text-xl font-semibold">Detalhes do arquivo</h2>
          <button
            type="button"
            onClick={onFechar}
            aria-label="Fechar detalhes"
            className="flex h-11 w-11 items-center justify-center rounded-md text-texto-secundario hover:bg-white/5 hover:text-texto"
          >
            <Icone nome="fechar" />
          </button>
        </div>

        <div className="flex flex-col gap-6 px-6 pb-6">
          <div className="flex h-64 items-center justify-center overflow-hidden rounded-xl border border-borda bg-cartao">
            {urlPrevia ? (
              // eslint-disable-next-line @next/next/no-img-element
              <img src={urlPrevia} alt={`Prévia de ${arquivo.originalFilename}`} className="h-full w-full object-contain" />
            ) : carregandoPrevia ? (
              <div className="h-1 w-32 overflow-hidden rounded-full bg-cartao" role="status" aria-label="Carregando prévia">
                <div className="progresso-indeterminado h-full w-1/3 bg-acento" />
              </div>
            ) : (
              <span className={`flex h-28 w-28 items-center justify-center rounded-xl ${COR_POR_TIPO[tipo]}`}>
                <Icone nome={ICONE_POR_TIPO[tipo]} tamanho={56} />
              </span>
            )}
          </div>

          <h3 id="detalhe-titulo" className="break-all text-xl font-semibold">
            {arquivo.originalFilename}
          </h3>

          <dl>
            {renderLinha("play", "Tipo", arquivo.contentType || "—")}
            {renderLinha("banco", "Tamanho", formatarTamanho(arquivo.sizeBytes))}
            {renderLinha("calendario", "Enviado em", formatarData(arquivo.createdAt))}
            {renderLinha("pasta", "Bucket", nomeBucket)}
            {renderLinha(
              "escudo",
              "SHA-256",
              abreviarSha(arquivo.sha256),
              arquivo.sha256 && (
                <button
                  type="button"
                  onClick={copiarSha}
                  aria-label={copiou ? "SHA-256 copiado" : "Copiar SHA-256"}
                  title={copiou ? "Copiado!" : "Copiar"}
                  className={`flex h-8 w-8 shrink-0 items-center justify-center rounded-md transition-colors hover:bg-white/5 ${copiou ? "text-acento" : "text-texto-secundario hover:text-texto"}`}
                >
                  <Icone nome={copiou ? "check" : "copiar"} tamanho={18} />
                </button>
              )
            )}
          </dl>

          <div className="mt-2 flex flex-col gap-3">
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
