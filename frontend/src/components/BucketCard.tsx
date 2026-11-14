"use client";

import Link from "next/link";
import { useState } from "react";
import { ResumoBucket } from "@/resources";
import { COR_POR_TIPO, ICONE_POR_TIPO } from "./ArquivoCard";
import { Icone } from "./Icone";

export type ModoBucket = "rotativo" | "grade" | "lista";

interface BucketCardProps {
  id: string;
  nome: string;
  dataCriacao: string;
  resumo?: ResumoBucket; // undefined enquanto carrega
  modo?: ModoBucket;
  ativo?: boolean; // em destaque (centro do rotativo, último aberto ou foco do teclado)
  excluindo?: boolean; // status DELETING no servidor
  enviando?: boolean; // arquivos sendo enviados para este bucket
  ordem?: number; // posição, para a animação de entrada
  onAbrir?: () => void;
  onFocar?: () => void;
  onExcluir: () => void;
  onSoltarArquivos: (arquivos: File[]) => void;
}

// Ícone grande do cartão: o tipo de arquivo que mais aparece no bucket (vazio = pasta)
const IconeDoBucket: React.FC<{ resumo?: ResumoBucket; tamanho: number; caixa: string }> = ({ resumo, tamanho, caixa }) => {
  const tipo = resumo?.tipoPrincipal;
  return (
    <span
      className={`flex shrink-0 items-center justify-center rounded-xl transition-transform duration-300 group-hover:scale-110 ${caixa}
        ${tipo ? COR_POR_TIPO[tipo] : "bg-acento/10 text-acento"}`}
    >
      <Icone nome={tipo ? ICONE_POR_TIPO[tipo] : "pasta"} tamanho={tamanho} />
    </span>
  );
};

// Os 3 números: Imagens · Docs · Outros
const Numeros: React.FC<{ resumo?: ResumoBucket; grande?: boolean }> = ({ resumo, grande = false }) => {
  const valor = (n?: number) => (resumo ? String(n) : "—");
  const item = (icone: "imagem" | "pdf" | "arquivo", numero: string, rotulo: string) => (
    <span className="flex items-center gap-1.5" title={rotulo}>
      <Icone nome={icone} tamanho={grande ? 18 : 15} />
      <span className="sr-only">{rotulo}:</span>
      {numero}
    </span>
  );
  return (
    <span className={`flex items-center gap-5 text-texto-secundario ${grande ? "text-base" : "text-sm"}`}>
      {item("imagem", valor(resumo?.imagens), "Imagens")}
      {item("pdf", valor(resumo?.docs), "Documentos")}
      {item("arquivo", valor(resumo?.outros), "Outros")}
    </span>
  );
};

