# =====================================================
# verificar_respostas.py  -  15 perguntas de verificação
# =====================================================
#
# PARA QUE SERVE
# --------------
# Roda 15 perguntas contra a API e destaca respostas que
# PROMETEM recursos que o DECODE v1 não tem (achado F19:
# busca por palavra-chave, backup automático, compartilhamento,
# administrador, controle de acesso granular, planos).
#
# Rode este script ANTES DE CADA ENTREGA, com a API ligada
# (uvicorn) e o banco_chroma criado.
#
# COMO EXECUTAR
# -------------
#   python verificar_respostas.py            (as 15 perguntas)
#   python verificar_respostas.py 3 8 13     (só as perguntas 3, 8 e 13)
#
# COMO LER O RESULTADO
# --------------------
#   [OK]       a resposta passou nas duas checagens automáticas.
#   [REVISAR]  uma checagem falhou; LEIA a resposta e decida.
#              (A checagem é simples: ela pode marcar uma
#               resposta correta por engano. Quem decide é
#               você, lendo o texto.)

# Biblioteca que faz chamadas HTTP (GET, POST...)
import requests

# Biblioteca para esperar alguns segundos (time.sleep)
import time

# Biblioteca para ler os argumentos digitados na linha de comando
import sys


# =====================================================
# Endereço da API
# =====================================================

# "localhost" quando a API roda no mesmo computador.
# Na EC2, rode este script dentro da própria EC2.
ip = "localhost"

# =====================================================
# Limite de tokens da Groq
# =====================================================

# A conta gratuita da Groq limita o modelo openai/gpt-oss-20b a
# 8000 tokens POR MINUTO (TPM). Cada pergunta gasta cerca de 2000.
# Se o script fizesse as 15 perguntas seguidas, a Groq responderia
# "429 - rate limit" e a API devolveria erro 500 (isso já aconteceu).
# Por isso o script ESPERA entre as perguntas.
PAUSA_ENTRE_PERGUNTAS = 20   # segundos de espera depois de cada pergunta
ESPERA_APOS_ERRO = 30        # segundos de espera antes de tentar de novo
TENTATIVAS = 3               # quantas vezes tenta a mesma pergunta


# =====================================================
# Termos que NÃO podem aparecer como recurso existente
# =====================================================

# Recursos que o DECODE v1 NÃO tem. Se a resposta citar um deles
# em uma frase sem negação (sem "não", "sem", "previsto"...),
# o script marca [REVISAR].
RECURSOS_INEXISTENTES = [
    "palavra-chave",
    "palavras-chave",
    "busca rápida",
    "busca avançada",
    "backup automático",
    "compartilh",
    "administrador",
    "acesso granular",
    "controle de acesso granular",
    "plano pago",
    "planos",
]

# Se a frase tiver uma destas palavras, entendemos que o recurso
# foi citado como INEXISTENTE ou FUTURO (o que é correto).
NEGACOES = [
    "não",
    "nao",
    "sem ",
    "ainda",
    "previst",
    "planejad",
    "v2.0",
    "futur",
    "nenhum",
    "trará",
    "em breve",
]


# =====================================================
# As 15 perguntas
# =====================================================
#
# Cada item tem:
#   pergunta  -> o que o usuário digita no Telegram;
#   esperados -> a resposta deve conter PELO MENOS UM destes
#                termos (confirma que usou a base certa);
#   checar_proibidos -> True: verifica os RECURSOS_INEXISTENTES.

