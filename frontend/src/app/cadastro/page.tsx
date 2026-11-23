"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { useState } from "react";
import { Botao, Campo, Template } from "@/components";
import { ErroApi, mensagemDeErro, useAuthService, useSessao } from "@/resources";

const REGEX_EMAIL = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;

export default function CadastroPage() {
  const router = useRouter();
  const service = useAuthService();
  const sessao = useSessao();

  const [nome, setNome] = useState<string>("");
  const [email, setEmail] = useState<string>("");
  const [senha, setSenha] = useState<string>("");
  const [erroNome, setErroNome] = useState<string>("");
  const [erroEmail, setErroEmail] = useState<string>("");
  const [erroSenha, setErroSenha] = useState<string>("");
  const [erroGeral, setErroGeral] = useState<string>("");
  const [carregando, setCarregando] = useState<boolean>(false);

  // Mesmas regras do back-end
  function validar(): boolean {
    const n = nome.trim();
    const e = email.trim();
    const eNome = !n ? "Obrigatório" : n.length > 100 ? "Use no máximo 100 caracteres" : "";
    const eEmail = !e ? "Obrigatório" : e.length > 254 || !REGEX_EMAIL.test(e) ? "E-mail inválido" : "";
    const eSenha =
      senha.length < 8
        ? "A senha precisa ter pelo menos 8 caracteres"
        : senha.length > 128
          ? "A senha pode ter no máximo 128 caracteres"
          : "";
    setErroNome(eNome);
    setErroEmail(eEmail);
    setErroSenha(eSenha);
    return !eNome && !eEmail && !eSenha;
  }

  async function cadastrar(event: React.FormEvent) {
    event.preventDefault();
    setErroGeral("");
    if (!validar()) return;

    setCarregando(true);
    try {
      await service.cadastrar(nome.trim(), email.trim(), senha);
    } catch (erro) {
      setCarregando(false);
      if (erro instanceof ErroApi && erro.codigo === "EMAIL_ALREADY_IN_USE") {
        setErroEmail(mensagemDeErro(erro));
      } else {
        setErroGeral(mensagemDeErro(erro));
      }
      return;
    }

    // Conta criada: entra automaticamente
    try {
      await service.login(email.trim(), senha);
      sessao.salvarNome(nome.trim()); // o token não traz o nome
      router.push("/buckets");
    } catch {
      router.push("/login");
    }
  }

  return (
    <Template>
      <section className="mx-auto flex w-full max-w-md flex-col gap-8 py-6 md:py-12">
        <div className="flex flex-col gap-2 text-center">
          <h1 className="text-3xl font-bold">Criar conta</h1>
          <p className="text-texto-secundario">Leva menos de um minuto. Depois, você já entra direto.</p>
        </div>

        {erroGeral && (
          <p className="rounded-md border-l-4 border-erro bg-erro/10 px-4 py-3 text-sm text-erro" role="alert">
            {erroGeral}
          </p>
        )}

        <form
          onSubmit={cadastrar}
          className="surgir flex flex-col gap-5 rounded-2xl border border-acento/50 bg-cartao/80 p-6 shadow-[0_0_40px_-12px_rgb(0_213_232/0.35)] backdrop-blur-md md:p-8"
          noValidate
        >
          <Campo id="nome" rotulo="Nome" icone="usuario" placeholder="Digite seu nome" valor={nome} onChange={setNome} erro={erroNome} autoComplete="name" maxLength={100} />
          <Campo
            id="email"
            rotulo="E-mail"
            tipo="email"
            icone="email"
            placeholder="Digite seu e-mail"
            valor={email}
            onChange={setEmail}
            erro={erroEmail}
            autoComplete="email"
            maxLength={254}
          />
          <Campo
            id="senha"
            rotulo="Senha"
            tipo="password"
            icone="cadeado"
            placeholder="Crie uma senha"
            valor={senha}
            onChange={setSenha}
            erro={erroSenha}
            dica="Mínimo de 8 caracteres"
            autoComplete="new-password"
            maxLength={128}
          />
          <Botao tipo="submit" icone="seta" larguraTotal desabilitado={carregando}>
            {carregando ? "Cadastrando…" : "Cadastrar"}
          </Botao>
        </form>

        <Link href="/login" className="text-center text-sm text-acento hover:underline">
          Já tem conta? Entrar
        </Link>
      </section>
    </Template>
  );
}