export const BucketCard: React.FC<BucketCardProps> = ({
  id,
  nome,
  dataCriacao,
  resumo,
  modo = "grade",
  ativo = false,
  excluindo = false,
  enviando = false,
  ordem = 0,
  onAbrir,
  onFocar,
  onExcluir,
  onSoltarArquivos,
}: BucketCardProps) => {
  const [arrastando, setArrastando] = useState<boolean>(false);
  const lista = modo === "lista";
  const rotativo = modo === "rotativo";

  // Soltar arquivos em cima do cartão envia direto para este bucket
  function aoSoltar(event: React.DragEvent<HTMLDivElement>) {
    event.preventDefault();
    setArrastando(false);
    const arquivos = Array.from(event.dataTransfer.files);
    if (arquivos.length > 0 && !enviando) onSoltarArquivos(arquivos);
  }

  const vidro = "border bg-cartao/70 backdrop-blur-md";
  const borda = arrastando
    ? "border-2 border-dashed border-acento"
    : ativo
      ? "border-2 border-acento shadow-brilho"
      : "border-white/10 hover:border-acento/60";

  // Bucket sendo apagado no servidor: esmaecido e sem clique
  if (excluindo) {
    return (
      <div
        className={`${vidro} flex rounded-2xl border-white/10 p-5 opacity-40 ${lista ? "items-center gap-4" : "h-full flex-col justify-center gap-3"}`}
        aria-disabled="true"
      >
        <Icone nome="pasta" tamanho={28} className="text-texto-secundario" />
        <div>
          <h3 className="font-semibold">{nome}</h3>
          <p className="text-sm font-semibold text-acento">Excluindo…</p>
        </div>
      </div>
    );
  }

  return (
    <div
      onDragOver={(event) => {
        if (!event.dataTransfer.types.includes("Files")) return;
        event.preventDefault();
        setArrastando(true);
      }}
      onDragLeave={() => setArrastando(false)}
      onDrop={aoSoltar}
      style={rotativo ? undefined : { animationDelay: `${Math.min(ordem, 12) * 40}ms` }}
      className={`group relative h-full overflow-hidden rounded-2xl transition-all duration-300 ease-out ${vidro} ${borda}
        ${rotativo ? "" : "surgir"} ${!rotativo && !ativo && !arrastando ? "hover:-translate-y-1" : ""}`}
    >
      {/* Luz suave no canto, como no vidro da referência */}
      <span
        aria-hidden="true"
        className="pointer-events-none absolute -left-16 -top-16 h-40 w-40 rounded-full bg-white/5 blur-2xl"
      />

      <Link
        href={`/buckets/${id}`}
        onClick={onAbrir}
        onFocus={onFocar}
        tabIndex={rotativo && !ativo ? -1 : undefined} // no rotativo, só o cartão do centro recebe foco
        data-cartao-bucket
        className={`relative flex h-full focus-visible:outline-none
          ${lista ? "items-center gap-4 p-4 pr-28" : "flex-col items-center p-6 pb-12 text-center"}
          ${rotativo ? "justify-center gap-5" : lista ? "" : "gap-4"}`}
      >
        <IconeDoBucket
          resumo={resumo}
          tamanho={lista ? 24 : rotativo && ativo ? 56 : 40}
          caixa={lista ? "h-12 w-12" : rotativo && ativo ? "h-24 w-24" : "h-18 w-18"}
        />

        {lista ? (
          <div className="flex min-w-0 flex-1 flex-wrap items-center gap-x-8 gap-y-1">
            <h3 className="min-w-32 truncate font-semibold">{nome}</h3>
            <p className="text-sm text-texto-secundario">Criado em {dataCriacao}</p>
            <Numeros resumo={resumo} />
          </div>
        ) : (
          <div className="flex w-full min-w-0 flex-col items-center">
            <h3 className={`w-full truncate font-semibold ${rotativo && ativo ? "text-2xl" : "text-lg"}`}>{nome}</h3>
            <p className="mt-1 text-xs text-texto-secundario">Criado em {dataCriacao}</p>
            <div className="mt-4 w-full border-t border-white/10 pt-4">
              <span className="flex justify-center">
                <Numeros resumo={resumo} grande={rotativo && ativo} />
              </span>
            </div>
          </div>
        )}
      </Link>

      <button
        type="button"
        onClick={onExcluir}
        aria-label={`Excluir o bucket ${nome}`}
        tabIndex={rotativo && !ativo ? -1 : 0}
        className={`absolute flex min-h-11 items-center px-3 text-xs text-erro opacity-70 transition-opacity hover:opacity-100 hover:underline focus-visible:opacity-100
          ${lista ? "right-3 top-1/2 -translate-y-1/2" : "bottom-1 left-1/2 -translate-x-1/2"}`}
      >
        Excluir
      </button>

      {/* Camada que aparece ao arrastar arquivos ou durante o envio */}
      {(arrastando || enviando) && (
        <div className="pointer-events-none absolute inset-0 flex flex-col items-center justify-center gap-2 bg-fundo/85 text-sm text-acento">
          <Icone nome="upload" tamanho={26} />
          {enviando ? "Enviando…" : `Solte para enviar a ${nome}`}
          {enviando && (
            <div className="h-1 w-24 overflow-hidden rounded-full bg-cartao">
              <div className="progresso-indeterminado h-full w-1/3 bg-acento" />
            </div>
          )}
        </div>
      )}
    </div>
  );
};
