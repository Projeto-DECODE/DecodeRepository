"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { useState } from "react";
import { mensagemDeErro, useAuthService } from "@/resources";
import { Botao } from "./Botao";
import { Campo } from "./Campo";

interface FormLoginProps {
  sessaoExpirou?: boolean;
  titulo?: string;
}

// Cartão de login (usado na tela de login e na página inicial)
export const FormLogin: React.FC<FormLoginProps> = ({ sessaoExpirou = false, titulo = "Entrar" }: FormLoginProps) => {
  const router = useRouter();
  const service = useAuthService();

  const [email, setEmail] = useState<string>("");
  const [senha, setSenha] = useState<string>("");
  const [erroEmail, setErroEmail] = useState<string>("");
  const [erroSenha, setErroSenha] = useState<string>("");
  const [erroGeral, setErroGeral] = useState<string>("");
  const [carregando, setCarregando] = useState<boolean>(false);

  async function entrar(event: React.FormEvent) {
    event.preventDefault();
    const emailLimpo = email.trim();
    setErroEmail(emailLimpo ? "" : "Obrigatório");
    setErroSenha(senha ? "" : "Obrigatório");
    setErroGeral("");
    if (!emailLimpo || !senha) return;

    setCarregando(true);
    try {
      await service.login(emailLimpo, senha);
      router.push("/buckets");
    } catch (erro) {
      setErroGeral(mensagemDeErro(erro));
      setCarregando(false);
    }
  }

  return (
    <div className="w-full rounded-xl border border-white/10 bg-cartao/70 p-6 shadow-2xl shadow-black/40 backdrop-blur-md md:p-8">
      <h2 className="mb-6 text-center text-xl font-semibold">{titulo}</h2>

      {sessaoExpirou && !erroGeral && (
        <p className="mb-5 rounded-md border-l-4 border-acento bg-acento/5 px-4 py-3 text-sm" role="status">
          Sua sessão expirou. Entre novamente.
        </p>
      )}
      {erroGeral && (
        <p className="mb-5 rounded-md border-l-4 border-erro bg-erro/10 px-4 py-3 text-sm text-erro" role="alert">
          {erroGeral}
        </p>
      )}

      <form onSubmit={entrar} className="flex flex-col gap-5" noValidate>
        <Campo id="email" rotulo="E-mail" tipo="email" valor={email} onChange={setEmail} erro={erroEmail} autoComplete="email" />
        <Campo
          id="senha"
          rotulo="Senha"
          tipo="password"
          valor={senha}
          onChange={setSenha}
          erro={erroSenha}
          autoComplete="current-password"
        />
        <Botao tipo="submit" larguraTotal desabilitado={carregando}>
          {carregando ? "Entrando…" : "Entrar"}
        </Botao>
      </form>

      <div className="mt-6 flex flex-col items-center gap-2 text-sm">
        <p className="text-texto-secundario">
          Não tem conta?{" "}
          <Link href="/cadastro" className="text-acento hover:underline">
            Criar conta
          </Link>
        </p>
        <p className="text-xs text-texto-secundario">A sessão expira em 15 minutos.</p>
      </div>
    </div>
  );
};
