# =====================================================
# verificar_chaves.py  -  DECODE
# =====================================================
#
# PARA QUE SERVE ESTE PROGRAMA
# ----------------------------
# Confere se o arquivo .env está correto e se as chaves
# da Groq e do Telegram FUNCIONAM, sem nunca mostrar as
# chaves na tela (assim você pode copiar o resultado
# e mostrar para alguém sem vazar nada).
#
# O QUE ELE FAZ, NA ORDEM
# -----------------------
#   1. lê o arquivo .env;
#   2. confere se as 3 variáveis existem e têm o formato esperado;
#   3. pergunta à Groq se a GROQ_API_KEY é válida;
#   4. pergunta ao Telegram qual é o bot do TELEGRAM_TOKEN.
#
# COMO EXECUTAR (pasta ~/decode-chatbot, com a .venv ativa)
# ----------------------------------------------------
#   python verificar_chaves.py

# Lê o arquivo .env e coloca os valores nas variáveis de ambiente
from dotenv import load_dotenv

# Acessa as variáveis de ambiente
import os

# Faz chamadas HTTP (a mesma biblioteca usada no bot.py)
import requests


# =====================================================
# 1. Lendo o arquivo .env
# =====================================================

# Procura o .env na pasta atual e carrega os valores
load_dotenv()

# Lê cada variável (strip() remove espaços e quebras de linha
# que às vezes entram sem querer ao colar a chave)
groq_key = os.getenv("GROQ_API_KEY", "").strip()
telegram_token = os.getenv("TELEGRAM_TOKEN", "").strip()
api_url = os.getenv("API_URL", "").strip()

# Contador de problemas encontrados
problemas = 0


# =====================================================
# 2. Conferindo o formato (sem mostrar os valores)
# =====================================================

print("--- Conferindo o arquivo .env ---")

# A chave da Groq começa com "gsk_"
if groq_key.startswith("gsk_"):
    print("GROQ_API_KEY: formato ok")
else:
    print("GROQ_API_KEY: ausente ou com formato estranho (deve começar com gsk_)")
    problemas += 1

# O token do Telegram tem o formato NUMEROS:LETRAS (tem ":" no meio)
if ":" in telegram_token and telegram_token.split(":")[0].isdigit():
    print("TELEGRAM_TOKEN: formato ok")
else:
    print("TELEGRAM_TOKEN: ausente ou com formato estranho (deve ser NUMEROS:LETRAS)")
    problemas += 1

# O endereço da API precisa começar com http
if api_url.startswith("http"):
    print(f"API_URL: {api_url}")
else:
    print("API_URL: ausente (use http://localhost:8000)")
    problemas += 1

# Aviso comum: aspas soltas dentro da chave (resto de um copiar e colar errado)
if '"' in groq_key or "'" in groq_key or '"' in telegram_token or "'" in telegram_token:
    print("ATENÇÃO: há aspas dentro de uma chave. Remova as aspas do .env.")
    problemas += 1


# =====================================================
# 3. Testando a chave da Groq
# =====================================================

print("--- Testando a Groq ---")

if groq_key.startswith("gsk_"):
    try:
        # Pede a lista de modelos; só funciona com chave válida.
        # timeout=15 -> desiste depois de 15 segundos
        resposta = requests.get(
            "https://api.groq.com/openai/v1/models",
            headers={"Authorization": f"Bearer {groq_key}"},
            timeout=15
        )

        if resposta.status_code == 200:
            print("Groq: chave VÁLIDA")
        elif resposta.status_code == 401:
            print("Groq: chave INVÁLIDA ou revogada (erro 401). Crie outra no console da Groq.")
            problemas += 1
        else:
            print(f"Groq: resposta inesperada (status {resposta.status_code})")
            problemas += 1

    except requests.RequestException:
        # Não imprimimos o erro completo: ele poderia conter a chave
        print("Groq: não consegui conectar (internet ou bloqueio de rede).")
        problemas += 1
else:
    print("Groq: teste pulado (corrija a chave antes).")


# =====================================================
# 4. Testando o token do Telegram
# =====================================================

print("--- Testando o Telegram ---")

if ":" in telegram_token:
    try:
        # getMe devolve os dados do bot dono do token
        resposta = requests.get(
            f"https://api.telegram.org/bot{telegram_token}/getMe",
            timeout=15
        )

        if resposta.status_code == 200:
            nome = resposta.json()["result"]["username"]
            print(f"Telegram: token VÁLIDO (bot @{nome})")
        elif resposta.status_code in (401, 404):
            print("Telegram: token INVÁLIDO ou revogado. Gere outro no BotFather.")
            problemas += 1
        else:
            print(f"Telegram: resposta inesperada (status {resposta.status_code})")
            problemas += 1

    except requests.RequestException:
        # Não imprimimos o erro completo: a URL dele contém o token
        print("Telegram: não consegui conectar (internet ou bloqueio de rede).")
        problemas += 1
else:
    print("Telegram: teste pulado (corrija o token antes).")


# =====================================================
# Resultado final
# =====================================================

print("---------------------------------")
if problemas == 0:
    print("TUDO CERTO: o .env está pronto.")
else:
    print(f"Foram encontrados {problemas} problema(s). Corrija o .env e rode de novo.")
