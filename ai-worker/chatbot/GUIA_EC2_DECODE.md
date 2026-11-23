# GUIA — Chatbot do DECODE na EC2 (API + Bot do Telegram)

Padrão do professor (aula 9.0): Ubuntu na EC2, ambiente virtual `.venv`, API FastAPI com uvicorn.
Acrescentamos o **bot do Telegram** que chama a API e mantém o **histórico** como na aula 6.2.
A base de conhecimento (`DECODE_Base_Conhecimento.pdf`) foi montada a partir dos documentos do repositório
`Projeto-DECODE/DecodeRepository` (commit `f086070`, de 18/11/2026) e atualizada com as combinações do
grupo de WhatsApp da equipe (leitura de 22/11/2026).

> **Pendência do grupo (18/11):** o Eduardo pediu que você faça o **commit do chatbot no repositório**, porque
> ele precisa dele para testar se o chatbot cabe na nuvem. Esse passo está na **Parte 14**. Você pode fazê-lo
> antes mesmo de ter a EC2 pronta.

---

## Parte 0 — Visão geral (leia primeiro)

```
Usuário no Telegram
        │  mensagem
        ▼
   bot.py  (histórico por chat_id: 1ª mensagem + 20 recentes)
        │  POST http://localhost:8000/chat  {pergunta, historico}
        ▼
   app.py  (FastAPI)
        │  1. decidir_rota → RAG ou NORMAL
        │  2. agente_rag (consulta o banco_chroma)  ou  agente_normal
        ▼
   Groq (openai/gpt-oss-20b)
```

- `bot.py` e `app.py` rodam **na mesma EC2**, em dois programas separados.
- **Combinado com o Eduardo no grupo (14/10):** o bot usa **long polling** (sem webhook, sem domínio, sem porta nova),
  roda na EC2 como **serviço systemd** e chama uma **API de LLM externa** (a Groq), porque a EC2 pequena não
  comporta um modelo em GPU. Tudo isso já é o que este projeto faz.
- **Dois bots, um por ambiente** (obrigatório): `decode_staging_bot` (testes) e `decode_bot` (produção).
  Um token só pode ter **uma** instância ligada; a segunda recebe o erro 409 e as duas se derrubam.
- O **histórico fica no bot**. A API é "sem memória": recebe o histórico a cada chamada.
- `banco_vetorial.py` roda **uma vez** para criar a pasta `banco_chroma` a partir do PDF.

### Arquivos da entrega

| Arquivo | Função |
|---|---|
| `DECODE_Base_Conhecimento.pdf` | Base de conhecimento (69 itens, 19 páginas) |
| `banco_vetorial.py` | Lê o PDF e cria o banco vetorial |
| `app.py` | API FastAPI com roteador, agentes e histórico |
| `bot.py` | Bot do Telegram que chama a API |
| `teste.py` | Testa a API pelo terminal |
| `verificar_chaves.py` | Confere o `.env` e testa as chaves da Groq e do Telegram (sem mostrar as chaves) |
| `diagnostico_busca.py` | Mostra quais itens da base a busca encontra para cada pergunta (útil se o bot responder "não consta" sem motivo) |
| `requirements.txt` | Bibliotecas (lista do professor + `pyTelegramBotAPI` e `requests`) |
| `env.example.txt` | Modelo do `.env` (vira `.env` na EC2) |
| `deploy/decode-api.service` e `deploy/decode-bot.service` | Mantêm API e bot ligados (Parte 10). Refletem a instalação real na EC2 do DECODE (`/opt/decode-chatbot`) |

### O que o repositório do DECODE já tem (e o que isso muda para nós)

- O Eduardo já mantém uma EC2 de produção chamada `decode-app` (pasta `/opt/decode`, região us-east-2),
  que roda o site e os 3 serviços com Docker Compose. Os limites de memória dos contêineres somam cerca de **3,1 GB**.
