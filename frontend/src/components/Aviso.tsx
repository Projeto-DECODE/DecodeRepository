interface AvisoProps {
  mensagem: string;
  tipo?: "info" | "sucesso" | "erro";
  acao?: string;
  onAcao?: () => void;
}

// Mensagem fixa na parte de baixo da tela (ex.: "Bucket excluído · Desfazer")
export const Aviso: React.FC<AvisoProps> = ({ mensagem, tipo = "info", acao, onAcao }: AvisoProps) => {
  const cor = tipo === "erro" ? "border-erro" : tipo === "sucesso" ? "border-sucesso" : "border-acento";
  return (
    <div
      role="status"
      aria-live="polite"
      className={`surgir fixed bottom-6 left-1/2 z-40 flex w-[calc(100%-2rem)] max-w-md -translate-x-1/2 items-center justify-between gap-4 rounded-md border border-white/10 border-l-4 bg-cartao-claro/95 px-5 py-4 shadow-2xl backdrop-blur ${cor}`}
    >
      <p className="text-sm">{mensagem}</p>
      {acao && onAcao && (
        <button type="button" onClick={onAcao} className="min-h-11 px-2 text-sm font-bold text-acento hover:underline">
          {acao}
        </button>
      )}
    </div>
  );
};
