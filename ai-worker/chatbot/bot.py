# =====================================================
# bot.py  -  Bot do Telegram do DECODE
# =====================================================
#
# PARA QUE SERVE ESTE PROGRAMA
# ----------------------------
# É a "porta de entrada" do usuário. Ele recebe as
# mensagens no Telegram e, para cada uma:
#
#   1. guarda e consulta o HISTÓRICO da conversa
#      (dicionário "historicos", um para cada chat_id,
#      como na aula 6.2: 1ª mensagem + 20 mais recentes);
#   2. chama a API (POST /chat) enviando pergunta + histórico;
#   3. envia a rota e a resposta de volta no Telegram;
#   4. atualiza o histórico.
#
# O bot NÃO usa a Groq diretamente: quem fala com o
# modelo é o app.py. Por isso a API (app.py) precisa
# estar ligada antes do bot.
#
# COMO EXECUTAR (pasta ~/decode-chatbot, com a .venv ativa)
# ----------------------------------------------------
#   python bot.py
#
# ATENÇÃO: cada token do BotFather aceita UM bot ligado
# por vez. Se o mesmo token estiver rodando em outro lugar
# (Colab, outro terminal...), aparece o erro 409 Conflict.

# Lê o arquivo .env e coloca os valores nas variáveis de ambiente
from dotenv import load_dotenv

# Acessa as variáveis de ambiente (os.getenv)
import os

# Expressões regulares (usadas para formatar o negrito)
import re

# Biblioteca do Telegram
import telebot

# Faz as chamadas HTTP para a API
import requests

# Tipos de mensagem que guardam o histórico
from langchain_core.messages import AIMessage, HumanMessage


# =====================================================
# Variáveis de ambiente
# =====================================================

# Lê o arquivo .env desta pasta
load_dotenv()

# Token do bot (vem do BotFather)
TELEGRAM_TOKEN = os.getenv("TELEGRAM_TOKEN")

# Endereço da API (no .env: http://localhost:8000)
API_URL = os.getenv("API_URL")

# Se faltar alguma variável no .env, para aqui com
# uma mensagem clara, em vez de um erro difícil de entender.
if TELEGRAM_TOKEN is None:
    raise ValueError("TELEGRAM_TOKEN não encontrado no arquivo .env")

if API_URL is None:
    raise ValueError("API_URL não encontrada no arquivo .env")

print(
    "Variáveis do .env carregadas."
)


# =====================================================
# Configurações do bot
# =====================================================

# Mensagem inicial usada no /start e como 1ª mensagem do histórico
mensagem_boas_vindas = "Olá! Sou o assistente virtual do DECODE. Como posso ajudar?"

# True  -> o bot envia "Rota: RAG" ou "Rota: NORMAL" antes da resposta
#          (útil para testar, como a aula 6.2 mostrava o agente)
# False -> o usuário vê apenas a resposta
mostrar_rota = True


# =====================================================
# Histórico das conversas
# =====================================================

# Dicionário: a chave é o chat_id (identifica cada conversa
# do Telegram) e o valor é a lista de mensagens daquela conversa.
historicos = {}


def iniciar_historico(chat_id):

    # Só cria o histórico se esse chat ainda não tiver um
    if chat_id not in historicos:

        historicos[chat_id] = [

            AIMessage(
                content=mensagem_boas_vindas
            )

        ]


# =====================================================
# Formatação para o Telegram
# =====================================================

def formatar_telegram(texto):

    # Converte **texto** (negrito do modelo) para <b>texto</b> (negrito do Telegram)
    texto = re.sub(
        r"\*\*(.*?)\*\*",
        r"<b>\1</b>",
        texto
    )

    return texto


# =====================================================
# Chamada da API
# =====================================================

