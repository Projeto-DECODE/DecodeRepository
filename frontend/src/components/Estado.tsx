import { Botao } from "./Botao";
import { Icone, NomeIcone } from "./Icone";

// Telas de "carregando", "vazio" e "erro", usadas nas listas.

export const Carregando: React.FC<{ texto?: string }> = ({ texto = "Carregando…" }) => {
  return (
    <div className="flex flex-col items-center gap-4 py-20 text-texto-secundario" role="status">
      <div className="h-1 w-40 overflow-hidden rounded-full bg-cartao">
        <div className="progresso-indeterminado h-full w-1/3 bg-acento" />
      </div>
      <p className="text-sm">{texto}</p>
    </div>
  );
};

interface MensagemProps {
  icone: NomeIcone;
  texto: string;
  botao?: string;
  onBotao?: () => void;
  erro?: boolean;
}

export const Mensagem: React.FC<MensagemProps> = ({ icone, texto, botao, onBotao, erro = false }: MensagemProps) => {
  return (
    <div className="flex flex-col items-center gap-5 rounded-lg border border-dashed border-borda bg-cartao/40 px-6 py-16 text-center">
      <Icone nome={icone} tamanho={44} className={erro ? "text-erro" : "text-texto-secundario"} />
      <p className="max-w-sm text-texto-secundario">{texto}</p>
      {botao && onBotao && (
        <Botao variante="secundario" onClick={onBotao}>
          {botao}
        </Botao>
      )}
    </div>
  );
};
