"use client";

import { useRouter } from "next/navigation";
import { useEffect, useRef, useState } from "react";
import { Aviso, Botao, BucketCard, BucketCarrossel, Campo, Icone, Mensagem, Modal, NomeIcone, Template } from "@/components";
import { Bucket, LIMITE_UPLOAD_BYTES, ResumoBucket, useArquivoService, useBucketService } from "@/resources";
import { formatarData, REGEX_NOME_BUCKET } from "@/utils/formatos";
import {
  ModoExibicao,
  salvarUltimoBucket,
  useAviso,
  useModoExibicao,
  useProtegerPagina,
  useTratarErro,
  useUltimoBucket,
} from "@/utils/hooks";

type Estado = "carregando" | "conteudo" | "erro";
type Ordem = "recentes" | "nome" | "arquivos";

interface ExclusaoPendente {
  bucket: Bucket;
  posicao: number;
  timer: ReturnType<typeof setTimeout>;
}

const TEMPO_DESFAZER_MS = 5000;

export default function BucketsPage() {
  useProtegerPagina();
  const router = useRouter();
  const bucketService = useBucketService();
  const arquivoService = useArquivoService();
  const tratarErro = useTratarErro();
  const [aviso, mostrarAviso, fecharAviso] = useAviso();
  const ultimoBucket = useUltimoBucket();

  const [buckets, setBuckets] = useState<Bucket[]>([]);
  const [resumos, setResumos] = useState<Record<string, ResumoBucket>>({});
  const [estado, setEstado] = useState<Estado>("carregando");
  const [mensagemErro, setMensagemErro] = useState<string>("");

  // Busca, ordem e modo de exibição
  const [busca, setBusca] = useState<string>("");
  const [ordem, setOrdem] = useState<Ordem>("recentes");
  const [modo, setModo] = useModoExibicao();
  const [selecionadoId, setSelecionadoId] = useState<string>(""); // centro do rotativo / foco do teclado
  const [enviandoId, setEnviandoId] = useState<string>("");

  const [modalAberto, setModalAberto] = useState<boolean>(false);
  const [nomeNovo, setNomeNovo] = useState<string>("");
  const [erroNome, setErroNome] = useState<string>("");
  const [criando, setCriando] = useState<boolean>(false);

  const campoBusca = useRef<HTMLInputElement>(null);
  const grade = useRef<HTMLElement>(null);

  // Exclusão adiada: só vai ao servidor quando o tempo do "Desfazer" acaba
  const pendente = useRef<ExclusaoPendente | null>(null);

  // ---------- Carregar ----------

  function buscarBuckets() {
    bucketService.listar().then(aplicarLista).catch(aplicarErro);
  }

  function aplicarLista(lista: Bucket[]) {
    setBuckets(lista);
    setEstado("conteudo");
    resumirBuckets(lista);
  }

  function aplicarErro(erro: unknown) {
    const mensagem = tratarErro(erro);
    if (mensagem) {
      setMensagemErro(mensagem);
      setEstado("erro");
    }
  }

  // Números de cada bucket: Imagens · Docs · Outros (o back-end não manda junto com a lista)
  function resumirBuckets(lista: Bucket[]) {
    for (const bucket of lista) atualizarResumo(bucket.id ?? "");
  }

  function atualizarResumo(bucketId: string) {
    arquivoService
      .resumir(bucketId)
      .then((resumo) => setResumos((atuais) => ({ ...atuais, [bucketId]: resumo })))
      .catch(() => undefined); // sem o resumo, o cartão mostra "—"
  }

  function tentarDeNovo() {
    setEstado("carregando");
    buscarBuckets();
  }

  useEffect(() => {
    bucketService.listar().then(aplicarLista).catch(aplicarErro);
    // Saiu da página com uma exclusão esperando: executa agora
    return () => {
      if (pendente.current) {
        clearTimeout(pendente.current.timer);
        bucketService.excluir(pendente.current.bucket.id ?? "").catch(() => undefined);
        pendente.current = null;
      }
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  // Atalhos: "/" vai para a busca, "N" abre o "Novo bucket"
  useEffect(() => {
    const aoTeclar = (event: KeyboardEvent) => {
      const alvo = event.target as HTMLElement;
      if (alvo.tagName === "INPUT" || alvo.tagName === "SELECT" || alvo.tagName === "TEXTAREA") return;
      if (event.key === "/") {
        event.preventDefault();
        campoBusca.current?.focus();
      }
      if (event.key === "n" || event.key === "N") {
        event.preventDefault();
        abrirModal();
      }
    };
    window.addEventListener("keydown", aoTeclar);
    return () => window.removeEventListener("keydown", aoTeclar);
  }, []);

  // Setas do teclado andam entre os cartões
  function navegarComSetas(event: React.KeyboardEvent<HTMLElement>) {
    if (modo === "rotativo") return; // o rotativo trata as setas sozinho
    if (!grade.current || !["ArrowLeft", "ArrowRight", "ArrowUp", "ArrowDown"].includes(event.key)) return;
    const cartoes = Array.from(grade.current.querySelectorAll<HTMLAnchorElement>("[data-cartao-bucket]"));
    const atual = cartoes.indexOf(document.activeElement as HTMLAnchorElement);
    if (atual === -1) return;
    const colunas = modo === "lista" ? 1 : getComputedStyle(grade.current).gridTemplateColumns.split(" ").length;
    const passo = { ArrowLeft: -1, ArrowRight: 1, ArrowUp: -colunas, ArrowDown: colunas }[event.key] ?? 0;
    const proximo = cartoes[atual + passo];
    if (proximo) {
      event.preventDefault();
      proximo.focus();
    }
  }

  // ---------- Criar ----------

  function abrirModal() {
    setNomeNovo("");
    setErroNome("");
    setModalAberto(true);
  }

  async function criarBucket(event: React.FormEvent) {
    event.preventDefault();
    const nome = nomeNovo.trim();
    if (nome.length < 3 || nome.length > 63 || !REGEX_NOME_BUCKET.test(nome)) {
      setErroNome("Nome inválido. Use letras minúsculas, números e hífen (3 a 63).");
      return;
    }
    setCriando(true);
    try {
      const bucket = await bucketService.criar(nome);
      setBuckets([...buckets, bucket]);
      setResumos({ ...resumos, [bucket.id ?? ""]: new ResumoBucket() });
      setEstado("conteudo");
      setModalAberto(false);
      setBusca("");
      salvarUltimoBucket(bucket.id ?? "");
      setSelecionadoId(bucket.id ?? "");
      setOrdem("recentes");
      mostrarAviso({ mensagem: `Bucket "${bucket.name}" criado. Arraste arquivos para ele.`, tipo: "sucesso" });
    } catch (erro) {
      const mensagem = tratarErro(erro);
      if (mensagem) setErroNome(mensagem);
    }
    setCriando(false);
  }

  // ---------- Enviar arquivos soltando em cima do cartão ----------

  async function enviarPara(bucket: Bucket, arquivos: File[]) {
    const bucketId = bucket.id ?? "";
    setEnviandoId(bucketId);
    let enviados = 0;
    for (const arquivo of arquivos) {
      if (arquivo.size === 0 || arquivo.size > LIMITE_UPLOAD_BYTES) {
        mostrarAviso({
          mensagem: `${arquivo.name}: ${arquivo.size === 0 ? "o arquivo está vazio" : "arquivo maior que 10 MB"}.`,
          tipo: "erro",
        });
        continue;
      }
      try {
        await arquivoService.enviar(bucketId, arquivo);
        enviados++;
      } catch (erro) {
        const mensagem = tratarErro(erro);
        if (!mensagem) return;
        mostrarAviso({ mensagem: `${arquivo.name}: ${mensagem}`, tipo: "erro" });
      }
    }
    setEnviandoId("");
    if (enviados > 0) {
      atualizarResumo(bucketId);
      mostrarAviso(
        {
          mensagem: `${enviados} ${enviados === 1 ? "arquivo enviado" : "arquivos enviados"} para ${bucket.name}`,
          tipo: "sucesso",
          acao: "Abrir",
          onAcao: () => {
            salvarUltimoBucket(bucketId);
            router.push(`/buckets/${bucketId}`);
          },
        },
        6000
      );
    }
  }

  // ---------- Excluir com "Desfazer" ----------

  async function executarExclusao(exclusao: ExclusaoPendente) {
    try {
      await bucketService.excluir(exclusao.bucket.id ?? "");
    } catch (erro) {
      // Não deu para excluir (ex.: bucket com arquivos): o cartão volta
      setBuckets((atuais) => {
        const lista = [...atuais];
        lista.splice(Math.min(exclusao.posicao, lista.length), 0, exclusao.bucket);
        return lista;
      });
      const mensagem = tratarErro(erro);
      if (mensagem) mostrarAviso({ mensagem, tipo: "erro" });
    }
  }

  function excluirComDesfazer(bucket: Bucket) {
    // Havia outra exclusão esperando? Confirma ela agora
    if (pendente.current) {
      clearTimeout(pendente.current.timer);
      executarExclusao(pendente.current);
    }

    const posicao = buckets.findIndex((b) => b.id === bucket.id);
    setBuckets(buckets.filter((b) => b.id !== bucket.id));

    const exclusao: ExclusaoPendente = {
      bucket,
      posicao,
      timer: setTimeout(() => {
        if (pendente.current === exclusao) {
          pendente.current = null;
          executarExclusao(exclusao);
        }
      }, TEMPO_DESFAZER_MS),
    };
    pendente.current = exclusao;

    mostrarAviso({ mensagem: "Bucket excluído", acao: "Desfazer", onAcao: () => desfazer(exclusao) }, TEMPO_DESFAZER_MS);
  }

  function desfazer(exclusao: ExclusaoPendente) {
    if (pendente.current !== exclusao) return;
    clearTimeout(exclusao.timer);
    pendente.current = null;
    setBuckets((atuais) => {
      const lista = [...atuais];
      lista.splice(Math.min(exclusao.posicao, lista.length), 0, exclusao.bucket);
      return lista;
    });
    fecharAviso();
  }

  // ---------- Busca e ordem ----------

  const termo = busca.trim().toLowerCase();
  const visiveis = buckets
    .filter((bucket) => (bucket.name ?? "").includes(termo))
    .sort((a, b) => {
      if (ordem === "nome") return (a.name ?? "").localeCompare(b.name ?? "");
      if (ordem === "arquivos") return (resumos[b.id ?? ""]?.total ?? 0) - (resumos[a.id ?? ""]?.total ?? 0);
      return (b.createdAt ?? "").localeCompare(a.createdAt ?? "");
    });

  const totalArquivos = Object.values(resumos).reduce((soma, r) => soma + r.total, 0);

  // Bucket em destaque: o escolhido agora, senão o último aberto, senão o primeiro
  const indiceSelecionado = visiveis.findIndex((b) => b.id === selecionadoId);
  const indiceUltimo = visiveis.findIndex((b) => b.id === ultimoBucket);
  const indiceAtivo = indiceSelecionado >= 0 ? indiceSelecionado : indiceUltimo >= 0 ? indiceUltimo : 0;
  const idEmDestaque = visiveis[indiceAtivo]?.id ?? "";

  // ---------- Tela ----------

  const renderBucketCard = (bucket: Bucket, posicao: number) => {
    return (
      <BucketCard
        key={bucket.id}
        id={bucket.id ?? ""}
        nome={bucket.name ?? ""}
        dataCriacao={formatarData(bucket.createdAt)}
        resumo={resumos[bucket.id ?? ""]}
        modo={modo}
        ordem={posicao}
        ativo={bucket.id === idEmDestaque}
        excluindo={bucket.status === "DELETING"}
        enviando={bucket.id === enviandoId}
        onAbrir={() => salvarUltimoBucket(bucket.id ?? "")}
        onFocar={() => setSelecionadoId(bucket.id ?? "")}
        onExcluir={() => excluirComDesfazer(bucket)}
        onSoltarArquivos={(arquivos) => enviarPara(bucket, arquivos)}
      />
    );
  };

  const renderEsqueleto = () => (
    <section className="flex items-center justify-center gap-6 py-10" aria-label="Carregando buckets">
      {[0, 1, 2].map((n) => (
        <div
          key={n}
          className={`esqueleto flex flex-col items-center gap-4 rounded-2xl bg-cartao p-6 ${n === 1 ? "h-80 w-64" : "hidden h-64 w-52 md:flex"}`}
        >
          <div className="h-16 w-16 rounded-xl bg-cartao-claro" />
          <div className="h-4 w-2/3 rounded bg-cartao-claro" />
          <div className="h-3 w-1/2 rounded bg-cartao-claro" />
        </div>
      ))}
    </section>
  );

  const renderConteudo = () => {
    if (estado === "carregando") return renderEsqueleto();
    if (estado === "erro") {
      return <Mensagem icone="nuvem" erro texto={mensagemErro} botao="Tentar de novo" onBotao={tentarDeNovo} />;
    }
    if (buckets.length === 0) {
      return <Mensagem icone="pasta" texto="Você ainda não tem buckets. Crie o primeiro." botao="Novo bucket" onBotao={abrirModal} />;
    }
    if (visiveis.length === 0) {
      return <Mensagem icone="busca" texto={`Nenhum bucket com "${busca}".`} botao="Limpar busca" onBotao={() => setBusca("")} />;
    }
    if (modo === "rotativo") {
      return (
        <div className="flex w-full flex-col items-center gap-6">
          <BucketCarrossel
            buckets={visiveis}
            resumos={resumos}
            indiceAtivo={indiceAtivo}
            enviandoId={enviandoId}
            onMudar={(indice) => setSelecionadoId(visiveis[indice]?.id ?? "")}
            onAbrir={(bucket) => salvarUltimoBucket(bucket.id ?? "")}
            onExcluir={excluirComDesfazer}
            onSoltarArquivos={enviarPara}
          />
          <button
            type="button"
            onClick={abrirModal}
            className="flex min-h-12 items-center gap-2 rounded-full border border-acento/50 bg-cartao/70 px-8 text-sm font-medium backdrop-blur transition-all duration-300 hover:border-acento hover:shadow-brilho-botao"
          >
            <Icone nome="mais" tamanho={18} className="text-acento" /> Criar novo bucket
          </button>
        </div>
      );
    }
    return (
      <section
        ref={grade}
        onKeyDown={navegarComSetas}
        aria-label="Buckets"
        className={
          modo === "grade"
            ? "grid grid-cols-1 gap-5 sm:grid-cols-2 md:grid-cols-3 lg:grid-cols-4 xl:grid-cols-5"
            : "flex flex-col gap-3"
        }
      >
        {visiveis.map(renderBucketCard)}
      </section>
    );
  };

  const botaoModo = (valor: ModoExibicao, rotulo: string, icone: NomeIcone) => (
    <button
      type="button"
      onClick={() => setModo(valor)}
      aria-pressed={modo === valor}
      className={`flex min-h-11 items-center gap-2 rounded-md px-3 text-sm transition-all duration-300
        ${modo === valor ? "bg-acento font-semibold text-fundo" : "text-texto-secundario hover:text-texto"}`}
    >
      <Icone nome={icone} tamanho={18} />
      <span className="hidden sm:inline">{rotulo}</span>
      <span className="sr-only sm:hidden">{rotulo}</span>
    </button>
  );

  return (
    <Template>
      {/* Título e ação principal */}
      <div className="mb-6 flex flex-wrap items-end justify-between gap-4">
        <div>
          <h1 className="text-3xl font-bold">Meus buckets</h1>
          <p className="mt-1 text-sm text-texto-secundario">
            {estado !== "conteudo"
              ? "Carregando…"
              : `${buckets.length} ${buckets.length === 1 ? "bucket" : "buckets"}` +
                (Object.keys(resumos).length ? ` · ${totalArquivos} ${totalArquivos === 1 ? "arquivo" : "arquivos"}` : "")}
          </p>
        </div>
        <div className="hidden md:block">
          <Botao icone="mais" onClick={abrirModal}>
            Novo bucket
          </Botao>
        </div>
      </div>

      {/* Barra de ferramentas: busca, ordem e modo */}
      {estado === "conteudo" && buckets.length > 0 && (
        <div className="mb-6 flex flex-col gap-3 md:flex-row md:items-center">
          <div className="relative flex-1">
            <Icone nome="busca" tamanho={18} className="pointer-events-none absolute left-4 top-1/2 -translate-y-1/2 text-texto-secundario" />
            <label htmlFor="busca-bucket" className="sr-only">
              Buscar bucket
            </label>
            <input
              ref={campoBusca}
              id="busca-bucket"
              type="search"
              value={busca}
              onChange={(event) => setBusca(event.target.value.toLowerCase())}
              placeholder="Buscar bucket"
              className="min-h-12 w-full rounded-lg border border-borda bg-cartao pl-11 pr-12 text-texto placeholder:text-texto-secundario/60 transition-all duration-300 focus:border-acento focus:outline-none"
            />
            <kbd className="pointer-events-none absolute right-3 top-1/2 hidden -translate-y-1/2 border border-borda px-2 text-xs text-texto-secundario md:block">
              /
            </kbd>
          </div>
          <div className="flex gap-3">
            <label htmlFor="ordem" className="sr-only">
              Ordenar por
            </label>
            <select
              id="ordem"
              value={ordem}
              onChange={(event) => setOrdem(event.target.value as Ordem)}
              className="min-h-12 flex-1 rounded-lg border border-borda bg-cartao px-4 text-texto focus:border-acento focus:outline-none md:flex-none"
            >
              <option value="recentes">Mais recentes</option>
              <option value="nome">Nome (A–Z)</option>
              <option value="arquivos">Mais arquivos</option>
            </select>
            <div className="flex gap-1 rounded-lg border border-borda bg-cartao p-1" role="group" aria-label="Modo de exibição">
              {botaoModo("rotativo", "Rotativo", "carrossel")}
              {botaoModo("grade", "Grade", "grade")}
              {botaoModo("lista", "Lista", "menu")}
            </div>
          </div>
        </div>
      )}

      {/* pb-20 no celular: espaço para o botão flutuante não cobrir o último cartão */}
      <div className="pb-20 md:pb-0">{renderConteudo()}</div>

      {estado === "conteudo" && buckets.length > 0 && (
        <p className="mt-6 hidden items-center gap-2 text-xs text-texto-secundario md:flex">
          <Icone nome="upload" tamanho={14} className="text-acento" />
          Dica: arraste arquivos do computador para cima de um bucket para enviar. Use as setas ← → para navegar, <kbd>/</kbd> para
          buscar e <kbd>N</kbd> para criar.
        </p>
      )}

      {/* Celular: botão flutuante */}
      <button
        type="button"
        onClick={abrirModal}
        aria-label="Novo bucket"
        className="fixed bottom-6 right-6 z-20 flex h-14 w-14 items-center justify-center rounded-full bg-acento text-fundo shadow-brilho-botao transition-transform duration-300 active:scale-95 md:hidden"
      >
        <Icone nome="mais" tamanho={28} />
      </button>

      <Modal titulo="Novo bucket" aberto={modalAberto} onFechar={() => setModalAberto(false)}>
        <form onSubmit={criarBucket} className="flex flex-col gap-6" noValidate>
          <Campo
            id="nome-bucket"
            rotulo="Nome do bucket"
            valor={nomeNovo}
            onChange={(valor) => setNomeNovo(valor.toLowerCase())}
            erro={erroNome}
            dica="Use letras minúsculas, números e hífen, de 3 a 63 caracteres."
            placeholder="ex.: documentos"
            maxLength={63}
          />
          <div className="flex justify-end gap-3">
            <Botao variante="secundario" onClick={() => setModalAberto(false)}>
              Cancelar
            </Botao>
            <Botao tipo="submit" desabilitado={criando}>
              {criando ? "Criando…" : "Criar"}
            </Botao>
          </div>
        </form>
      </Modal>

      {aviso && <Aviso {...aviso} />}
    </Template>
  );
}