def chamar_api(
    pergunta,
    historico
):

    # A API espera o histórico como lista de dicionários
    # {"role": ..., "content": ...}. Aqui convertemos as
    # mensagens do LangChain (AIMessage / HumanMessage).
    historico_json = []

    for mensagem in historico:

        # mensagem.type vale "human" (usuário) ou "ai" (IA)
        if mensagem.type == "human":
            papel = "user"
        else:
            papel = "assistant"

        historico_json.append({
            "role": papel,
            "content": mensagem.content
        })

    # Envia a pergunta e o histórico para a API.
    # timeout=120 -> espera no máximo 120 segundos pela resposta
    resposta = requests.post(

        f"{API_URL}/chat",

        json={
            "pergunta": pergunta,
            "historico": historico_json
        },

        timeout=120
    )

    # Se a API devolveu erro (ex.: 500), gera uma exceção
    # que será tratada no try/except do bot.
    resposta.raise_for_status()

    # Converte o JSON da resposta em dicionário Python
    dados = resposta.json()

    return dados["rota"], dados["resposta"]


# =====================================================
# Criando o bot Telegram
# =====================================================

# strip() remove espaços ou quebras de linha que
# possam ter vindo junto com o token
bot = telebot.TeleBot(
    TELEGRAM_TOKEN.strip()
)

print(
    "Bot criado."
)


# =====================================================
# Comando /start
# =====================================================

@bot.message_handler(
    commands=["start"]
)
def iniciar_bot(message):

    chat_id = message.chat.id

    iniciar_historico(chat_id)

    bot.send_message(
        chat_id,
        mensagem_boas_vindas
    )


# =====================================================
# Recebendo mensagens do usuário
# =====================================================

@bot.message_handler(
    content_types=["text"],
    func=lambda message: True
)
def receber_mensagem(message):

    chat_id = message.chat.id

    pergunta = message.text

    iniciar_historico(
        chat_id
    )

    try:

        # ==========================================
        # 1. Chama a API (pergunta + histórico)
        # ==========================================

        rota, resposta = chamar_api(
            pergunta,
            historicos[chat_id]
        )

        # ==========================================
        # 2. Mostra a rota usada
        # ==========================================

        if mostrar_rota:

            bot.send_message(
                chat_id,
                f"Rota: {rota}"
            )

        # ==========================================
        # 3. Formata a resposta
        # ==========================================

        resposta_formatada = formatar_telegram(
            resposta
        )

        # ==========================================
        # 4. Envia a resposta
        # ==========================================

        try:

            bot.send_message(
                chat_id,
                resposta_formatada,
                parse_mode="HTML"
            )

        except Exception as e:

            # O Telegram recusa HTML malformado. Se isso
            # acontecer, reenvia a resposta como texto simples.
            print(f"Falha ao enviar em HTML: {e}")

            bot.send_message(
                chat_id,
                resposta
            )

        # ==========================================
        # 5. Atualiza o histórico
        # ==========================================

        # {pessoa mandou}
        historicos[chat_id].append(

            HumanMessage(
                content=pergunta
            )

        )

        # {resposta da IA}
        historicos[chat_id].append(

            AIMessage(
                content=resposta
            )

        )

        # Mantém a 1ª mensagem + as 20 mais recentes,
        # para o histórico não crescer sem parar
        mensagem_inicial = historicos[chat_id][0]
        mensagem_recentes = historicos[chat_id][1:][-20:]
        historicos[chat_id] = [mensagem_inicial] + mensagem_recentes

    except Exception as e:

        # O erro técnico fica só no log do servidor
        print(f"Erro: {e}")

        # O usuário recebe uma mensagem simples, sem
        # detalhes internos (como endereços da API)
        bot.send_message(
            chat_id,
            "Desculpe, tive um problema para responder agora. Tente novamente em instantes."
        )


# =====================================================
# Iniciando o bot
# =====================================================

print("Bot iniciado...")

bot.infinity_polling(
    skip_pending=True  # Mensagens antigas pendentes são ignoradas
)
