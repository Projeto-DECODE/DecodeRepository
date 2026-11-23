"use client";

import { useRef, useState } from "react";
import { Icone } from "./Icone";

interface AreaUploadProps {
  enviando: boolean;
  onArquivos: (arquivos: File[]) => void;
}

// Área de "arrastar e soltar". Também abre o seletor de arquivos ao clicar.
export const AreaUpload: React.FC<AreaUploadProps> = ({ enviando, onArquivos }: AreaUploadProps) => {
  const inputRef = useRef<HTMLInputElement>(null);
  const [arrastando, setArrastando] = useState<boolean>(false);

  function aoSoltar(event: React.DragEvent<HTMLDivElement>) {
    event.preventDefault();
    setArrastando(false);
    if (enviando) return;
    const arquivos = Array.from(event.dataTransfer.files);
    if (arquivos.length > 0) onArquivos(arquivos);
  }

  function aoEscolher(event: React.ChangeEvent<HTMLInputElement>) {
    const arquivos = Array.from(event.target.files ?? []);
    if (arquivos.length > 0) onArquivos(arquivos);
    event.target.value = ""; // permite escolher o mesmo arquivo de novo
  }

  return (
    <div
      onDragOver={(event) => {
        event.preventDefault();
        setArrastando(true);
      }}
      onDragLeave={() => setArrastando(false)}
      onDrop={aoSoltar}
      className={`group relative overflow-hidden rounded-xl border-2 border-dashed border-acento transition-all duration-300
        ${arrastando ? "bg-acento/10 shadow-brilho" : "bg-cartao/40 hover:bg-acento/5 hover:shadow-brilho"}`}
    >
      <button
        type="button"
        disabled={enviando}
        onClick={() => inputRef.current?.click()}
        className="flex w-full flex-col items-center gap-3 px-6 py-10 text-center disabled:cursor-wait"
      >
        <span className="flex h-14 w-14 items-center justify-center rounded-xl bg-acento/15 text-acento transition-transform duration-300 group-hover:-translate-y-0.5">
          <Icone nome="upload" tamanho={28} />
        </span>
        <span className="text-lg font-medium">
          {enviando ? "Enviando arquivo…" : "Arraste arquivos aqui ou clique para escolher"}
        </span>
        <span className="text-sm text-texto-secundario">Até 10 MB por arquivo</span>
      </button>
      <input ref={inputRef} type="file" multiple hidden onChange={aoEscolher} />

      {enviando && (
        <div className="absolute inset-x-0 bottom-0 h-1 overflow-hidden bg-cartao" role="progressbar" aria-label="Enviando">
          <div className="progresso-indeterminado h-full w-1/3 bg-acento" />
        </div>
      )}
    </div>
  );
};
