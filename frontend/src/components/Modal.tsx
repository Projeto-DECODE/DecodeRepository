"use client";

import { useEffect } from "react";
import { Icone } from "./Icone";

interface ModalProps {
  titulo: string;
  aberto: boolean;
  onFechar: () => void;
  children: React.ReactNode;
}

// Janela centralizada sobre a tela. Fecha com Esc, com o X ou clicando fora.
export const Modal: React.FC<ModalProps> = ({ titulo, aberto, onFechar, children }: ModalProps) => {
  useEffect(() => {
    if (!aberto) return;
    const aoTeclar = (event: KeyboardEvent) => {
      if (event.key === "Escape") onFechar();
    };
    window.addEventListener("keydown", aoTeclar);
    return () => window.removeEventListener("keydown", aoTeclar);
  }, [aberto, onFechar]);

  if (!aberto) return null;

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/60 px-4 backdrop-blur-sm" onClick={onFechar}>
      <div
        role="dialog"
        aria-modal="true"
        aria-labelledby="modal-titulo"
        className="surgir w-full max-w-md rounded-lg border border-white/10 bg-cartao/95 p-6 shadow-2xl"
        onClick={(event) => event.stopPropagation()}
      >
        <div className="mb-5 flex items-center justify-between">
          <h2 id="modal-titulo" className="text-2xl font-semibold">
            {titulo}
          </h2>
          <button
            type="button"
            onClick={onFechar}
            aria-label="Fechar"
            className="flex h-10 w-10 items-center justify-center text-texto-secundario hover:text-texto"
          >
            <Icone nome="fechar" tamanho={20} />
          </button>
        </div>
        {children}
      </div>
    </div>
  );
};
