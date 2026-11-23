# =====================================================
# diagnostico_busca.py  -  DECODE
# =====================================================
#
# PARA QUE SERVE ESTE PROGRAMA
# ----------------------------
# Mostra QUAIS itens da base de conhecimento o banco
# vetorial encontra para cada pergunta, sem usar a Groq.
# Serve para descobrir se um erro de resposta vem da
# BUSCA (o item certo não foi encontrado) ou do MODELO
# de linguagem (o item foi encontrado, mas mal usado).
#
# COMO LER O RESULTADO
# --------------------
# Cada linha mostra: distância | número e título do item.
# Quanto MENOR a distância, mais parecido o item é com a
# pergunta. A primeira linha é a melhor.
#
# COMO EXECUTAR (pasta decode_chatbot, com o ambiente "decode" ativo)
# -------------------------------------------------------------------
#   python diagnostico_busca.py

# Expressões regulares: usadas para achar o título do item
import re

# Mesmo modelo de embeddings do banco_vetorial.py e do app.py
from langchain_huggingface import HuggingFaceEmbeddings

# Banco vetorial
from langchain_chroma import Chroma


# =====================================================
# Abrindo o banco vetorial (somente leitura)
# =====================================================

embeddings = HuggingFaceEmbeddings(
    model_name="sentence-transformers/paraphrase-multilingual-MiniLM-L12-v2"
)

# collection_name e persist_directory iguais ao banco_vetorial.py
vectorstore = Chroma(
    collection_name="base_decode",
    embedding_function=embeddings,
    persist_directory="./banco_chroma"
)


# =====================================================
# Perguntas para testar
# =====================================================

# A primeira é a que falhou no teste.py (pergunta curta).
# As outras mostram como a busca se comporta com perguntas
# completas e com outros assuntos.
perguntas = [
    "E quais as tecnologias?",
    "Quais tecnologias o DECODE usa?",
    "tecnologias do DECODE",
    "Quem faz parte da equipe?",
    "Como funciona o deploy?",
    "O DECODE já tem OCR?",
    "Existe administrador no DECODE?",
    "Posso compartilhar arquivos com outras pessoas?",
    "Qual é o tamanho máximo de um arquivo?",
]


# =====================================================
# Buscando e mostrando os 5 itens mais parecidos
# =====================================================

for pergunta in perguntas:

    print()
    print("PERGUNTA:", pergunta)

    # similarity_search_with_score devolve (documento, distância)
    # k=8 -> mostra 8 itens (o app.py usa os 5 primeiros)
    resultados = vectorstore.similarity_search_with_score(
        pergunta,
        k=8
    )

    for posicao, (documento, distancia) in enumerate(resultados, start=1):

        # Procura o título do item, no formato "12. TÍTULO EM MAIÚSCULAS"
        titulo = re.search(r"\d\d\. [^\n]+", documento.page_content)

        if titulo:
            texto_titulo = titulo.group(0)
        else:
            texto_titulo = "(título não encontrado)"

        # O app.py só usa os 5 primeiros; marcamos com * quem entra
        marca = "*" if posicao <= 5 else " "

        print(f"  {marca} {distancia:.3f} | {texto_titulo}")

print()
print("Itens marcados com * são os que o app.py entrega ao modelo (k=5).")
