# =====================================================
# app.py  -  API do DECODE (FastAPI)
# =====================================================
#
# PARA QUE SERVE ESTE PROGRAMA
# ----------------------------
# É o "cérebro" do chatbot. Ele cria uma API web com o
# endpoint POST /chat. Quem chama esse endpoint (o bot do
# Telegram ou o teste.py) envia:
#
#   - a pergunta do usuário;
#   - o histórico da conversa (mensagens anteriores).
#
# A API então:
#   1. decide a ROTA da pergunta (RAG ou NORMAL);
#   2. chama o agente correspondente, já com o histórico;
#   3. devolve a rota usada e a resposta.
#
# ONDE FICA O HISTÓRICO?
# ----------------------
# No bot (dicionário "historicos", como na aula 6.2).
# A API NÃO guarda nada: a cada chamada ela recebe o
# histórico pronto. Assim, se a API reiniciar, nenhuma
# conversa é perdida pela API.
#
# COMO EXECUTAR (pasta ~/decode-chatbot, com a .venv ativa)
# ----------------------------------------------------
#   uvicorn app:app --host 0.0.0.0 --port 8000
#
# Antes disso, o banco_vetorial.py precisa ter sido
# executado (ele cria a pasta ./banco_chroma).

# Lê o arquivo .env e coloca as chaves nas variáveis de ambiente
from dotenv import load_dotenv

# Cria a API web
from fastapi import FastAPI

# Define o formato dos dados que a API recebe
# (Field permite colocar LIMITES: tamanho máximo dos textos e da lista)
from pydantic import BaseModel, Field

# Modelo de linguagem da Groq
from langchain_groq import ChatGroq

# Modelo de embeddings (o MESMO usado no banco_vetorial.py)
from langchain_huggingface import HuggingFaceEmbeddings

# Banco vetorial
from langchain_chroma import Chroma

# Decorador que transforma uma função em "ferramenta" do agente
from langchain_core.tools import tool

# Função que cria agentes
from langchain.agents import create_agent


# =====================================================
# Variáveis de ambiente
# =====================================================

# Lê o arquivo .env desta pasta. A partir daqui o
# ChatGroq encontra sozinho a GROQ_API_KEY.
load_dotenv()

print(
    "Arquivo .env carregado."
)


# =====================================================
# Configurações do modelo
# =====================================================

# temperature=0   -> sem criatividade: a resposta fica mais estável e
#                    o modelo tem menos espaço para inventar (achado F19)
# max_tokens=1000 -> LIMITE de tamanho da resposta (achado F16).
#                    Este modelo "raciocina" antes de responder e
#                    esse raciocínio também conta nos tokens; por
#                    isso o valor não é tão pequeno. Se as respostas
#                    saírem cortadas, aumente; se o consumo da Groq
#                    preocupar, diminua.
# timeout=30      -> desiste se a Groq não responder em 30 segundos,
#                    em vez de ficar esperando para sempre
# max_retries=1   -> tenta de novo só 1 vez se der erro de rede
llm = ChatGroq(
    model="openai/gpt-oss-20b",
    temperature=0,
    max_tokens=1000,
    timeout=30,
    max_retries=1
)

print(
    "Modelo de linguagem carregado."
)


# =====================================================
# Banco vetorial (somente leitura)
# =====================================================

# O modelo de embeddings PRECISA ser o mesmo do banco_vetorial.py,
# senão as buscas não encontram os textos certos.
embeddings = HuggingFaceEmbeddings(
    model_name="sentence-transformers/paraphrase-multilingual-MiniLM-L12-v2"
)

# Aqui NÃO criamos o banco: apenas ABRIMOS o que já existe.
# collection_name e persist_directory iguais ao banco_vetorial.py.
vectorstore = Chroma(
    collection_name="base_decode",
    embedding_function=embeddings,
    persist_directory="./banco_chroma"
)

print(
    "Banco vetorial carregado."
)


# =====================================================
# Ferramenta de consulta à base de conhecimento
# =====================================================

