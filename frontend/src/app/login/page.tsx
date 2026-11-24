"use client";

import { useSearchParams } from "next/navigation";
import { Suspense } from "react";
import { FormLogin, Icone, Template } from "@/components";

export default function LoginPage() {
  return (
    <Template>
      <section className="mx-auto flex w-full max-w-md flex-col items-center gap-6 py-4 md:py-10">
        <div className="flex flex-col items-center gap-3 text-center">
          <span className="flex h-20 w-20 items-center justify-center rounded-full border border-acento/50 bg-acento/10 text-acento shadow-[0_0_40px_-6px_rgb(0_213_232/0.6)]">
            <Icone nome="nuvem" tamanho={40} />
          </span>
          <p className="text-texto">Seus arquivos, organizados em buckets</p>
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
