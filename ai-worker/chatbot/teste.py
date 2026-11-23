# =====================================================
# teste.py  -  Teste da API do DECODE
# =====================================================
#
# PARA QUE SERVE
# --------------
# Chama a API como se fosse o bot, mas direto do
# terminal, para confirmar que ela responde.
# Pode ser executado na própria EC2 (ip = "localhost")
# ou no seu computador (ip = IP público da EC2).
#
# COMO EXECUTAR
# -------------
#   python teste.py

# Biblioteca que faz chamadas HTTP (GET, POST...)
import requests


# =====================================================
# Endereço da API
# =====================================================

# Troque pelo IP público da EC2 (se rodar no seu PC)
# ou por "localhost" (se rodar dentro da própria EC2).
ip = "seu_ip_publico_aqui"


# =====================================================
# Teste 1 - API no ar?
# =====================================================

resposta = requests.get(

    f"http://{ip}:8000/"
)

print("Teste 1 - status:")

print(resposta.status_code)

print(resposta.json())


# =====================================================
# Teste 2 - Pergunta sem histórico
# =====================================================

resposta = requests.post(

    f"http://{ip}:8000/chat",

    json={
        "pergunta": "O que é o DECODE?"
    }
)

print("Teste 2 - status:")

print(resposta.status_code)

print(resposta.json())


# =====================================================
# Teste 3 - Pergunta curta COM histórico
# =====================================================
#
# "E quais as tecnologias?" só faz sentido porque o
# histórico informa que o assunto é o DECODE.

resposta = requests.post(

    f"http://{ip}:8000/chat",

    json={
        "pergunta": "E quais as tecnologias?",

        "historico": [
            {
                "role": "assistant",
                "content": "Olá! Sou o assistente virtual do DECODE. Como posso ajudar?"
            },
            {
                "role": "user",
                "content": "O que é o DECODE?"
            },
            {
                "role": "assistant",
                "content": "O DECODE é uma plataforma de gestão inteligente de documentos não estruturados."
            }
        ]
    }
)

print("Teste 3 - status:")

print(resposta.status_code)

print(resposta.json())
