"use client";

import { useState } from "react";
import { Icone, NomeIcone } from "./Icone";

interface CampoProps {
  id: string;
  rotulo: string;
  valor: string;
  onChange: (valor: string) => void;
  tipo?: "text" | "email" | "password";
  placeholder?: string;
  erro?: string;
  dica?: string;
  autoComplete?: string;
  maxLength?: number;
  icone?: NomeIcone; // ícone ao lado do rótulo e dentro do campo
}

export const Campo: React.FC<CampoProps> = ({
  id,
  rotulo,
  valor,
  onChange,
  tipo = "text",
  placeholder,
  erro,
  dica,
  autoComplete,
  maxLength,
  icone,
}: CampoProps) => {
  const [senhaVisivel, setSenhaVisivel] = useState<boolean>(false);
  const ehSenha = tipo === "password";

  return (
    <div className="flex flex-col gap-2">
      <label htmlFor={id} className="flex items-center gap-2 text-sm font-medium text-texto">
        {icone && <Icone nome={icone} tamanho={16} className="text-texto-secundario" />}
        {rotulo}
      </label>
      <div className="relative">
        {icone && (
          <Icone
            nome={icone}
            tamanho={18}
            className="pointer-events-none absolute left-4 top-1/2 -translate-y-1/2 text-texto-secundario"
          />
        )}
        <input
          id={id}
          type={ehSenha && senhaVisivel ? "text" : tipo}
          value={valor}
          placeholder={placeholder}
          autoComplete={autoComplete}
          maxLength={maxLength}
          aria-invalid={erro ? true : undefined}
          aria-describedby={erro || dica ? `${id}-ajuda` : undefined}
          onChange={(event) => onChange(event.target.value)}
          className={`min-h-12 w-full rounded-lg border bg-campo text-base text-texto placeholder:text-texto-secundario/70 transition-all duration-300 focus:outline-none
            ${icone ? "pl-11" : "pl-4"} ${ehSenha ? "pr-12" : "pr-4"}
            ${erro ? "border-erro" : "border-borda hover:border-texto-secundario/50 focus:border-acento focus:shadow-foco"}`}
        />
        {ehSenha && (
          <button
            type="button"
            onClick={() => setSenhaVisivel(!senhaVisivel)}
            aria-label={senhaVisivel ? "Esconder senha" : "Mostrar senha"}
            aria-pressed={senhaVisivel}
            className="absolute right-1 top-1/2 flex h-10 w-10 -translate-y-1/2 items-center justify-center rounded-md text-texto-secundario transition-colors hover:text-acento"
          >
            <Icone nome={senhaVisivel ? "olhoFechado" : "olho"} tamanho={20} />
          </button>
        )}
      </div>
      {(erro || dica) && (
        <p id={`${id}-ajuda`} className={`text-sm ${erro ? "text-erro" : "text-texto-secundario"}`}>
          {erro || dica}
        </p>
      )}
    </div>
  );
};