# O agente lê a descrição (docstring) abaixo para decidir
# QUANDO usar a ferramenta. Por isso ela é importante.
@tool
def consultar_base_decode(pergunta: str) -> str:
    """
    Consulta a base de conhecimento do projeto DECODE.

    Utilize esta ferramenta quando a pergunta depender
    de informações específicas do projeto, como objetivo,
    equipe, estado atual, arquitetura, tecnologias,
    segurança, buckets e arquivos, site e aplicativo,
    como executar, testes, CI/CD, implantação na AWS,
    fluxo de trabalho com Git, roteiro da apresentação
    ou funcionamento deste chatbot no Telegram.
    """

    # Busca os 5 chunks mais parecidos com a pergunta.
    # (Eram 3. Com a base maior, o item certo às vezes ficava em
    # 4º lugar, por uma diferença mínima; com 5 ele não se perde.)
    documentos = vectorstore.similarity_search(
        pergunta,
        k=5
    )

    # Se não veio nada, avisa o agente
    if not documentos:
        return "Nenhuma informação encontrada."

    # Junta o texto dos chunks em uma lista
    contexto = []
    for documento in documentos:
        contexto.append(documento.page_content)

    # Devolve os chunks separados por uma linha em branco
    return "\n\n".join(contexto)


# =====================================================
# Agente com RAG (usa a ferramenta)
# =====================================================

agente_rag = create_agent(

    model=llm,

    tools=[
        consultar_base_decode
    ],

    system_prompt="""
    Você é o assistente virtual do projeto DECODE.

    O DECODE é uma plataforma SaaS de gestão inteligente
    de documentos não estruturados, desenvolvida como
    Projeto Integrador do SENAI-SP.

    Você possui acesso a uma base de conhecimento
    do projeto através da ferramenta:

    consultar_base_decode

    REGRAS OBRIGATÓRIAS (achado F19):

    1. Para QUALQUER pergunta sobre o DECODE, inclusive
       sobre benefícios, vantagens, "por que usar" ou
       comparação com outros serviços, chame a
       ferramenta ANTES de responder.

    2. Responda SOMENTE com o que estiver nos trechos
       devolvidos pela ferramenta. NÃO complete a
       resposta com conhecimento geral sobre sistemas
       de arquivos, nuvem ou outros produtos.

    3. Só diga que o DECODE tem um recurso se os trechos
       o descreverem como existente. Recursos comuns em
       serviços de armazenamento (busca por palavra-chave,
       backup automático, compartilhamento, administrador,
       controle de acesso granular, planos e preços) só
       podem ser citados como existentes se os trechos
       disserem isso. Se não disserem, não os mencione
       como funcionalidade do DECODE.

    4. Em perguntas sobre benefícios ou "por que usar",
       liste apenas o que a versão 1 já faz e, em uma
       frase, o que ainda está previsto.

    5. Se a informação não estiver nos trechos, diga que
       não consta e oriente o usuário a consultar
       a equipe do projeto DECODE.

    Use o histórico da conversa para entender
    perguntas curtas que sejam continuação
    do assunto anterior.

    Não invente informações.

    Diferencie o que JÁ EXISTE do que é PLANEJADO
    ou PREVISTO. Nunca apresente algo planejado
    (por exemplo, Amazon S3, OCR, busca avançada e
    chat com IA sobre os arquivos) como se já
    estivesse funcionando.

    Formato da resposta (ela será lida no Telegram):
    - NÃO use tabelas;
    - use frases curtas ou listas simples, com
      "- " no início de cada item;
    - use **negrito** só para destacar nomes ou
      termos importantes;
    - NÃO use itálico com asteriscos simples,
      nem crases, nem títulos com #.

    Responda em português.
    Seja claro e objetivo.
    """
)

print(
    "Agente RAG criado."
)


# =====================================================
# Agente normal (sem ferramentas)
# =====================================================

