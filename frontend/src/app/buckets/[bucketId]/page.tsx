"use client";

import Link from "next/link";
import { useParams } from "next/navigation";
import { useEffect, useState } from "react";
import {
  AreaUpload,
  ArquivoCard,
  Aviso,
  Botao,
  Carregando,
  Icone,
  Mensagem,
  Modal,
  PainelDetalhe,
  Template,
} from "@/components";
import { Arquivo, LIMITE_UPLOAD_BYTES, Pagina, useArquivoService, useBucketService } from "@/resources";
import { formatarData, formatarTamanho, TipoArquivo, tipoDoArquivo } from "@/utils/formatos";
import { salvarUltimoBucket, useAviso, useProtegerPagina, useTratarErro } from "@/utils/hooks";

type Estado = "carregando" | "conteudo" | "erro";
type FiltroTipo = "" | TipoArquivo;

export default function ArquivosPage() {
  useProtegerPagina();
  const { bucketId } = useParams<{ bucketId: string }>();
  const bucketService = useBucketService();
  const arquivoService = useArquivoService();
  const tratarErro = useTratarErro();
  const [aviso, mostrarAviso] = useAviso();

  const [nomeBucket, setNomeBucket] = useState<string>("");
  const [arquivos, setArquivos] = useState<Arquivo[]>([]);
  const [total, setTotal] = useState<number>(0);
  const [pagina, setPagina] = useState<number>(0);
  const [totalPaginas, setTotalPaginas] = useState<number>(0);
  const [estado, setEstado] = useState<Estado>("carregando");
  const [mensagemErro, setMensagemErro] = useState<string>("");
  const [carregandoMais, setCarregandoMais] = useState<boolean>(false);
  const [enviando, setEnviando] = useState<boolean>(false);

  // Pesquisa: o que está digitado e o filtro aplicado ao clicar em "Pesquisar"
  const [query, setQuery] = useState<string>("");
  const [tipo, setTipo] = useState<FiltroTipo>("");
  const [filtro, setFiltro] = useState<{ query: string; tipo: FiltroTipo }>({ query: "", tipo: "" });

  const [selecionado, setSelecionado] = useState<Arquivo | null>(null);
  const [baixando, setBaixando] = useState<boolean>(false);
  const [paraExcluir, setParaExcluir] = useState<Arquivo | null>(null);
  const [excluindo, setExcluindo] = useState<boolean>(false);

  // ---------- Carregar ----------

  function buscarPagina(numero: number) {
    arquivoService
      .listar(bucketId, numero)
      .then((resultado) => aplicarPagina(numero, resultado))
      .catch((erro) => aplicarErro(numero, erro));
  }

  function aplicarPagina(numero: number, resultado: Pagina<Arquivo>) {
    const conteudo = resultado.content ?? [];
    setArquivos((atuais) => (numero === 0 ? conteudo : [...atuais, ...conteudo]));
    setPagina(numero);
    setTotal(resultado.totalElements ?? 0);
    setTotalPaginas(resultado.totalPages ?? 0);
    setEstado("conteudo");
    setCarregandoMais(false);
  }

  function aplicarErro(numero: number, erro: unknown) {
    setCarregandoMais(false);
    const mensagem = tratarErro(erro);
    if (!mensagem) return;
    if (numero === 0) {
      setMensagemErro(mensagem);
      setEstado("erro");
    } else {
      mostrarAviso({ mensagem, tipo: "erro" });
    }
  }

  // Volta para a página 0 (depois de enviar ou excluir)
  function recarregar() {
    setEstado("carregando");
    buscarPagina(0);
  }

  function carregarMais() {
    setCarregandoMais(true);
    buscarPagina(pagina + 1);
  }

  useEffect(() => {
    salvarUltimoBucket(bucketId);
    bucketService
      .buscar(bucketId)
      .then((bucket) => setNomeBucket(bucket.name ?? ""))
      .catch(() => undefined); // se falhar, a lista mostra o erro
    arquivoService
      .listar(bucketId, 0)
      .then((resultado) => aplicarPagina(0, resultado))
      .catch((erro) => aplicarErro(0, erro));
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [bucketId]);

  // ---------- Enviar ----------

  async function enviarArquivos(lista: File[]) {
    setEnviando(true);
    let enviados = 0;
    for (const arquivo of lista) {
      if (arquivo.size === 0) {
        mostrarAviso({ mensagem: `${arquivo.name}: o arquivo está vazio.`, tipo: "erro" });
        continue;
      }
      if (arquivo.size > LIMITE_UPLOAD_BYTES) {
        mostrarAviso({ mensagem: `${arquivo.name}: arquivo maior que 10 MB.`, tipo: "erro" });
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
    setEnviando(false);
    if (enviados > 0) {
      mostrarAviso({ mensagem: enviados === 1 ? "Arquivo enviado" : `${enviados} arquivos enviados`, tipo: "sucesso" });
      recarregar();
    }
  }

  // ---------- Baixar ----------

  async function baixar(arquivo: Arquivo) {
    setBaixando(true);
    try {
      const blob = await arquivoService.baixar(bucketId, arquivo.id ?? "");
      const url = URL.createObjectURL(blob);
      const link = document.createElement("a");
      link.href = url;
      link.download = arquivo.originalFilename ?? "arquivo";
      link.click();
      URL.revokeObjectURL(url);
    } catch (erro) {
      const mensagem = tratarErro(erro);
      if (mensagem) mostrarAviso({ mensagem: "Não foi possível baixar o arquivo.", tipo: "erro" });
    }
    setBaixando(false);
  }

  // ---------- Excluir ----------

  async function confirmarExclusao() {
    if (!paraExcluir) return;
    setExcluindo(true);
    try {
      await arquivoService.excluir(bucketId, paraExcluir.id ?? "");
      if (selecionado?.id === paraExcluir.id) setSelecionado(null);
      setParaExcluir(null);
      mostrarAviso({ mensagem: "Arquivo excluído", tipo: "sucesso" });
      recarregar();
    } catch (erro) {
      const mensagem = tratarErro(erro);
      if (mensagem) mostrarAviso({ mensagem, tipo: "erro" });
    }
    setExcluindo(false);
  }

  // ---------- Pesquisa (nos arquivos já carregados) ----------

  function pesquisar() {
    setFiltro({ query: query.trim().toLowerCase(), tipo });
  }

  const arquivosFiltrados = arquivos.filter((arquivo) => {
    const nome = (arquivo.originalFilename ?? "").toLowerCase();
    const passaNome = !filtro.query || nome.includes(filtro.query);
    const passaTipo = !filtro.tipo || tipoDoArquivo(arquivo.contentType, arquivo.originalFilename) === filtro.tipo;
    return passaNome && passaTipo;
  });
  const pesquisando = filtro.query !== "" || filtro.tipo !== "";

  // ---------- Tela ----------

  const renderArquivoCard = (arquivo: Arquivo) => {
    return (
      <ArquivoCard
        key={arquivo.id}
        nome={arquivo.originalFilename ?? ""}
        tamanho={formatarTamanho(arquivo.sizeBytes)}
        dataUpload={formatarData(arquivo.createdAt)}
        tipo={tipoDoArquivo(arquivo.contentType, arquivo.originalFilename)}
        selecionado={selecionado?.id === arquivo.id}
        onAbrir={() => setSelecionado(arquivo)}
        onExcluir={() => setParaExcluir(arquivo)}
      />
    );
  };

  const renderLista = () => {
    if (estado === "carregando") return <Carregando />;
    if (estado === "erro") {
      return <Mensagem icone="nuvem" erro texto={mensagemErro} botao="Tentar de novo" onBotao={recarregar} />;
    }
    if (arquivos.length === 0) {
      return <Mensagem icone="arquivo" texto="Nenhum arquivo neste bucket. Envie o primeiro na área acima." />;
    }
    if (arquivosFiltrados.length === 0) {
      return <Mensagem icone="busca" texto="Nenhum arquivo encontrado com esses filtros." />;
    }
    return (
      <>
        <section className="grid grid-cols-1 gap-3 md:grid-cols-2 xl:grid-cols-3">{arquivosFiltrados.map(renderArquivoCard)}</section>
        {pagina + 1 < totalPaginas && (
          <div className="mt-6 flex flex-col items-center gap-2">
            {pesquisando && (
              <p className="text-sm text-texto-secundario">A pesquisa considera só os arquivos já carregados.</p>
            )}
            <Botao variante="secundario" desabilitado={carregandoMais} onClick={carregarMais}>
              {carregandoMais ? "Carregando…" : "Carregar mais"}
            </Botao>
          </div>
        )}
      </>
    );
  };

  return (
    <Template>
      <nav aria-label="Caminho" className="mb-3 flex items-center gap-2 text-sm text-texto-secundario">
        <Link href="/buckets" className="flex min-h-11 items-center gap-2 hover:text-acento">
          <Icone nome="voltar" tamanho={16} /> Meus buckets
        </Link>
        <span aria-hidden="true">/</span>
        <span className="truncate text-acento">{nomeBucket || "…"}</span>
      </nav>

      <div className="mb-6">
        <h1 className="break-all text-3xl font-bold">{nomeBucket || "Arquivos"}</h1>
        {estado === "conteudo" && (
          <p className="mt-1 text-sm text-texto-secundario">
            {total} {total === 1 ? "arquivo" : "arquivos"}
          </p>
        )}
      </div>

      <div className="mb-6">
        <AreaUpload enviando={enviando} onArquivos={enviarArquivos} />
      </div>

      {/* Barra de pesquisa */}
      <section className="mb-6 flex flex-col gap-3 md:flex-row">
        <label htmlFor="pesquisa" className="sr-only">
          Pesquisar por nome
        </label>
        <input
          id="pesquisa"
          type="text"
          placeholder="Pesquisar por nome"
          value={query}
          onChange={(event) => setQuery(event.target.value)}
          onKeyDown={(event) => event.key === "Enter" && pesquisar()}
          className="min-h-12 flex-1 rounded-md border border-borda bg-cartao px-4 text-texto placeholder:text-texto-secundario/60 transition-all duration-300 focus:border-acento focus:shadow-[0_0_0_3px_rgb(57_255_20/0.15)] focus:outline-none"
        />
        <label htmlFor="tipo" className="sr-only">
          Tipo de arquivo
        </label>
        <select
          id="tipo"
          value={tipo}
          onChange={(event) => setTipo(event.target.value as FiltroTipo)}
          className="min-h-12 rounded-md border border-borda bg-cartao px-4 text-texto transition-all duration-300 focus:border-acento focus:outline-none"
        >
          <option value="">Todos os tipos</option>
          <option value="imagem">Imagens</option>
          <option value="pdf">PDF</option>
          <option value="planilha">Planilhas</option>
          <option value="outro">Outros</option>
        </select>
        <Botao icone="busca" onClick={pesquisar}>
          Pesquisar
        </Botao>
      </section>

      {renderLista()}

      {selecionado && (
        <PainelDetalhe
          key={selecionado.id}
          arquivo={selecionado}
          nomeBucket={nomeBucket}
          baixando={baixando}
          onBaixar={() => baixar(selecionado)}
          onExcluir={() => setParaExcluir(selecionado)}
          onFechar={() => setSelecionado(null)}
          onErro={(erro) => tratarErro(erro)}
        />
      )}

      <Modal titulo="Excluir arquivo?" aberto={paraExcluir !== null} onFechar={() => setParaExcluir(null)}>
        <p className="mb-6 break-all text-texto-secundario">
          <span className="text-texto">{paraExcluir?.originalFilename}</span> será excluído permanentemente.
        </p>
        <div className="flex justify-end gap-3">
          <Botao variante="secundario" onClick={() => setParaExcluir(null)}>
            Cancelar
          </Botao>
          <Botao variante="perigo" icone="lixeira" desabilitado={excluindo} onClick={confirmarExclusao}>
            {excluindo ? "Excluindo…" : "Excluir"}
          </Botao>
        </div>
      </Modal>

      {aviso && <Aviso {...aviso} />}
    </Template>
  );
}