- Só o Nginx publica porta (80). A porta **8000** do nosso guia não existe lá.
- O chatbot é a primeira peça em Python do projeto: a pasta `ai-worker/` está reservada (OCR, embeddings e chat da v2.0).
- A equipe trabalha com Pull Requests, commits em português e **nunca** versiona segredos (Parte 14).
- Em **18/11/2026** o Eduardo validou o DECODE no ar na AWS (criou usuário e bucket, enviou e baixou um arquivo).
  A PR #9 (deploy automático na EC2 via SSM) estava aguardando aprovação.

---

## Parte 1 — O que pedir ao Eduardo Mosti

**Decisão importante: máquina dedicada ou a `decode-app`?**

| Opção | Quando usar |
|---|---|
| **EC2 nova, só para o chatbot** (recomendada) | Mantém o padrão da aula 9.0 e não disputa memória com o site. O modelo de embeddings e o PyTorch precisam de cerca de 1,5 a 2 GB de RAM. |
| **A própria `decode-app`** | Só se o Eduardo confirmar memória livre (rode `free -h` e veja se sobram uns 2 GB) e cuidar do Security Group. |

Envie esta mensagem:

> Eduardo, preciso de uma EC2 para o chatbot do DECODE (assistente no Telegram), separada da decode-app:
> - Sistema: **Ubuntu Server 24.04 LTS**
> - Tipo: **t3.small** (2 vCPU, 2 GB de RAM) — se puder, t3.medium (4 GB) para ter folga
> - Disco: **30 GB gp3**
> - Security Group (entrada): **SSH (porta 22)** somente para o meu IP e **TCP 8000** somente para o meu IP
> - Par de chaves (`.pem`) para eu acessar
> - Se possível, um **Elastic IP** (IP fixo)
>
> Preciso que você me envie: **o IP público**, **o arquivo .pem**, e confirme **o usuário de acesso** (no Ubuntu é `ubuntu`).
> Se preferir usar a decode-app, me diga quanta memória livre ela tem (`free -h`).

O que **você** precisa ter em mãos antes de começar:

1. IP público da EC2 e arquivo `.pem` (do Eduardo).
2. `GROQ_API_KEY` (a mesma das aulas).
3. **Dois** tokens do BotFather (aula 5.1): o do `decode_staging_bot` (testes) e o do `decode_bot` (produção).
   Cada token é uma conversa `/newbot` no BotFather, e cada nome precisa terminar em `bot`.
4. Os arquivos da tabela da Parte 0, numa pasta no seu computador.

> **Segurança:** o professor abre a porta 8000 para `0.0.0.0/0` (qualquer pessoa) só para o laboratório. Como o bot roda dentro da própria EC2 e usa `localhost`, você só precisa da porta 8000 aberta para **o seu IP**, para executar o `teste.py` do seu computador. Quando não precisar mais, peça para fechá-la.

---

## Parte 2 — Conectar na EC2

Siga o mesmo método da aula 9.0 (VS Code + Remote-SSH).

1. Abra o **VS Code** → instale a extensão **Remote - SSH**.
2. Guarde o arquivo `.pem` em uma pasta sua (ex.: `C:\Users\SeuNome\.ssh\chave-decode-chatbot.pem`).
3. No VS Code, pressione `F1` → **Remote-SSH: Open SSH Configuration File** → escolha o arquivo `config` e acrescente:

```
Host decode-chatbot
    HostName IP_PUBLICO_DA_EC2
    User ubuntu
    IdentityFile C:\Users\SeuNome\.ssh\chave-decode-chatbot.pem
```

   - `Host` = apelido que você escolhe;
   - `HostName` = o IP público que o Eduardo enviou;
   - `User` = `ubuntu`;
   - `IdentityFile` = caminho do `.pem`.
4. `F1` → **Remote-SSH: Connect to Host** → `decode-chatbot`. Escolha **Linux** se perguntar e aceite a impressão digital (*Continue*).
5. Abra o terminal: menu **Terminal → New Terminal**.