agente_normal = create_agent(

    model=llm,

    tools=[],

    system_prompt="""
    Você é o assistente virtual do projeto DECODE.

    Responda normalmente às perguntas do usuário,
    como cumprimentos e conceitos gerais de
    tecnologia.

    Se perguntarem o que você sabe responder, diga
    que você responde dúvidas sobre o projeto DECODE:
    objetivo, equipe, arquitetura, segurança, como
    rodar, testes, implantação e apresentação.

    Use o histórico da conversa para manter o contexto.

    Se perguntarem algo sobre o DECODE que você não
    sabe (por exemplo, telefone, e-mail ou outro
    contato), diga que essa informação não consta
    na base de conhecimento e oriente a consultar a
    equipe do projeto DECODE.

    Não use tabelas, itálico com asteriscos simples
    nem crases; use **negrito** só se precisar.

    Responda em português.
    Seja claro e objetivo.

    Não invente informações sobre o DECODE.
    """
)

print(
    "Agente normal criado."
)


# =====================================================
# Funções auxiliares do histórico
# =====================================================

# O histórico chega da requisição como lista de mensagens
# (papel + texto). Esta função monta a lista no formato
# que os agentes entendem: [{"role": ..., "content": ...}].
def montar_mensagens(
    pergunta,
    historico
):

    # Começa com uma lista vazia
    mensagens = []

    # Copia cada mensagem do histórico, aceitando somente
    # os papéis "user" (usuário) e "assistant" (IA).
    # Qualquer outro papel (ex.: "system") é ignorado.
    for mensagem in historico:
        if mensagem.role in ["user", "assistant"]:
            mensagens.append({
                "role": mensagem.role,
                "content": mensagem.content
            })

    # Por último, entra a pergunta atual do usuário
    mensagens.append({
        "role": "user",
        "content": pergunta
    })

    return mensagens


# O roteador recebe o histórico como TEXTO simples, só
# com as últimas mensagens, para entender perguntas curtas
# como "e qual o prazo?".
def historico_em_texto(
    historico
):

    texto = ""

    # [-4:] pega apenas as 4 últimas mensagens
    for mensagem in historico[-4:]:
        # Mesma regra de segurança: só "user" e "assistant"
        if mensagem.role in ["user", "assistant"]:
            texto = texto + f"{mensagem.role}: {mensagem.content}\n"

    # Se não há histórico, informa isso
    if texto == "":
        texto = "(sem histórico)"

    return texto


# =====================================================
# Roteador: decide RAG ou NORMAL
# =====================================================

def decidir_rota(
    pergunta,
    historico
):

    prompt = f"""
    Você é um roteador de perguntas.

    Analise a pergunta do usuário e decida
    se ela precisa consultar a base de conhecimento
    específica do projeto DECODE.

    Responda SOMENTE com uma destas opções:

    RAG

    ou

    NORMAL

    Use RAG quando a pergunta depender de informações
    específicas do DECODE, como:

    - o que é o projeto, objetivo e contexto acadêmico;
    - equipe, responsabilidades e prazos;
    - estado atual, versão 1 e versão 2.0 (OCR e IA);
    - arquitetura, microsserviços e banco de dados;
    - tecnologias e stack (back-end, site e app);
    - segurança, JWT, senhas, tokens e LGPD;
    - buckets, upload, download, limites e SHA-256;
    - site web e aplicativo Android;
    - como instalar, executar e testar o projeto;
    - CI/CD, deploy e infraestrutura na AWS;
    - fluxo de trabalho com Git, commits e Pull Requests;
    - roteiro e tempo da apresentação final;
    - como funciona este chatbot, o bot do Telegram e os
      bots de staging e de produção;
    - limitações e melhorias previstas;
    - contatos, valores, custos, notas ou endereço
      público do site do DECODE (mesmo que a base
      possa não ter a informação).

    Este assistente atende SOMENTE o projeto DECODE.
    Por isso, perguntas sobre arquivos, buckets, tamanho
    de arquivo, upload, download, compartilhamento,
    contas, login, administrador, equipe, tecnologias,
    recursos, versões ou custos são sobre o DECODE
    MESMO QUE a pergunta não cite o nome DECODE
    (exemplos: "Qual é o tamanho máximo de um arquivo?",
    "Posso compartilhar arquivos com outras pessoas?",
    "Existe administrador?"). Nesses casos use RAG.

    Use NORMAL somente para cumprimentos, agradecimentos
    e conversas gerais sem relação com o projeto
    (exemplos: "Oi", "Obrigado", "Tudo bem?").

    Na dúvida entre RAG e NORMAL, use RAG.

    Se a pergunta for curta e continuar um assunto
    do DECODE tratado no histórico, use RAG.

    Histórico recente:

    {historico_em_texto(historico)}

    Pergunta:

    {pergunta}
    """

    # Chama o modelo diretamente (sem agente) e lê o texto
    resposta = llm.invoke(prompt)

    # strip() remove espaços e quebras de linha
    # upper() deixa tudo em maiúsculas
    rota = resposta.content.strip().upper()

    # Se o modelo responder algo inesperado, usa NORMAL
    if rota not in ["RAG", "NORMAL"]:
        rota = 'NORMAL'

    return rota


