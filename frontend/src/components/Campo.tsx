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
}: CampoProps) => {
  return (
    <div className="flex flex-col gap-1.5">
      <label htmlFor={id} className="text-sm text-texto">
        {rotulo}
      </label>
      <input
        id={id}
        type={tipo}
        value={valor}
        placeholder={placeholder}
        autoComplete={autoComplete}
        maxLength={maxLength}
        aria-invalid={erro ? true : undefined}
        aria-describedby={erro || dica ? `${id}-ajuda` : undefined}
        onChange={(event) => onChange(event.target.value)}
        className={`min-h-12 rounded-md border bg-fundo/60 px-4 text-base text-texto placeholder:text-texto-secundario/60 transition-all duration-300 focus:outline-none
          ${erro ? "border-erro" : "border-borda focus:border-acento focus:shadow-[0_0_0_3px_rgb(57_255_20/0.15)]"}`}
      />
      {(erro || dica) && (
        <p id={`${id}-ajuda`} className={`text-sm ${erro ? "text-erro" : "text-texto-secundario"}`}>
          {erro || dica}
        </p>
      )}
    </div>
  );
};
