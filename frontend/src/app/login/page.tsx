"use client";

import { useSearchParams } from "next/navigation";
import { Suspense } from "react";
import { FormLogin, Icone, Template } from "@/components";

export default function LoginPage() {
  return (
    <Template>
      <section className="mx-auto flex w-full max-w-md flex-col items-center gap-6 py-4 md:py-10">
        <div className="flex flex-col items-center gap-3 text-center">
          <span className="flex h-16 w-16 items-center justify-center rounded-full bg-acento/10 text-acento shadow-brilho-botao">
            <Icone nome="nuvem" tamanho={36} />
          </span>
          <p className="text-texto-secundario">Seus arquivos, organizados em buckets</p>
        </div>
        {/* useSearchParams precisa ficar dentro de um Suspense no Next.js */}
        <Suspense>
          <LoginComAviso />
        </Suspense>
      </section>
    </Template>
  );
}

function LoginComAviso() {
  const parametros = useSearchParams();
  return <FormLogin sessaoExpirou={parametros.get("expirou") === "1"} />;
}
