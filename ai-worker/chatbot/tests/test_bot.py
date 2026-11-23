"""Testes da lógica do bot do Telegram sem executar o bot.

O bot.py inicia o polling ao ser importado, então aqui só extraímos do
código-fonte (via ast) as constantes e funções puras que queremos testar:
formatar_telegram, chat_permitido e passou_do_limite.
"""

import ast
import html
import re
import time
from collections import deque
from pathlib import Path

FONTE = Path(__file__).resolve().parent.parent / "bot.py"

CONSTANTES = {"LIMITE_POR_MINUTO", "LIMITE_POR_DIA", "TAMANHO_MAXIMO_PERGUNTA", "chats_permitidos",
              "horarios_minuto", "horarios_dia", "aviso_privacidade"}
FUNCOES = {"formatar_telegram", "chat_permitido", "passou_do_limite"}


def _carregar():
    """Executa só as constantes e funções puras do bot.py e devolve o espaço de nomes."""
    arvore = ast.parse(FONTE.read_text(encoding="utf-8"))
    nos = []
    for no in arvore.body:
        if isinstance(no, ast.FunctionDef) and no.name in FUNCOES:
            nos.append(no)
        elif isinstance(no, ast.Assign) and any(isinstance(t, ast.Name) and t.id in CONSTANTES for t in no.targets):
            nos.append(no)
    espaco = {"re": re, "html": html, "time": time, "deque": deque}
    exec(compile(ast.Module(nos, []), str(FONTE), "exec"), espaco)
    return espaco


# --------------------------------------------------------------------------
# Formatação (achado F20, corrigido)
# --------------------------------------------------------------------------


def test_negrito_markdown_vira_tag_b():
    assert "<b>negrito</b>" in _carregar()["formatar_telegram"]("isto é **negrito**")


def test_html_do_modelo_e_escapado():
    saida = _carregar()["formatar_telegram"]('veja <a href="https://exemplo.invalid/login">sua conta</a>')
    assert "&lt;a" in saida
    assert "<a " not in saida


# --------------------------------------------------------------------------
# Limite de uso e lista de chats permitidos (achado F17, corrigido)
# --------------------------------------------------------------------------


def test_limite_por_minuto_recusa_a_mensagem_seguinte():
    ns = _carregar()
    limite = ns["LIMITE_POR_MINUTO"]
    assert [ns["passou_do_limite"](1) for _ in range(limite)] == [False] * limite
    assert ns["passou_do_limite"](1) is True


def test_limite_e_por_chat():
    ns = _carregar()
    for _ in range(ns["LIMITE_POR_MINUTO"]):
        ns["passou_do_limite"](1)
    assert ns["passou_do_limite"](1) is True
    assert ns["passou_do_limite"](2) is False


def test_mensagem_recusada_nao_consome_o_limite():
    ns = _carregar()
    for _ in range(ns["LIMITE_POR_MINUTO"] + 5):
        ns["passou_do_limite"](1)
    assert len(ns["horarios_minuto"][1]) == ns["LIMITE_POR_MINUTO"]


def test_sem_lista_todos_os_chats_sao_permitidos():
    ns = _carregar()
    assert ns["chat_permitido"](123) is True


def test_com_lista_so_os_chats_listados_sao_permitidos():
    ns = _carregar()
    ns["chats_permitidos"].add(111)
    assert ns["chat_permitido"](111) is True
    assert ns["chat_permitido"](222) is False


# --------------------------------------------------------------------------
# Privacidade (achado F23, corrigido)
# --------------------------------------------------------------------------


def test_aviso_de_privacidade_cita_o_servico_externo():
    aviso = _carregar()["aviso_privacidade"]
    assert "privacidade" in aviso.lower()
    assert "Groq" in aviso
