"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { useState } from "react";
import { useSessao, useUsuarioLogado } from "@/resources";
import { Botao } from "./Botao";
import { Icone } from "./Icone";
import { Modal } from "./Modal";

interface TemplateProps {
  children: React.ReactNode;
}

export const Template: React.FC<TemplateProps> = (props: TemplateProps) => {
  return (
    <>
      <Header />
      <main className="container mx-auto w-full flex-1 px-4 py-8 md:py-12">{props.children}</main>
      <Footer />
    </>
  );
};

const Header: React.FC = () => {
  const router = useRouter();
  const sessao = useSessao();
  const nomeLogado = useUsuarioLogado();
  const [menuAberto, setMenuAberto] = useState<boolean>(false);
  const [perfilAberto, setPerfilAberto] = useState<boolean>(false);

  function sair() {
    sessao.limpar();
    setMenuAberto(false);
    router.push("/login");
  }

  const linkClasse = "flex min-h-11 items-center rounded-md px-3 text-sm text-texto-secundario transition-colors hover:text-acento";

  const renderLinksPublicos = () => (
    <>
      <Link href="/" className={linkClasse} onClick={() => setMenuAberto(false)}>
        Início
      </Link>
      <Link href="/#recursos" className={linkClasse} onClick={() => setMenuAberto(false)}>
        Recursos
      </Link>
      <Link href="/login" className={linkClasse} onClick={() => setMenuAberto(false)}>
        Log in
      </Link>
      <Link
        href="/cadastro"
        onClick={() => setMenuAberto(false)}
        className="ml-2 flex min-h-11 items-center rounded-md bg-acento px-5 text-sm font-semibold text-fundo transition-all duration-300 hover:bg-acento-suave hover:shadow-brilho-botao"
      >
        Criar conta
      </Link>
    </>
  );

  const renderLinksLogado = () => (
    <>
      <Link href="/buckets" className={linkClasse} onClick={() => setMenuAberto(false)}>
        Meus buckets
      </Link>
      <span className="flex min-h-11 items-center gap-2 px-3 text-sm text-texto">
        <span className="flex h-8 w-8 items-center justify-center rounded-full bg-acento/15 text-xs font-bold uppercase text-acento">
          {nomeLogado.charAt(0)}
        </span>
        Olá, {nomeLogado}
      </span>
      <button
        type="button"
        className="flex min-h-11 items-center gap-2 border border-texto-secundario/50 px-4 text-sm text-texto transition-colors hover:border-acento hover:text-acento md:min-h-9"
        onClick={() => {
          setMenuAberto(false);
          setPerfilAberto(true);
        }}
      >
        <Icone nome="usuario" tamanho={16} /> Perfil
      </button>
      <button
        type="button"
        className="flex min-h-11 items-center gap-2 border border-erro/70 px-4 text-sm text-erro transition-colors hover:bg-erro hover:text-fundo md:min-h-9"
        onClick={sair}
      >
        <Icone nome="sair" tamanho={16} /> Sair
      </button>
    </>
  );

  return (
    <header className="sticky top-0 z-20 border-b border-white/5 bg-fundo/80 backdrop-blur-md">
      <div className="container mx-auto flex items-center justify-between gap-4 px-4 py-4">
        <Link href={nomeLogado ? "/buckets" : "/"} className="flex items-center gap-3">
          <Icone nome="nuvem" tamanho={30} className="text-acento" />
          <span className="text-xl font-bold tracking-[0.2em]">DECODE</span>
          <span className="hidden border-l border-borda pl-3 text-sm text-texto-secundario lg:block">
            Armazenamento na Nuvem com Controle Total
          </span>
        </Link>

        {/* Computador */}
        <nav className="hidden items-center gap-2 md:flex" aria-label="Menu principal">
          {nomeLogado ? renderLinksLogado() : renderLinksPublicos()}
        </nav>

        {/* Celular: menu hambúrguer */}
        <button
          type="button"
          className="flex h-11 w-11 items-center justify-center md:hidden"
          aria-label={menuAberto ? "Fechar menu" : "Abrir menu"}
          aria-expanded={menuAberto}
          onClick={() => setMenuAberto(!menuAberto)}
        >
          <Icone nome={menuAberto ? "fechar" : "menu"} />
        </button>
      </div>

      {menuAberto && (
        <nav className="flex flex-col gap-2 border-t border-borda px-4 py-3 md:hidden" aria-label="Menu principal">
          {nomeLogado ? renderLinksLogado() : renderLinksPublicos()}
        </nav>
      )}

      <Modal titulo="Meu perfil" aberto={perfilAberto} onFechar={() => setPerfilAberto(false)}>
        <dl className="mb-6 flex flex-col gap-3">
          {sessao.getNome() && (
            <div className="flex justify-between gap-4 border-b border-borda pb-3">
              <dt className="text-texto-secundario">Nome</dt>
              <dd>{sessao.getNome()}</dd>
            </div>
          )}
          <div className="flex justify-between gap-4 border-b border-borda pb-3">
            <dt className="text-texto-secundario">E-mail</dt>
            <dd className="break-all">{sessao.getEmail()}</dd>
          </div>
        </dl>
        <Botao larguraTotal onClick={() => setPerfilAberto(false)}>
          OK
        </Botao>
      </Modal>
    </header>
  );
};

const Footer: React.FC = () => {
  return (
    <footer className="border-t border-borda">
      <div className="container mx-auto px-4 py-6 text-center text-sm text-texto-secundario">
        DECODE Cloud Storage © 2026 · Projeto Integrador IV · SENAI-SP
      </div>
    </footer>
  );
};