**Resultado esperado:** o terminal mostra algo como `ubuntu@ip-172-31-xx-xx:~$`. Isso confirma que você está **dentro** da EC2.

---

## Parte 3 — Preparar o Ubuntu (uma vez)

Cada linha abaixo é um comando; execute uma por vez.

```bash
# Atualiza a lista de programas disponíveis
sudo apt update

# Atualiza os programas já instalados (-y = responde "sim" sozinho)
sudo apt upgrade -y

# Instala Python, pip, venv, arquivos de desenvolvimento e compiladores
sudo apt install -y python3 python3-pip python3-venv python3-dev build-essential
```

**Conferir:**

```bash
python3 --version
```

Esperado: `Python 3.12.x`.

> Se aparecer uma tela rosa/roxa perguntando sobre reiniciar serviços, apenas pressione **Enter**.

---

## Parte 4 — Pasta do projeto e ambiente virtual

```bash
# Cria a pasta do projeto
mkdir ~/decode-chatbot

# Entra na pasta
cd ~/decode-chatbot

# Cria o ambiente virtual (pasta oculta .venv)
python3 -m venv .venv

# Ativa o ambiente virtual
source .venv/bin/activate
```

**Resultado esperado:** o terminal passa a mostrar `(.venv)` no começo da linha. Isso significa que está ativo.
Sempre que abrir um **terminal novo**, entre na pasta e ative de novo:

```bash
cd ~/decode-chatbot
source .venv/bin/activate
```

---

## Parte 5 — Instalar as bibliotecas

### 5.1 Enviar os arquivos para a EC2

No VS Code (conectado à EC2): **File → Open Folder** → `/home/ubuntu/decode-chatbot`.
Depois **arraste** do seu computador para o painel Explorer (à esquerda) estes arquivos:

- `requirements.txt`
- `banco_vetorial.py`
- `app.py`
- `bot.py`
- `teste.py`
- `verificar_chaves.py`
- `DECODE_Base_Conhecimento.pdf`

**Conferir** no terminal:

```bash
ls
```

Esperado: os 7 arquivos acima aparecem na listagem.

### 5.2 Instalar o PyTorch (versão CPU) primeiro

Antes, veja o tipo de processador da máquina:

```bash
uname -m
```

- `x86_64`: processador comum. Siga normalmente.
- `aarch64`: processador ARM (instâncias Graviton, como `t4g`). O PyTorch CPU também existe para ARM, mas
  **teste a instalação** e avise o Eduardo se algo falhar. A instância pedida na Parte 1 (`t3.small`) é x86_64.

Agora instale:

```bash
pip install --no-cache-dir torch --index-url https://download.pytorch.org/whl/cpu
```

- `--index-url` aponta para a versão **só CPU**, bem menor (a EC2 não tem placa de vídeo).
- `--no-cache-dir` evita guardar arquivos temporários e poupa disco.

### 5.3 Instalar o restante

```bash
pip install --no-cache-dir -r requirements.txt
```

`-r requirements.txt` instala tudo o que está listado no arquivo. Leva alguns minutos.

**Conferir:**

```bash
pip list | grep -i -E "fastapi|langchain-groq|chromadb|pytelegrambotapi"
```

Esperado: as 4 bibliotecas aparecem com número de versão.

---

## Parte 6 — Chaves e arquivo `.env`

O `.env` guarda **segredos**. Quem tiver a chave usa a sua conta (e o seu limite gratuito) como se fosse você.
Regras: crie as chaves **você mesmo**, cole **só** no `.env`, e **nunca** envie chave por WhatsApp, e-mail,
GitHub, documento de projeto ou conversa com IA.

### 6.1 Criar a chave da Groq

