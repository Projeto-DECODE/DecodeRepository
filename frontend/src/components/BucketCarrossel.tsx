"use client";

import { useRef } from "react";
import { Bucket, ResumoBucket } from "@/resources";
import { formatarData } from "@/utils/formatos";
import { BucketCard } from "./BucketCard";
import { Icone } from "./Icone";

interface BucketCarrosselProps {
  buckets: Bucket[];
  resumos: Record<string, ResumoBucket>;
  indiceAtivo: number;
  enviandoId: string;
  onMudar: (indice: number) => void;
  onAbrir: (bucket: Bucket) => void;
  onExcluir: (bucket: Bucket) => void;
  onSoltarArquivos: (bucket: Bucket, arquivos: File[]) => void;
}

const VISIVEIS_DE_CADA_LADO = 3;

// Modo "rotativo": o bucket ativo fica no centro, maior e com brilho verde,
// e os vizinhos vão diminuindo e ficando desfocados para os lados.
export const BucketCarrossel: React.FC<BucketCarrosselProps> = ({
  buckets,
  resumos,
  indiceAtivo,
  enviandoId,
  onMudar,
  onAbrir,
  onExcluir,
  onSoltarArquivos,
}: BucketCarrosselProps) => {
  const inicioToque = useRef<number | null>(null);
  const arrastou = useRef<boolean>(false); // evita abrir o bucket ao terminar um arraste
  const total = buckets.length;

  function ir(indice: number) {
    if (indice >= 0 && indice < total) onMudar(indice);
  }

  function aoTeclar(event: React.KeyboardEvent<HTMLDivElement>) {
    if (event.key === "ArrowLeft") {
      event.preventDefault();
      ir(indiceAtivo - 1);
    } else if (event.key === "ArrowRight") {
      event.preventDefault();
      ir(indiceAtivo + 1);
    } else if (event.key === "Home") {
      ir(0);
    } else if (event.key === "End") {
      ir(total - 1);
    }
  }

  // Arrastar com o dedo (celular) ou com o mouse para os lados
  function aoApertarPonteiro(event: React.PointerEvent<HTMLDivElement>) {
    inicioToque.current = event.clientX;
    arrastou.current = false;
  }

  function aoSoltarPonteiro(event: React.PointerEvent<HTMLDivElement>) {
    if (inicioToque.current === null) return;
    const distancia = event.clientX - inicioToque.current;
    inicioToque.current = null;
    if (Math.abs(distancia) > 50) {
      arrastou.current = true;
      // Arrastou bastante: pula mais de um cartão
      const passos = Math.max(1, Math.round(Math.abs(distancia) / 220));
      const destino = distancia < 0 ? indiceAtivo + passos : indiceAtivo - passos;
      onMudar(Math.min(total - 1, Math.max(0, destino)));
    }
  }

  // Se foi um arraste, o clique que vem logo depois não deve abrir o bucket
  function aoClicar(event: React.MouseEvent<HTMLDivElement>) {
    if (arrastou.current) {
      event.preventDefault();
      event.stopPropagation();
      arrastou.current = false;
    }
  }

  const renderCartao = (bucket: Bucket, indice: number) => {
    const distancia = indice - indiceAtivo;
    const afastamento = Math.abs(distancia);
    if (afastamento > VISIVEIS_DE_CADA_LADO) return null;
    const ativo = distancia === 0;

    const escala = ativo ? 1 : 1 - afastamento * 0.12;
    const opacidade = ativo ? 1 : afastamento === 1 ? 0.75 : afastamento === 2 ? 0.4 : 0.15;
    const desfoque = afastamento >= 2 ? afastamento - 1 : 0;

    return (
      <div
        key={bucket.id}
        role="group"
        aria-roledescription="bucket"
        aria-label={`${indice + 1} de ${total}: ${bucket.name}`}
        aria-hidden={!ativo && afastamento > 1}
        className="absolute left-1/2 top-1/2 h-[340px] w-[250px] transition-all duration-500 ease-[cubic-bezier(0.22,1,0.36,1)] md:h-[380px] md:w-[290px]"
        style={{
          transform: `translate(-50%, -50%) translateX(calc(${distancia} * var(--passo))) scale(${escala})`,
          opacity: opacidade,
          filter: desfoque ? `blur(${desfoque}px)` : undefined,
          zIndex: 10 - afastamento,
        }}
      >
        <BucketCard
          id={bucket.id ?? ""}
          nome={bucket.name ?? ""}
          dataCriacao={formatarData(bucket.createdAt)}
          resumo={resumos[bucket.id ?? ""]}
          modo="rotativo"
          ativo={ativo}
          excluindo={bucket.status === "DELETING"}
          enviando={bucket.id === enviandoId}
          onAbrir={() => onAbrir(bucket)}
          onExcluir={() => onExcluir(bucket)}
          onSoltarArquivos={(arquivos) => onSoltarArquivos(bucket, arquivos)}
        />
        {/* Cartões dos lados: clicar traz para o centro (em vez de abrir) */}
        {!ativo && (
          <button
            type="button"
            tabIndex={-1}
            aria-label={`Mostrar ${bucket.name}`}
            onClick={() => ir(indice)}
            className="absolute inset-0 z-10 cursor-pointer rounded-2xl"
          />
        )}
      </div>
    );
  };

  const botaoSeta = (direcao: "anterior" | "proximo") => {
    const desabilitado = direcao === "anterior" ? indiceAtivo === 0 : indiceAtivo === total - 1;
    return (
      <button
        type="button"
        onClick={() => ir(direcao === "anterior" ? indiceAtivo - 1 : indiceAtivo + 1)}
        disabled={desabilitado}
        aria-label={direcao === "anterior" ? "Bucket anterior" : "Próximo bucket"}
        className={`absolute top-1/2 z-20 flex h-12 w-12 -translate-y-1/2 items-center justify-center rounded-full border border-white/10 bg-cartao/80 text-texto backdrop-blur transition-all duration-300 hover:border-acento hover:text-acento disabled:pointer-events-none disabled:opacity-0
          ${direcao === "anterior" ? "left-0 md:left-2" : "right-0 md:right-2"}`}
      >
        <Icone nome="voltar" tamanho={20} className={direcao === "proximo" ? "rotate-180" : ""} />
      </button>
    );
  };

  return (
    <div className="flex w-full flex-col items-center gap-6">
      <div
        tabIndex={0}
        role="region"
        aria-roledescription="carrossel"
        aria-label="Buckets. Use as setas esquerda e direita para navegar."
        onKeyDown={aoTeclar}
        onPointerDown={aoApertarPonteiro}
        onPointerUp={aoSoltarPonteiro}
        onClickCapture={aoClicar}
        onDragStart={(event) => event.preventDefault()} // não "arrastar o link" com o mouse
        onPointerLeave={() => (inicioToque.current = null)}
        className="relative h-[400px] w-full touch-pan-y select-none overflow-hidden rounded-2xl [--passo:200px] focus-visible:outline-2 focus-visible:outline-acento sm:[--passo:240px] md:h-[440px] md:[--passo:280px] lg:[--passo:300px]"
      >
        {/* Brilho verde atrás do cartão central */}
        <div
          aria-hidden="true"
          className="pointer-events-none absolute left-1/2 top-1/2 h-72 w-72 -translate-x-1/2 -translate-y-1/2 rounded-full bg-acento/10 blur-3xl"
        />
        {buckets.map(renderCartao)}
        {botaoSeta("anterior")}
        {botaoSeta("proximo")}
      </div>

      {/* Bolinhas de posição */}
      <div className="flex flex-wrap items-center justify-center gap-2" aria-label="Escolher bucket">
        {buckets.map((bucket, indice) => (
          <button
            key={bucket.id}
            type="button"
            onClick={() => ir(indice)}
            aria-label={`Ir para ${bucket.name}`}
            aria-current={indice === indiceAtivo}
            className="flex h-6 items-center justify-center px-0.5"
          >
            <span
              className={`block h-2 rounded-full transition-all duration-300 ${indice === indiceAtivo ? "w-6 bg-acento" : "w-2 bg-white/20 hover:bg-white/40"}`}
            />
          </button>
        ))}
      </div>
    </div>
  );
};
