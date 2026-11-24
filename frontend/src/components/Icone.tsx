// Ícones de linha (outline) desenhados em SVG, sem biblioteca externa.

export type NomeIcone =
  | "pasta"
  | "arquivo"
  | "imagem"
  | "pdf"
  | "planilha"
  | "upload"
  | "download"
  | "lixeira"
  | "mais"
  | "sair"
  | "usuario"
  | "fechar"
  | "menu"
  | "voltar"
  | "busca"
  | "nuvem"
  | "cadeado"
  | "raio"
  | "grade"
  | "carrossel"
  | "email"
  | "olho"
  | "olhoFechado"
  | "seta"
  | "calendario"
  | "copiar"
  | "escudo"
  | "banco"
  | "play"
  | "check";

const DESENHOS: Record<NomeIcone, React.ReactNode> = {
  pasta: <path d="M3 6.5V18a1 1 0 0 0 1 1h16a1 1 0 0 0 1-1V8.5a1 1 0 0 0-1-1h-8.5L9.5 5H4a1 1 0 0 0-1 1.5Z" />,
  arquivo: (
    <>
      <path d="M14 3H6a1 1 0 0 0-1 1v16a1 1 0 0 0 1 1h12a1 1 0 0 0 1-1V8l-5-5Z" />
      <path d="M14 3v5h5" />
    </>
  ),
  imagem: (
    <>
      <rect x="3" y="4" width="18" height="16" />
      <circle cx="8.5" cy="9.5" r="1.5" />
      <path d="m21 16-5-5-9 9" />
    </>
  ),
  pdf: (
    <>
      <path d="M14 3H6a1 1 0 0 0-1 1v16a1 1 0 0 0 1 1h12a1 1 0 0 0 1-1V8l-5-5Z" />
      <path d="M14 3v5h5M8.5 13h7M8.5 16.5h7" />
    </>
  ),
  planilha: (
    <>
      <rect x="4" y="3" width="16" height="18" />
      <path d="M4 9h16M4 15h16M10 3v18" />
    </>
  ),
  upload: <path d="M12 16V4m0 0-4.5 4.5M12 4l4.5 4.5M4 15v4a1 1 0 0 0 1 1h14a1 1 0 0 0 1-1v-4" />,
  download: <path d="M12 4v12m0 0-4.5-4.5M12 16l4.5-4.5M4 15v4a1 1 0 0 0 1 1h14a1 1 0 0 0 1-1v-4" />,
  lixeira: <path d="M4 7h16M9.5 7V4.5h5V7M6.5 7l1 13h9l1-13M10 11v5.5M14 11v5.5" />,
  mais: <path d="M12 5v14M5 12h14" />,
  sair: <path d="M10 4H5a1 1 0 0 0-1 1v14a1 1 0 0 0 1 1h5M15 8l4 4-4 4M19 12H9" />,
  usuario: (
    <>
      <circle cx="12" cy="8" r="4" />
      <path d="M4.5 20a7.5 7.5 0 0 1 15 0" />
    </>
  ),
  fechar: <path d="M6 6l12 12M18 6 6 18" />,
  menu: <path d="M4 7h16M4 12h16M4 17h16" />,
  voltar: <path d="M19 12H5m0 0 6-6m-6 6 6 6" />,
  busca: (
    <>
      <circle cx="11" cy="11" r="6.5" />
      <path d="m16 16 4.5 4.5" />
    </>
  ),
  nuvem: <path d="M7 18.5h10.5a4 4 0 0 0 .6-7.95A6 6 0 0 0 6.6 9.1 4.75 4.75 0 0 0 7 18.5Z" />,
  cadeado: (
    <>
      <rect x="5" y="10.5" width="14" height="10" />
      <path d="M8 10.5V7a4 4 0 0 1 8 0v3.5" />
    </>
  ),
  raio: <path d="M13 3 5 13.5h6L10 21l8-10.5h-6L13 3Z" />,
  grade: <path d="M4 4h6.5v6.5H4zM13.5 4H20v6.5h-6.5zM4 13.5h6.5V20H4zM13.5 13.5H20V20h-6.5z" />,
  carrossel: <path d="M8 5h8v14H8zM4 7.5v9M20 7.5v9" />,
  email: (
    <>
      <rect x="3" y="5" width="18" height="14" rx="1.5" />
      <path d="m3.5 6 8.5 7 8.5-7" />
    </>
  ),
  olho: (
    <>
      <path d="M2.5 12S6 5.5 12 5.5 21.5 12 21.5 12 18 18.5 12 18.5 2.5 12 2.5 12Z" />
      <circle cx="12" cy="12" r="3" />
    </>
  ),
  olhoFechado: <path d="M3 3l18 18M10.6 6.1A9.6 9.6 0 0 1 12 6c6 0 9.5 6 9.5 6a17 17 0 0 1-2.6 3.3M6.6 7.6C4 9.4 2.5 12 2.5 12S6 18 12 18a9 9 0 0 0 4.4-1.1M9.9 9.9a3 3 0 0 0 4.2 4.2" />,
  seta: <path d="M5 12h14m0 0-6-6m6 6-6 6" />,
  calendario: (
    <>
      <rect x="4" y="5.5" width="16" height="14.5" rx="1" />
      <path d="M4 10h16M8.5 3.5v4M15.5 3.5v4" />
    </>
  ),
  copiar: (
    <>
      <rect x="8.5" y="8.5" width="11.5" height="11.5" rx="1" />
      <path d="M15.5 8.5V5a1 1 0 0 0-1-1H5a1 1 0 0 0-1 1v9.5a1 1 0 0 0 1 1h3.5" />
    </>
  ),
  escudo: <path d="M12 3 4.5 6v5.5c0 4.5 3.2 8 7.5 9.5 4.3-1.5 7.5-5 7.5-9.5V6L12 3Zm-3 9 2.2 2.2L15.5 10" />,
  banco: (
    <>
      <ellipse cx="12" cy="6" rx="7.5" ry="2.75" />
      <path d="M4.5 6v12c0 1.5 3.4 2.75 7.5 2.75s7.5-1.25 7.5-2.75V6M4.5 12c0 1.5 3.4 2.75 7.5 2.75s7.5-1.25 7.5-2.75" />
    </>
  ),
  play: <path d="M7 4.5v15l12-7.5-12-7.5Z" />,
  check: <path d="m5 12.5 4.5 4.5L19 7.5" />,
};

interface IconeProps {
  nome: NomeIcone;
  tamanho?: number;
  className?: string;
}

export const Icone: React.FC<IconeProps> = ({ nome, tamanho = 24, className = "" }: IconeProps) => {
  return (
    <svg
      width={tamanho}
      height={tamanho}
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth={1.5}
      strokeLinecap="round"
      strokeLinejoin="round"
      className={className}
      aria-hidden="true"
    >
      {DESENHOS[nome]}
    </svg>
  );
};
