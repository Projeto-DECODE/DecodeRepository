# =====================================================
# banco_vetorial.py  -  DECODE
# =====================================================
#
# PARA QUE SERVE ESTE PROGRAMA
# ----------------------------
# Ele lê o PDF da base de conhecimento do DECODE,
# corta o texto em pedaços (chunks), transforma cada
# pedaço em números (embeddings) e guarda tudo em um
# banco vetorial (Chroma) dentro da pasta ./banco_chroma.
#
# QUANDO EXECUTAR
# ---------------
# - UMA vez, depois de instalar as bibliotecas;
# - novamente SOMENTE se o PDF mudar. Antes de rodar de
#   novo, apague a pasta banco_chroma (rm -rf banco_chroma),
#   senão os pedaços ficam duplicados no banco.
#
# COMO EXECUTAR (dentro da pasta ~/decode-chatbot, com a .venv ativa)
# ----------------------------------------------------------------
#   python banco_vetorial.py
#
# IMPORTANTE: o PDF precisa estar na MESMA pasta deste
# arquivo, pois o caminho abaixo é relativo.

# Lê arquivos PDF e devolve uma lista de páginas (Document)
from langchain_community.document_loaders import PyPDFLoader

# Corta textos grandes em pedaços menores (chunks)
from langchain_text_splitters import RecursiveCharacterTextSplitter

# Modelo que transforma texto em vetores numéricos (embeddings)
from langchain_huggingface import HuggingFaceEmbeddings

# Banco de dados vetorial onde os vetores ficam guardados
from langchain_chroma import Chroma


# =====================================================
# Carregando o PDF
# =====================================================

# Nome do arquivo PDF (precisa estar na mesma pasta)
pdf_path = 'DECODE_Base_Conhecimento.pdf'

# Cria o "leitor" do PDF
loader = PyPDFLoader(
    pdf_path
)

# Lê o PDF: devolve uma lista com UMA entrada por página
documentos = loader.load()

# Mostra quantas páginas foram lidas (esperado: 17)
print(
    f"Páginas carregadas: {len(documentos)}"
)


# =====================================================
# Dividindo o documento
# =====================================================

# Cada item da base termina com uma linha "---".
# Usamos esse separador para que CADA ITEM vire UM chunk.
#   chunk_size=600  -> tamanho máximo aproximado de um chunk
#   chunk_overlap=0 -> não repete texto entre chunks vizinhos
#   separators      -> onde o texto deve ser cortado
text_splitter = RecursiveCharacterTextSplitter(
    chunk_size=600,
    chunk_overlap=0,
    separators=["\n---"]
)

# Aplica o corte em todas as páginas
chunks = text_splitter.split_documents(
    documentos
)

# Mostra quantos chunks foram criados (esperado: 66,
# um para cada item da base de conhecimento)
print(
    "Quantidade de chunks:",
    len(chunks)
)

# Mostra o tamanho (em caracteres) de cada chunk
for i, chunk in enumerate(chunks):
    print(
        f"Chunk {i + 1}: {len(chunk.page_content)} caracteres"
    )


# =====================================================
# Modelo de embeddings
# =====================================================
#
# MODELO ESCOLHIDO: paraphrase-multilingual-MiniLM-L12-v2, a opção multilíngue
# que o professor deixa comentada no 9.1. O all-mpnet-base-v2 (aula) é treinado
# principalmente em inglês e, no teste real, NÃO achou o item certo para perguntas
# curtas em português (ex.: "E quais as tecnologias?").
#
# Na primeira execução o modelo é BAIXADO da internet
# (cerca de 500 MB). Isso demora alguns minutos; nas
# próximas execuções ele já estará no cache da máquina.
#
embeddings = HuggingFaceEmbeddings(
    model_name="sentence-transformers/paraphrase-multilingual-MiniLM-L12-v2"
)

print(
    "Modelo de embeddings carregado."
)


# =====================================================
# Banco vetorial
# =====================================================

# Calcula o embedding de cada chunk e grava no Chroma.
#   documents         -> os chunks criados acima
#   embedding         -> o modelo que gera os vetores
#   collection_name   -> nome da "tabela" dentro do banco
#                        (o app.py precisa usar o MESMO nome)
#   persist_directory -> pasta onde o banco é salvo no disco
vectorstore = Chroma.from_documents(
    documents=chunks,
    embedding=embeddings,
    collection_name="base_decode",
    persist_directory="./banco_chroma"
)

print(
    "Banco vetorial criado."
)
