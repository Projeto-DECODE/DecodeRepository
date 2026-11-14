import { TipoArquivo } from "@/utils/formatos";
import { Icone, NomeIcone } from "./Icone";

export const ICONE_POR_TIPO: Record<TipoArquivo, NomeIcone> = {
  imagem: "imagem",
  pdf: "pdf",
  planilha: "planilha",
  outro: "arquivo",
};

// Classes de cor de cada tipo (definidas no globals.css)
export const COR_POR_TIPO: Record<TipoArquivo, string> = {
  imagem: "bg-tipo-imagem/15 text-tipo-imagem",
  pdf: "bg-tipo-pdf/15 text-tipo-pdf",
  planilha: "bg-tipo-planilha/15 text-tipo-planilha",
  outro: "bg-tipo-outro/15 text-tipo-outro",
};

interface ArquivoCardProps {
  nome: string;
  tamanho: string;
  dataUpload: string;
  tipo: TipoArquivo;
  selecionado?: boolean;
  onAbrir: () => void;
  onExcluir: () => void;
}

export const ArquivoCard: React.FC<ArquivoCardProps> = ({
  nome,
  tamanho,
  dataUpload,
  tipo,
  selecionado = false,
  onAbrir,
  onExcluir,
}: ArquivoCardProps) => {
  return (
    <div
      className={`group relative flex items-center rounded-lg border bg-cartao transition-all duration-300
        hover:border-acento/70 hover:bg-cartao-claro focus-within:border-acento
        ${selecionado ? "border-acento shadow-brilho" : "border-white/5"}`}
    >
      <button type="button" onClick={onAbrir} className="flex min-w-0 flex-1 items-center gap-4 rounded-lg p-4 pr-14 text-left focus-visible:outline-none">
        <span
          className={`flex h-12 w-12 shrink-0 items-center justify-center rounded-md transition-transform duration-300 group-hover:scale-105 ${COR_POR_TIPO[tipo]}`}
        >
          <Icone nome={ICONE_POR_TIPO[tipo]} tamanho={24} />
        </span>
        <span className="min-w-0">
          <span className="block truncate font-medium" title={nome}>
            {nome}
          </span>
          <span className="block text-sm text-texto-secundario">
            {tamanho} · {dataUpload}
          </span>
        </span>
      </button>
      <button
        type="button"
        onClick={onExcluir}
        aria-label={`Excluir o arquivo ${nome}`}
        title="Excluir"
        className="absolute right-2 flex h-11 w-11 items-center justify-center rounded-md text-texto-secundario transition-colors hover:bg-erro/10 hover:text-erro"
      >
        <Icone nome="lixeira" tamanho={18} />
      </button>
    </div>
  );
};
