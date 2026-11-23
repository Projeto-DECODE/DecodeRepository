"""Teste do bot do Telegram sem executar o bot.

O bot.py inicia o polling ao ser importado, então aqui só extraímos a função
formatar_telegram do código-fonte (via ast) e testamos ela isolada.
"""

import ast
import re
from pathlib import Path

import pytest

FONTE = Path(__file__).resolve().parent.parent / "bot.py"


def _formatar_telegram():
    arvore = ast.parse(FONTE.read_text(encoding="utf-8"))
    funcao = next(n for n in arvore.body if isinstance(n, ast.FunctionDef) and n.name == "formatar_telegram")
    espaco = {"re": re}
    exec(compile(ast.Module([funcao], []), str(FONTE), "exec"), espaco)
    return espaco["formatar_telegram"]


def test_negrito_markdown_vira_tag_b():
    assert "<b>negrito</b>" in _formatar_telegram()("isto é **negrito**")


@pytest.mark.xfail(strict=True, reason="F20: HTML vindo do modelo vai ao Telegram sem escape")
def test_html_do_modelo_e_escapado():
    saida = _formatar_telegram()('veja <a href="https://exemplo.invalid/login">sua conta</a>')
    assert "&lt;a" in saida
