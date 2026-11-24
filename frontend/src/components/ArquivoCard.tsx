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
      className={`group relative flex items-center overflow-hidden rounded-xl border bg-linear-to-br from-cartao-claro to-cartao
        shadow-[0_10px_30px_-12px_rgb(0_0_0/0.8)] transition-all duration-300
        hover:-translate-y-1 hover:border-acento/70 hover:shadow-brilho focus-within:border-acento
        ${selecionado ? "border-acento shadow-brilho" : "border-white/10"}`}
    >
      {selecionado && <span aria-hidden="true" className="absolute inset-y-0 left-0 w-1 bg-acento" />}
      <button type="button" onClick={onAbrir} className="flex min-w-0 flex-1 items-center gap-4 rounded-xl p-5 pr-14 text-left focus-visible:outline-none">
        <span
          className={`flex h-14 w-14 shrink-0 items-center justify-center rounded-xl ring-1 ring-white/5 transition-transform duration-300 group-hover:scale-110 ${COR_POR_TIPO[tipo]}`}
        >
          <Icone nome={ICONE_POR_TIPO[tipo]} tamanho={26} />
        </span>
        <span className="min-w-0">
          <span className="block truncate text-base font-semibold" title={nome}>
            {nome}
          </span>
          <span className="mt-0.5 block text-sm text-texto-secundario">
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