1. Entre em **console.groq.com** com a sua conta e abra o menu **API Keys**.
2. Clique em **Create API Key**, dê um nome que identifique o uso (ex.: `decode-ec2`) e confirme.
3. **Copie a chave agora**: ela aparece **uma única vez** e começa com `gsk_`. Cole num bloco de notas
   temporário (você vai apagá-lo no fim).
4. Se existir uma chave antiga que já foi exposta, **apague-a** na mesma página (botão de excluir/revogar)
   depois de confirmar que a nova funciona (item 6.4).

> Se você também testa no seu computador, crie uma **segunda chave** (ex.: `decode-pc`). Assim, se uma vazar,
> você revoga só ela.

### 6.2 Criar os dois bots no Telegram (BotFather)

No Telegram, procure **@BotFather** (tem o selo azul de verificado) e converse com ele:

| Você envia | O que acontece |
|---|---|
| `/newbot` | Ele pede o **nome** do bot (aparece na conversa). Ex.: `DECODE Staging` |
| (nome) | Ele pede o **username**, que precisa **terminar em `bot`**. Ex.: `decode_staging_bot` |
| (username) | Ele responde com o **token** (formato `NUMEROS:LETRAS`). Copie e guarde no bloco de notas |

Repita o `/newbot` para o bot de produção (nome `DECODE`, username `decode_bot`). Se o username já existir,
acrescente algo, como `decode_senai_bot`. No fim você tem **dois tokens**: um por bot.

Comandos úteis do BotFather:

- `/mybots` → escolha o bot → **API Token**: mostra o token de novo;
- `/revoke` → escolha o bot: **gera um token novo e invalida o antigo** (use se um token vazou);
- `/deletebot`: apaga um bot que você não usa mais.

### 6.3 Criar o arquivo `.env` na EC2

1. No Explorer do VS Code (pasta `decode-chatbot`) clique em **New File** e dê o nome exato `.env`.
2. Cole o conteúdo de `env.example.txt` e troque os textos pelos valores reais (use o token do
   `decode_staging_bot` enquanto estiver testando):

```
GROQ_API_KEY=sua_chave_da_groq
TELEGRAM_TOKEN=seu_token_do_botfather
API_URL=http://localhost:8000
```

Regras: **sem aspas**, **sem espaços** ao redor do `=`, **nenhuma linha com texto solto**.
Salve com `Ctrl+S`. Depois, deixe o arquivo legível só pelo seu usuário:

```bash
# chmod 600 = só o dono do arquivo lê e escreve (ninguém mais na máquina)
chmod 600 .env
```

**Conferir** (mostra só os nomes, sem revelar as chaves):

```bash
cut -d= -f1 .env
```

Esperado:

```
GROQ_API_KEY
TELEGRAM_TOKEN
API_URL
```

**Qual token usar em cada lugar** (regra: um token = um bot ligado):

| Onde o bot roda | `TELEGRAM_TOKEN` do… |
|---|---|
| Testes (a sua EC2 de testes ou o seu computador) | `decode_staging_bot` |
| Produção (a EC2 que ficará no ar na apresentação) | `decode_bot` |

Nunca deixe os dois ambientes ligados com o mesmo token ao mesmo tempo.

### 6.4 Testar as chaves

```bash
python verificar_chaves.py
```

Esperado (o script **não mostra** as chaves):

```
GROQ_API_KEY: formato ok
TELEGRAM_TOKEN: formato ok
Groq: chave VÁLIDA
Telegram: token VÁLIDO (bot @decode_staging_bot)
TUDO CERTO: o .env está pronto.
```

Se aparecer `INVÁLIDA` ou `revogada`, a chave foi copiada errada ou já foi apagada: crie outra.

Quando tudo estiver certo, **apague o bloco de notas temporário** com as chaves. Se o bot já estiver ligado
pelo `systemd` (Parte 10), reinicie para ele ler o `.env` novo:

```bash
sudo systemctl restart decode-api
sudo systemctl restart decode-bot
```