PERGUNTAS = [
    # --- Benefícios e comparações (onde o F19 apareceu) ---
    {"pergunta": "Por que devo me tornar usuário do DECODE?",
     "esperados": ["bucket", "sha-256"], "checar_proibidos": True},
    {"pergunta": "Quais são os benefícios do DECODE?",
     "esperados": ["bucket", "sha-256"], "checar_proibidos": True},
    {"pergunta": "O DECODE é igual ao Google Drive?",
     "esperados": ["não", "simples", "v1"], "checar_proibidos": True},
    {"pergunta": "Qual é a vantagem de usar o DECODE?",
     "esperados": ["bucket", "sha-256"], "checar_proibidos": True},

    # --- Recursos que NÃO existem (a resposta deve negar) ---
    {"pergunta": "O DECODE tem busca por palavra-chave?",
     "esperados": ["não", "v2.0", "previst"], "checar_proibidos": True},
    {"pergunta": "O DECODE faz backup automático dos arquivos?",
     "esperados": ["não"], "checar_proibidos": True},
    {"pergunta": "Posso compartilhar arquivos com outras pessoas?",
     "esperados": ["dono", "não existe", "não há", "não possui"], "checar_proibidos": True},
    {"pergunta": "Existe administrador no DECODE?",
     "esperados": ["não existe", "não há", "não possui", "não tem", "dono"], "checar_proibidos": True},
    {"pergunta": "Quanto custa o DECODE? Existe plano pago?",
     "esperados": ["não consta", "não define", "não traz", "equipe"], "checar_proibidos": True},

    # --- Fatos que EXISTEM (a resposta deve trazer o dado certo) ---
    {"pergunta": "O que é o DECODE?",
     "esperados": ["saas", "bucket", "gestão"], "checar_proibidos": True},
    {"pergunta": "Quais tecnologias o DECODE usa?",
     "esperados": ["java", "spring"], "checar_proibidos": False},
    {"pergunta": "Quem faz parte da equipe do DECODE?",
     "esperados": ["robson", "eduardo", "gabriel", "helio"], "checar_proibidos": False},
    {"pergunta": "Qual é o tamanho máximo de um arquivo?",
     "esperados": ["10 mb"], "checar_proibidos": False},
    {"pergunta": "O DECODE já tem inteligência artificial e OCR?",
     "esperados": ["v2.0", "previst", "não"], "checar_proibidos": True},

    # --- Informação que a base NÃO tem ---
    {"pergunta": "Qual é o telefone do DECODE?",
     "esperados": ["não consta", "equipe"], "checar_proibidos": False},
]


# =====================================================
# Funções de checagem
# =====================================================

def frases(texto):
    # Quebra a resposta em frases (por ponto, ! , ? e quebra de linha).
    # O ponto de "v2.0" NÃO é fim de frase: trocamos por um marcador
    # temporário, quebramos, e depois devolvemos o "v2.0" no lugar.
    texto = texto.replace("v2.0", "v2#0")
    texto = texto.replace("!", ".").replace("?", ".").replace("\n", ".")
    return [f.strip().replace("v2#0", "v2.0") for f in texto.split(".") if f.strip()]


def tem_negacao(texto):
    # True se o texto tem alguma palavra de negação/futuro.
    texto = texto.lower()
    return any(n in texto for n in NEGACOES)


def recursos_suspeitos(resposta):
    # Procura recursos inexistentes citados SEM negação.
    #
    # Atenção a LISTAS como esta (resposta correta do bot):
    #   - Na v1 do DECODE não há:
    #     - backup automático;
    #     - compartilhamento de arquivos;
    # A negação ("não há") está na linha de CIMA; os itens ficam
    # mais para a direita (mais "recuados"). Por isso, quando uma
    # linha termina em ":" e tem negação, os itens recuados abaixo
    # dela herdam essa negação.
    achados = []

    # Recuo (nº de espaços) da linha-título de uma lista negada;
    # None = não estamos dentro de uma lista negada.
    recuo_lista_negada = None

    for linha in resposta.split("\n"):

        if not linha.strip():
            continue

        recuo = len(linha) - len(linha.lstrip(" "))

        # Saiu da lista negada? (linha menos recuada ou igual ao título)
        if recuo_lista_negada is not None and recuo <= recuo_lista_negada:
            recuo_lista_negada = None

        # Esta linha é o título de uma lista negada?
        if linha.rstrip().endswith(":") and tem_negacao(linha):
            recuo_lista_negada = recuo

        dentro_de_lista_negada = recuo_lista_negada is not None and recuo > recuo_lista_negada

        for frase in frases(linha):

            frase_minuscula = frase.lower()

            for recurso in RECURSOS_INEXISTENTES:

                if recurso in frase_minuscula:

                    negada = tem_negacao(frase) or dentro_de_lista_negada

                    if not negada:
                        achados.append(f'"{recurso}" em: {frase}')

    return achados


