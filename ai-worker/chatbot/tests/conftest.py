"""Fixtures dos testes do chatbot.

Os testes NÃO usam rede, chave da Groq, modelo de embeddings nem banco
Chroma: o LLM, os embeddings e o Chroma são trocados por versões simuladas
antes de importar o app. Assim o job roda em segundos e sem segredos.
"""

import sys
import types
from pathlib import Path

import pytest
from langchain_core.language_models.chat_models import BaseChatModel
from langchain_core.messages import AIMessage
from langchain_core.outputs import ChatGeneration, ChatResult

CODIGO = Path(__file__).resolve().parent.parent
CHAMADAS = []  # tudo o que chega ao "LLM" simulado


class LLMFalso(BaseChatModel):
    @property
    def _llm_type(self):
        return "falso"

    def bind_tools(self, tools, **kwargs):
        return self

    def _generate(self, messages, stop=None, run_manager=None, **kwargs):
        textos = [str(m.content) for m in messages]
        CHAMADAS.append({"roles": [m.type for m in messages], "textos": textos})
        resposta = "NORMAL" if "roteador" in textos[-1].lower() else "ok"
        return ChatResult(generations=[ChatGeneration(message=AIMessage(content=resposta))])


class _Embeddings:
    def __init__(self, **kwargs):
        pass


class _Documento:
    page_content = "trecho da base"


class _Chroma:
    def __init__(self, **kwargs):
        pass

    def similarity_search(self, consulta, k=3):
        return [_Documento()]


def _stub(nome, **atributos):
    modulo = types.ModuleType(nome)
    for chave, valor in atributos.items():
        setattr(modulo, chave, valor)
    sys.modules[nome] = modulo


@pytest.fixture(scope="session")
def app_modulo():
    _stub("langchain_groq", ChatGroq=lambda **kwargs: LLMFalso())
    _stub("langchain_huggingface", HuggingFaceEmbeddings=_Embeddings)
    _stub("langchain_chroma", Chroma=_Chroma)
    sys.dont_write_bytecode = True
    sys.path.insert(0, str(CODIGO))
    import app

    return app


@pytest.fixture()
def client(app_modulo):
    from fastapi.testclient import TestClient

    return TestClient(app_modulo.app)


@pytest.fixture()
def chamadas():
    CHAMADAS.clear()
    return CHAMADAS


@pytest.fixture()
def llm_falso():
    return LLMFalso