> Nunca envie o `.env` por e-mail, WhatsApp ou GitHub. O repositório do DECODE é público.

---

## Parte 7 — Criar o banco vetorial (uma vez)

```bash
python banco_vetorial.py
```

**O que acontece, na ordem:**

1. `Páginas carregadas: 19`
2. `Quantidade de chunks: 69` e a lista com o tamanho de cada chunk (um chunk por item da base)
3. Download do modelo de embeddings (**só na primeira vez**, alguns minutos — pode aparecer barra de progresso)
4. `Modelo de embeddings carregado.`
5. `Banco vetorial criado.`

**Conferir:**

```bash
ls
```

Esperado: surgiu a pasta `banco_chroma`.

> **Se precisar refazer** (por exemplo, o PDF mudou): apague antes a pasta com `rm -rf banco_chroma` (no Windows: `rmdir /s /q banco_chroma`) e rode o `banco_vetorial.py` de novo. Sem apagar, os chunks ficam **duplicados**.

---

## Parte 8 — Ligar a API e testar

### 8.1 Ligar a API

```bash
uvicorn app:app --host 0.0.0.0 --port 8000
```

- `app:app` = arquivo `app.py`, objeto `app` dentro dele;
- `--host 0.0.0.0` = aceita conexões de fora da máquina;
- `--port 8000` = porta de acesso.

**Esperado** (no final): `Uvicorn running on http://0.0.0.0:8000`.
O terminal fica "ocupado" — é normal, a API está rodando. **Não feche.**

### 8.2 Testar no navegador

No seu computador, abra `http://IP_PUBLICO_DA_EC2:8000`.
Esperado: `{"mensagem":"API do DECODE Funcionando"}`.

Também funciona a página automática de documentação: `http://IP_PUBLICO_DA_EC2:8000/docs`.

### 8.3 Testar com o `teste.py`

Abra **um segundo terminal** no VS Code (ícone **+** do painel Terminal) e rode:

```bash
cd ~/decode-chatbot
source .venv/bin/activate
```

No arquivo `teste.py`, troque a linha:

```python
ip = "seu_ip_publico_aqui"
```

por:

```python
ip = "localhost"
```

(Dentro da EC2 use `localhost`. Do seu computador, use o IP público.) Execute:

```bash
python teste.py
```

**Esperado:**

- **Teste 1:** status `200` e `{'mensagem': 'API do DECODE Funcionando'}`
- **Teste 2:** status `200`, `rota` = `RAG` e a resposta explicando o que é o DECODE (plataforma SaaS de gestão inteligente de documentos não estruturados)
- **Teste 3:** status `200` e resposta sobre as tecnologias — mesmo a pergunta sendo curta (*"E quais as tecnologias?"*), ela só faz sentido por causa do **histórico** enviado. Isso comprova que o histórico funciona.

A primeira chamada pode demorar um pouco mais (o modelo está "acordando"). No terminal da API aparecem linhas `POST /chat ... 200 OK`.

---

## Parte 9 — Ligar o bot do Telegram

No **segundo terminal** (com `.venv` ativa e dentro de `~/decode-chatbot`; a API continua rodando no primeiro):

```bash
python bot.py
```

**Esperado:**

```
Variáveis do .env carregadas.
Bot criado.
Bot iniciado...
```

No Telegram, abra o bot **de testes** (`decode_staging_bot`) e teste, nesta ordem:

| Você envia | O bot deve… |
|---|---|
| `/start` | Responder com a mensagem de boas-vindas |
| `O que é o DECODE?` | Enviar `Rota: RAG` e depois a resposta |
| `E quais as tecnologias?` | Continuar o assunto (usa o histórico) |
| `Quem faz parte da equipe?` | Citar Robson Schram, Eduardo Mosti, Gabriel Henrique Hausmann e Helio do Nascimento Filho |
| `Como funciona o deploy?` | Explicar CI/CD, ECR, SSM e a volta para a versão anterior |
| `O DECODE já tem OCR?` | Dizer que OCR e a busca no acervo são da v2.0 (o chatbot é só sobre o projeto) |
| `Como será a apresentação?` | Citar o roteiro de 6 minutos e quem apresenta cada parte |
| `Por que existem dois bots?` | Explicar staging x produção e o erro 409 |
| `Qual o telefone de contato?` | Dizer que não consta na base e orientar a consultar a equipe |
| `Bom dia` | Enviar `Rota: NORMAL` e responder normalmente |

> Para o usuário **não** ver a linha "Rota: ...", mude `mostrar_rota = True` para `False` no `bot.py`.

Para parar o bot ou a API: `Ctrl + C` no terminal correspondente.

---

## Parte 10 — Deixar tudo ligado sozinho (systemd)

Rodando nos terminais, API e bot **param** quando você fecha o VS Code. Para ficarem ligados 24 h, use o `systemd` (serviços do Ubuntu).

1. Pare a API e o bot (`Ctrl + C` nos dois terminais).
2. Envie os arquivos de serviço para `~/decode-chatbot/deploy` (arrastando no VS Code) e copie:

```bash
# Copia para a pasta de serviços do sistema (precisa de sudo)
sudo cp ~/decode-chatbot/deploy/decode-api.service /etc/systemd/system/
sudo cp ~/decode-chatbot/deploy/decode-bot.service /etc/systemd/system/
```

3. Peça ao sistema para reler os serviços e ligue-os:

```bash
# Relê os arquivos de serviço
sudo systemctl daemon-reload

# enable = liga sozinho quando a máquina iniciar | --now = liga agora também
sudo systemctl enable --now decode-api
sudo systemctl enable --now decode-bot
```

4. Conferir:

```bash
sudo systemctl status decode-api
sudo systemctl status decode-bot
```

Esperado: `Active: active (running)` em verde nos dois (saia com `q`).

**Comandos úteis:**

```bash
# Ver o que o bot está imprimindo (log) - sair com Ctrl+C
sudo journalctl -u decode-bot -f

# Ver o log da API
sudo journalctl -u decode-api -f

# Reiniciar depois de alterar app.py ou bot.py
sudo systemctl restart decode-api
sudo systemctl restart decode-bot
```

> **Atenção — instalação real na EC2 do DECODE.** Esta Parte 10 descreve a instalação de teste (usuário `ubuntu`, pasta `/home/ubuntu/decode-chatbot`). Na EC2 do projeto o chatbot foi instalado de outra forma: usuário de sistema `decode-chatbot`, pasta `/opt/decode-chatbot`, API só em `127.0.0.1:8000` e limites de memória. Os arquivos `deploy/*.service` deste repositório já refletem essa instalação real. O passo a passo atual de instalação, operação e atualização está em **`docs/implantacao.md`, seção 9**; em caso de divergência com este guia, vale a seção 9.
>
> Se a máquina for criada com outro usuário ou outra pasta, ajuste as linhas `User=`, `WorkingDirectory=`, `ExecStart=` e `HOME` das duas units.

---

## Parte 11 — Atualizar a base de conhecimento

Quando o repositório mudar (por exemplo, a v2.0 for concluída), a base precisa ser refeita:

1. Gere o novo PDF (mantendo o nome `DECODE_Base_Conhecimento.pdf` e o formato: título numerado, perguntas associadas, resposta padrão e `---`).
2. Envie para a pasta `~/decode-chatbot` e rode:

```bash
cd ~/decode-chatbot
source .venv/bin/activate
rm -rf banco_chroma
python banco_vetorial.py
sudo systemctl restart decode-api
```

O `rm -rf banco_chroma` apaga o banco antigo (evita duplicar chunks) e o `restart` faz a API ler o banco novo.

---

## Parte 12 — Problemas comuns

