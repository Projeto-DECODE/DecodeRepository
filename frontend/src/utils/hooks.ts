"use client";

import { useRouter } from "next/navigation";
import { useEffect, useRef, useState, useSyncExternalStore } from "react";
import { isSessaoExpirada, mensagemDeErro, Sessao } from "@/resources";

export interface DadosAviso {
  mensagem: string;
  tipo?: "info" | "sucesso" | "erro";
  acao?: string;
  onAcao?: () => void;
}

// Mostra um aviso na parte de baixo da tela por alguns segundos
export function useAviso(): [DadosAviso | null, (aviso: DadosAviso, duracaoMs?: number) => void, () => void] {
  const [aviso, setAviso] = useState<DadosAviso | null>(null);
  const timer = useRef<ReturnType<typeof setTimeout> | null>(null);

  function fechar() {
    if (timer.current) clearTimeout(timer.current);
    setAviso(null);
  }

  function mostrar(novo: DadosAviso, duracaoMs: number = 4000) {
    if (timer.current) clearTimeout(timer.current);
    setAviso(novo);
    timer.current = setTimeout(() => setAviso(null), duracaoMs);
  }

  useEffect(() => {
    return () => {
      if (timer.current) clearTimeout(timer.current);
    };
  }, []);

  return [aviso, mostrar, fechar];
}

// Último bucket aberto: fica em destaque na lista
const CHAVE_ULTIMO_BUCKET = "decode.ultimoBucket";

export function salvarUltimoBucket(bucketId: string) {
  try {
    window.localStorage.setItem(CHAVE_ULTIMO_BUCKET, bucketId);
  } catch {
    // Sem armazenamento: só não destaca
  }
}

export function useUltimoBucket(): string {
  return useSyncExternalStore(
    () => () => undefined,
    () => {
      try {
        return window.localStorage.getItem(CHAVE_ULTIMO_BUCKET) ?? "";
      } catch {
        return "";
      }
    },
    () => ""
  );
}

// Modo de exibição dos buckets (rotativo, grade ou lista), lembrado no navegador
export type ModoExibicao = "rotativo" | "grade" | "lista";
const CHAVE_MODO = "decode.modoBuckets";
const EVENTO_MODO = "decode-modo";

export function useModoExibicao(): [ModoExibicao, (modo: ModoExibicao) => void] {
  const modo = useSyncExternalStore(
    (callback) => {
      window.addEventListener(EVENTO_MODO, callback);
      return () => window.removeEventListener(EVENTO_MODO, callback);
    },
    () => {
      try {
        const salvo = window.localStorage.getItem(CHAVE_MODO);
        return salvo === "grade" || salvo === "lista" ? salvo : "rotativo";
      } catch {
        return "rotativo";
      }
    },
    () => "rotativo"
  ) as ModoExibicao;

  function mudar(novo: ModoExibicao) {
    try {
      window.localStorage.setItem(CHAVE_MODO, novo);
    } catch {
      // Sem armazenamento: vale só até recarregar
    }
    window.dispatchEvent(new Event(EVENTO_MODO));
  }

  return [modo, mudar];
}

// Página só para quem está logado: sem sessão válida, volta para o login
export function useProtegerPagina() {
  const router = useRouter();
  useEffect(() => {
    const sessao = new Sessao();
    if (!sessao.isValida()) {
      // Só fala em "sessão expirada" para quem já tinha entrado
      router.replace(sessao.temToken() ? "/login?expirou=1" : "/login");
    }
  }, [router]);
}

// Trata um erro da API. Sessão expirada: limpa e volta ao login (devolve null).
// Outros erros: devolve a mensagem em português para mostrar na tela.
export function useTratarErro() {
  const router = useRouter();
  return (erro: unknown): string | null => {
    if (isSessaoExpirada(erro)) {
      new Sessao().limpar();
      router.replace("/login?expirou=1");
      return null;
    }
    return mensagemDeErro(erro);
  };
}