# =====================================================
# Roteamento entre os agentes
# =====================================================

def rotear_agente(
    pergunta,
    historico
):

    # 1. Decide a rota
    rota = decidir_rota(
        pergunta,
        historico
    )

    # 2. Monta as mensagens (histórico + pergunta atual)
    mensagens = montar_mensagens(
        pergunta,
        historico
    )

    # 3. Chama o agente da rota escolhida
    if rota == "RAG":
        resultado = agente_rag.invoke(
            {"messages": mensagens}
        )
    else:
        resultado = agente_normal.invoke(
            {"messages": mensagens}
        )

    # 4. A resposta final é a ÚLTIMA mensagem da lista
    resposta = resultado["messages"][-1]

    return rota, resposta.content


# =====================================================
# Modelos da requisição
# =====================================================

# LIMITES da requisição (achado F16 do adendo de segurança).
# Se alguém enviar algo maior, a API recusa com erro 422
# ANTES de chamar o modelo, então não gasta a cota da Groq.
#   - pergunta: até 2.000 caracteres (o Telegram aceita 4.096);
#   - cada mensagem do histórico: até 4.000 caracteres;
#   - histórico: até 30 mensagens (o bot envia no máximo 21:
#     a 1ª mensagem + as 20 mais recentes).
TAMANHO_MAXIMO_PERGUNTA = 2000
TAMANHO_MAXIMO_MENSAGEM = 4000
ITENS_MAXIMOS_HISTORICO = 30


# Uma mensagem do histórico:
#   role    -> "user" (usuário) ou "assistant" (IA)
#   content -> texto da mensagem (com tamanho máximo)
class Mensagem(BaseModel):

    role: str

    content: str = Field(max_length=TAMANHO_MAXIMO_MENSAGEM)


# Corpo do POST /chat:
#   pergunta  -> obrigatória (com tamanho máximo)
#   historico -> opcional (se não vier, é uma lista vazia),
#                com no máximo 30 mensagens
class Pergunta(BaseModel):

    pergunta: str = Field(max_length=TAMANHO_MAXIMO_PERGUNTA)

    historico: list[Mensagem] = Field(
        default=[],
        max_length=ITENS_MAXIMOS_HISTORICO
    )


# =====================================================
# API FastAPI
# =====================================================

app = FastAPI(

    title="DECODE API",

    description="API de atendimento do projeto DECODE"
)

print(
    "API criada."
)


# =====================================================
# Rota inicial
# =====================================================

# Serve para conferir se a API está no ar:
# abra http://IP_DA_EC2:8000 no navegador.
@app.get("/")
def inicio():

    return {
        "mensagem": "API do DECODE Funcionando"
    }


# =====================================================
# Endpoint de atendimento
# =====================================================

@app.post("/chat")
def conversar(
    dados: Pergunta
):

    rota, resposta = rotear_agente(
        dados.pergunta,
        dados.historico
    )

    return {

        "pergunta": dados.pergunta,

        "rota": rota,

        "resposta": resposta

    }
