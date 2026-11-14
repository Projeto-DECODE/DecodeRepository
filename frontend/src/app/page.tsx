"use client";

import Link from "next/link";
import { FormLogin, Icone, NomeIcone, Template } from "@/components";
import { useUsuarioLogado } from "@/resources";

interface Recurso {
  icone: NomeIcone;
  titulo: string;
  texto: string;
  cor: string;
}

// Somente o que o sistema faz de verdade hoje
const RECURSOS: Recurso[] = [
  { icone: "pasta", titulo: "Buckets", texto: "Organize seus arquivos em pastas com nomes simples.", cor: "bg-tipo-imagem/15 text-tipo-imagem" },
  { icone: "upload", titulo: "Envio rápido", texto: "Arraste e solte arquivos de até 10 MB direto no navegador.", cor: "bg-acento/15 text-acento" },
  { icone: "download", titulo: "Prévia e download", texto: "Veja imagens antes de baixar e confira os detalhes de cada arquivo.", cor: "bg-tipo-outro/15 text-tipo-outro" },
  { icone: "cadeado", titulo: "Acesso protegido", texto: "Login com token que expira em 15 minutos. Só você vê os seus arquivos.", cor: "bg-tipo-pdf/15 text-tipo-pdf" },
];

export default function InicioPage() {
  const nomeLogado = useUsuarioLogado();

  const renderRecurso = (recurso: Recurso) => {
    return (
      <div
        key={recurso.titulo}
        className="group flex flex-col gap-4 rounded-lg border border-white/5 bg-cartao p-6 transition-all duration-300 hover:-translate-y-1 hover:border-acento/60"
      >
        <span className={`flex h-12 w-12 items-center justify-center rounded-md transition-transform duration-300 group-hover:scale-110 ${recurso.cor}`}>
          <Icone nome={recurso.icone} tamanho={26} />
        </span>
        <h3 className="text-lg font-medium">{recurso.titulo}</h3>
        <p className="text-sm text-texto-secundario">{recurso.texto}</p>
      </div>
    );
  };

  return (
    <Template>
      <section className="grid items-center gap-10 py-6 md:py-14 lg:grid-cols-[1.2fr_1fr] lg:gap-16">
        <div className="flex flex-col items-start gap-6">
          <span className="flex items-center gap-2 rounded-full border border-acento/30 bg-acento/5 px-3 py-1 text-xs font-medium text-acento">
            <span className="h-1.5 w-1.5 rounded-full bg-acento" /> Plataforma DECODE
          </span>
          <h1 className="text-4xl font-bold leading-tight md:text-5xl xl:text-6xl">
            Armazenamento na Nuvem com <span className="text-acento">Controle Total</span>
          </h1>
          <p className="max-w-xl text-lg text-texto-secundario">
            Guarde, organize e encontre seus arquivos em buckets. Envie, visualize e baixe tudo pelo navegador ou pelo celular.
          </p>
          {nomeLogado ? (
            <Link
              href="/buckets"
              className="flex min-h-12 items-center gap-2 rounded-md bg-acento px-6 font-semibold text-fundo transition-all duration-300 hover:bg-acento-suave hover:shadow-brilho-botao"
            >
              Ir para meus buckets <Icone nome="voltar" tamanho={18} className="rotate-180" />
            </Link>
          ) : (
            <Link
              href="/cadastro"
              className="flex min-h-12 items-center rounded-md bg-acento px-6 font-semibold text-fundo transition-all duration-300 hover:bg-acento-suave hover:shadow-brilho-botao lg:hidden"
            >
              Criar conta grátis
            </Link>
          )}
        </div>

        {/* No computador, o login já aparece ao lado do título */}
        {!nomeLogado && (
          <div className="hidden lg:block">
            <FormLogin titulo="Acesse sua conta" />
          </div>
        )}
      </section>

      <section id="recursos" className="scroll-mt-24 py-10">
        <h2 className="mb-6 text-2xl font-semibold">Recursos</h2>
        <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">{RECURSOS.map(renderRecurso)}</div>
      </section>
    </Template>
  );
}
