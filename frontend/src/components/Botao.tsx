import { Icone, NomeIcone } from "./Icone";

type Variante = "primario" | "secundario" | "perigo" | "discreto";

interface BotaoProps {
  children?: React.ReactNode;
  variante?: Variante;
  icone?: NomeIcone;
  tipo?: "button" | "submit";
  desabilitado?: boolean;
  larguraTotal?: boolean;
  onClick?: () => void;
  rotulo?: string; // texto para leitores de tela quando o botão só tem ícone
}

const ESTILOS: Record<Variante, string> = {
  primario: "bg-acento text-fundo font-semibold hover:bg-acento-suave hover:shadow-brilho-botao",
  secundario: "border border-borda bg-campo text-texto hover:border-acento hover:text-acento",
  perigo: "border border-erro/70 bg-erro/5 text-erro font-semibold hover:bg-erro hover:text-texto",
  discreto: "text-texto-secundario hover:text-texto",
};

export const Botao: React.FC<BotaoProps> = ({
  children,
  variante = "primario",
  icone,
  tipo = "button",
  desabilitado = false,
  larguraTotal = false,
  onClick,
  rotulo,
}: BotaoProps) => {
  return (
    <button
      type={tipo}
      onClick={onClick}
      disabled={desabilitado}
      aria-label={rotulo}
      title={rotulo}
      className={`inline-flex min-h-11 items-center justify-center gap-2 rounded-lg px-5 py-2.5 text-sm transition-all duration-300 active:scale-[0.98] disabled:cursor-not-allowed disabled:opacity-50 disabled:shadow-none
        ${ESTILOS[variante]} ${larguraTotal ? "w-full" : ""}`}
    >
      {icone && <Icone nome={icone} tamanho={20} />}
      {children}
    </button>
  );
};