def normalizar_espacos(texto):
    # O modelo às vezes escreve "10 MB" com um espaço "invisível"
    # especial (espaço não separável, U+00A0 ou U+202F). Para o
    # olho é igual, mas para o Python "10 MB" deixa de ser igual a
    # "10<espaço especial>MB". Trocamos esses espaços por um normal.
    for especial in ("\u00a0", "\u202f", "\u2009"):
        texto = texto.replace(especial, " ")
    return texto


def tem_algum_esperado(resposta, esperados):
    # True se a resposta contém pelo menos um dos termos esperados.
    resposta_minuscula = normalizar_espacos(resposta).lower()
    return any(termo in resposta_minuscula for termo in esperados)


def verificar(item, resposta):
    # Devolve a lista de problemas encontrados (vazia = [OK]).
    problemas = []

    if not tem_algum_esperado(resposta, item["esperados"]):
        problemas.append("faltou algum destes termos: " + ", ".join(item["esperados"]))

    if item["checar_proibidos"]:
        for achado in recursos_suspeitos(resposta):
            problemas.append("recurso suspeito: " + achado)

    return problemas


# =====================================================
# Execução
# =====================================================

def main():

    # sys.argv[1:] são os argumentos digitados depois do nome do script.
    numeros_escolhidos = [int(a) for a in sys.argv[1:] if a.isdigit()]

    total_ok = 0
    total_revisar = 0
    total_erros = 0

    for numero, item in enumerate(PERGUNTAS, start=1):

        # Se você passou números na linha de comando
        # (ex.: python verificar_respostas.py 3 8 13),
        # roda SOMENTE essas perguntas.
        if numeros_escolhidos and numero not in numeros_escolhidos:
            continue

        print("=" * 60)

        # Cada pergunta é uma conversa NOVA (sem histórico).
        # Se a API falhar (ex.: limite da Groq), espera e tenta de novo.
        dados = None
        ultimo_erro = None

        for tentativa in range(1, TENTATIVAS + 1):

            try:
                resposta = requests.post(
                    f"http://{ip}:8000/chat",
                    json={"pergunta": item["pergunta"]},
                    timeout=90
                )

                resposta.raise_for_status()

                dados = resposta.json()
                break

            except requests.exceptions.RequestException as erro:
                ultimo_erro = erro

                if tentativa < TENTATIVAS:
                    print(f"   (falhou; esperando {ESPERA_APOS_ERRO}s para "
                          f"tentar de novo - tentativa {tentativa}/{TENTATIVAS})")
                    time.sleep(ESPERA_APOS_ERRO)

        if dados is None:
            # Esgotou as tentativas: registra o erro e segue para a próxima.
            print(f"{numero:02d}. {item['pergunta']}  [ERRO]")
            print(f"  -> a API falhou: {ultimo_erro}")
            total_erros += 1
            time.sleep(PAUSA_ENTRE_PERGUNTAS)
            continue

        problemas = verificar(item, dados["resposta"])

        situacao = "[OK]" if not problemas else "[REVISAR]"

        print(f"{numero:02d}. {item['pergunta']}  {situacao}")
        print(f"Rota: {dados['rota']}")
        print(dados["resposta"])

        for problema in problemas:
            print(f"  -> {problema}")

        if problemas:
            total_revisar += 1
        else:
            total_ok += 1

        # Espera antes da próxima pergunta (respeita o limite da Groq)
        time.sleep(PAUSA_ENTRE_PERGUNTAS)

    print("=" * 60)
    total = total_ok + total_revisar + total_erros
    print(f"Resumo: {total_ok} OK, {total_revisar} para revisar, "
          f"{total_erros} com erro da API, de {total} perguntas.")


# Só executa quando o arquivo é rodado diretamente
# (assim os testes podem importar as funções sem chamar a API).
if __name__ == "__main__":
    main()