| Sintoma | Causa provável | O que fazer |
|---|---|---|
| `Permission denied (publickey)` ao conectar | Arquivo `.pem` errado ou caminho errado no `config` | Conferir `IdentityFile` e se o usuário é `ubuntu` |
| `ModuleNotFoundError: No module named ...` | `.venv` não está ativa | `source ~/decode-chatbot/.venv/bin/activate` e repita |
| `FileNotFoundError ... DECODE_Base_Conhecimento.pdf` | Comando executado fora da pasta, ou PDF ausente | `cd ~/decode-chatbot` e `ls` |
| API sobe, mas o `/chat` dá erro 500 | `banco_chroma` não existe ou `GROQ_API_KEY` errada | Rodar `banco_vetorial.py`; conferir o `.env` |
| `Connection refused` / página não abre | API desligada ou porta 8000 fechada no Security Group | Ver `systemctl status decode-api`; pedir ao Eduardo a regra da porta 8000 |
| Bot: `409 Conflict: terminated by other getUpdates request` | O **mesmo token** está rodando em outro lugar (Colab, outro terminal, outro ambiente) | Parar a outra instância; um token = um bot ligado. Use o `decode_staging_bot` nos testes e o `decode_bot` só na produção |
| `verificar_chaves.py` diz `INVÁLIDA` ou `revogada` | Chave copiada errada, com espaço sobrando, ou já apagada | Criar outra chave (Parte 6), colar de novo e rodar o script |
| Bot responde "Desculpe, tive um problema..." | API fora do ar ou demorou mais de 120 s | Ver o log: `journalctl -u decode-bot -f` |
| Respostas repetidas ou misturadas após trocar o PDF | `banco_vetorial.py` rodado duas vezes sem apagar o banco | `rm -rf banco_chroma` e rodar de novo |
| A instalação trava ou a API é encerrada sozinha (`Killed`) | Falta de memória (t3.small de 2 GB, ou máquina compartilhada com o site) | Conferir `free -h`; pedir uma máquina com mais memória |
| `No space left on device` | Disco cheio | Conferir `df -h`; usar sempre `--no-cache-dir` no pip |
| Falha ao instalar pacote com `error: ... gcc` | `build-essential` não instalado | Refazer o comando da Parte 3 |
| IP mudou depois de desligar/ligar a EC2 | EC2 sem Elastic IP | Pedir um Elastic IP ao Eduardo; atualizar o `HostName` do `config` |

---

## Parte 13 — Pontos de atenção

- **Segurança da API:** ela não tem senha. Mantenha a porta 8000 liberada apenas para o seu IP (ou fechada). O bot usa `localhost` e não depende dela estar aberta.
- **Custo:** a EC2 cobra enquanto estiver ligada. Combine com o Eduardo quando desligar.
- **Chaves:** `GROQ_API_KEY` e `TELEGRAM_TOKEN` ficam apenas no `.env`. Se uma chave vazar, **revogue na hora** (apague a chave no console da Groq; `/revoke` no BotFather), crie outra (Parte 6) e atualize o `.env`. Apagar a mensagem ou o arquivo onde ela apareceu **não basta**.
- **Reinício do bot:** enquanto o bot está desligado ou reiniciando, o Telegram guarda as mensagens por até 24 horas e
  entrega quando ele volta (por isso o `infinity_polling(skip_pending=True)` do `bot.py` descarta as antigas; troque
  se quiser que elas sejam respondidas).
- **Histórico do bot:** fica na memória do `bot.py`. Se o bot reiniciar, as conversas recomeçam (comportamento igual ao da aula 6.2).
- **Duas chamadas ao modelo por pergunta:** uma para o roteador e outra para o agente. Isso é o padrão do professor (aula 9.4).
- **Base de conhecimento:** vem só dos documentos do repositório. O que o repositório não informa (contatos, valores, notas, endereço público do site) o bot responde que "não consta".
- **Modelo de embeddings:** usamos `paraphrase-multilingual-MiniLM-L12-v2`, a opção multilíngue que o professor deixa comentada no `9.1 - banco_vetorial.py`. O `all-mpnet-base-v2` da aula é treinado principalmente em inglês e, no teste real, não encontrou o item certo para perguntas curtas em português (ex.: "E quais as tecnologias?"). Se trocar de modelo, altere nos **três** arquivos (`banco_vetorial.py`, `app.py` e `diagnostico_busca.py`) e recrie o banco.
- **Aviso do `PyPDFLoader`:** ao rodar o `banco_vetorial.py` pode aparecer um aviso de que o `langchain-community` está sendo descontinuado. É só um aviso; o programa funciona e segue exatamente o código do professor (aula 9.1).

---

## Parte 14 — Levar o chatbot para o repositório do DECODE

O repositório é da equipe e tem regras (veja `CONTRIBUTING.md`). Combine com o Eduardo **onde** colocar. Nas mensagens do
grupo ele falou em "área /chatbot" (14/10) e, depois, em trabalhar na pasta do bloco: `backend/`, `frontend/`,
`ai-worker/` ou `infra/` (14/11). Sugestão: `ai-worker/chatbot/`, que cabe nas duas ideias.

**Combinados do grupo (mensagem do Eduardo de 14/11/2026):**

- A `main` é protegida: tudo entra por Pull Request com **1 aprovação**; **ninguém aprova o próprio PR**.
- Depois de abrir o PR, **avise no grupo com o link** e peça a revisão (a meta é responder em até 24 horas).
- Ele pediu que cada um rode `git rev-list --count HEAD` na pasta do seu projeto local e responda no grupo com o
  número (ou "não é repositório Git"), para decidir como importar o código sem perder o histórico.
- Confira que o `git config user.email` é o **mesmo e-mail da conta do GitHub**; senão o commit não aparece no perfil.

1. Atualize e crie uma branch curta:

```bash
git switch main
git pull
git switch -c feat/chatbot-telegram
```

2. Copie para `ai-worker/chatbot/`: `app.py`, `bot.py`, `banco_vetorial.py`, `teste.py`, `verificar_chaves.py`, `diagnostico_busca.py`, `requirements.txt`, `env.example.txt` (renomeie para `.env.example`), `DECODE_Base_Conhecimento.pdf`, a pasta `deploy/` e este guia (`GUIA_EC2_DECODE.md`).
3. **Não envie:** `.env`, `banco_chroma/`, `.venv/`. O `.gitignore` do projeto já ignora `.env` e `.venv/`, mas **não** lista `banco_chroma/` — acrescente a linha `banco_chroma/` ao `.gitignore` no mesmo PR.
4. Confira antes do commit: rode `git status` (não pode aparecer `.env`, `banco_chroma/` nem `.venv/`) e veja se há token, chave (Groq, Telegram, AWS) ou dado pessoal real no diff. O repositório é **público**.
5. Commit no padrão da equipe (Conventional Commits, em português):

```bash
git add ai-worker/chatbot .gitignore
git commit -m "feat: adiciona chatbot do Telegram com base de conhecimento do DECODE"
git push -u origin feat/chatbot-telegram
```

6. Abra o Pull Request para a `main`, preencha o template (resumo, tipo, como testar, checklist, evidências), **avise o link no grupo** e peça a revisão de outro integrante. Faça **Squash and merge** depois de aprovado e com o CI verde.
7. Depois do merge, volte para a `main` e limpe a branch:

```bash
git switch main
git pull
git branch -d feat/chatbot-telegram
```

> Observações: o CI atual só cobre back-end, front-end e mobile (não há job de Python), e o deploy automático só dispara com mudanças em `backend/`, `frontend/` e `infra/`. Portanto, adicionar o chatbot em `ai-worker/` **não** altera a implantação do site.
