# MeuStorage: como abrir

Este zip é o **projeto completo**. Basta abri-lo no Android Studio.

## 1. Extrair

Botão direito no zip → **Extrair tudo…** → escolha uma pasta **sem acentos nem espaços**
(ex.: `C:\Users\SEU_USUARIO\AndroidStudioProjects`) → **Extrair**.

## 2. Abrir

1. No Android Studio: menu **File** → **Open…** (na tela de boas-vindas, botão **Open**).
2. Selecione a pasta **MeuStorage** que contém `settings.gradle` e `gradlew` (não a pasta `app`) → **OK**.
3. Se perguntar "Trust Project?", clique em **Trust Project**.
4. Espere o Sync terminar (barra no rodapé). Na primeira vez, ele baixa o Gradle 8.13 e as bibliotecas (alguns minutos).

Se aparecer uma sugestão de **upgrade do Android Gradle Plugin**, pode fechar e não atualizar.

## 3. Rodar

1. Menu **Build** → **Make Project**. A aba **Build** deve terminar em "BUILD SUCCESSFUL".
2. Escolha o Xiaomi na lista de dispositivos e clique no **▶ verde**.

## 4. Testar no modo mock (sem back-end)

O app começa em `Config.USAR_MOCK = true`. Contas de teste (senha `12345678`):

| E-mail | Para quê |
|---|---|
| `teste@teste.com` | Conta normal |
| `existe@teste.com` | Cadastrar com ele mostra "Este e-mail já está cadastrado." |
| `expira@teste.com` | Sessão de 60 segundos: depois disso, qualquer ação volta ao login |

Buckets de exemplo:

- `documentos`, `contratos`: arquivos pdf e xlsx. Arrastar para excluir → o bucket volta com "Esvazie o bucket antes de excluí-lo."
- `fotos-obra`: 26 imagens → testa a paginação (20 + 6) e a prévia. `panorama-obra.jpg` tem mais de 10 MB e mostra só o ícone.
- `relatorios-mensais`: planilhas → ícone verde de planilha.
- `videos-treinamento`: vídeos e uma imagem → ícone laranja ("outros").
- `backups`: vazio → pasta verde; pode ser excluído.
- Criar um bucket com nome repetido → "Já existe um bucket com esse nome."

### Tela "Meus buckets": 3 modos

Os 3 botões ao lado do "Ordenar por" trocam o modo (o app lembra a última escolha):

- **Rotativo**: carrossel. Deslize para os lados ou toque num cartão do lado para trazê-lo ao centro. Tocar no cartão do centro abre o bucket.
- **Grade**: 2 por fileira no celular (4 no tablet, 5 em tablet grande). Arrastar o cartão para o lado exclui.
- **Lista**: faixas na horizontal. Arrastar para o lado exclui.

Em todos: busca instantânea, ordem (recentes, nome, mais arquivos), números **Imagens · Docs · Outros** e "Excluir" com "Desfazer".

Os dados do mock ficam na memória e somem quando o app é fechado.

## 5. Ver o layout do tablet sem emulador

1. Abra `res/layout-sw600dp/activity_arquivos.xml` e clique em **Design** (canto superior direito do editor).
2. Na barra acima do desenho, clique no nome do aparelho (ex.: "Pixel") e escolha **Medium Tablet**.
3. Abra `res/layout/activity_arquivos.xml` e deixe em um celular (ex.: **Pixel 8**) para comparar.
4. Para ver as outras telas no tablet, abra o XML delas e troque o aparelho do mesmo jeito.
   Os formulários ficam limitados a 480 dp e a lista de buckets a 720 dp.

## 6. Ligar a API real

1. Abra `java/br.senai.sp.meustorage/api/Config.java` e troque `USAR_MOCK = true` por `USAR_MOCK = false`.
2. Com o back-end rodando no computador e o celular no cabo, clique na aba **Terminal** (rodapé do Android Studio) e rode:

   ```
   adb reverse tcp:8081 tcp:8081
   adb reverse tcp:8082 tcp:8082
   adb reverse tcp:8083 tcp:8083
   ```

   O `adb reverse` faz o `localhost` **do celular** apontar para o `localhost` **do computador** pelo cabo USB.
   **Repita sempre que reconectar o cabo.**
   Se o Terminal disser que `adb` não existe, use o caminho completo:
   `%LOCALAPPDATA%\Android\Sdk\platform-tools\adb reverse tcp:8081 tcp:8081` (e o mesmo para 8082 e 8083).
3. Rode o app de novo (▶).

No emulador, os endereços em `Config.java` seriam `http://10.0.2.2:PORTA/`.

## 7. Testes automáticos

Ficam em `app/src/test/java` (31 testes: formatos, nome do bucket, resumo
Imagens · Docs · Outros e o modo mock). Rodam no computador, sem emulador.

No Android Studio: clique com o botão direito na pasta `app/src/test/java`
→ **Run 'Tests in java'**. Ou, no terminal do Android Studio:

```
gradlew test
```

## Arquivos do projeto

```
settings.gradle, build.gradle, gradle.properties, gradle/ (wrapper + libs.versions.toml)
app/build.gradle
app/src/main/AndroidManifest.xml
app/src/main/java/br/senai/sp/meustorage/
├── LoginActivity, CadastroActivity, BucketsActivity, ArquivosActivity, DetalheArquivoActivity
├── model/   Usuario, Bucket, Arquivo, Pagina, TokenResposta, ErroApi
├── api/     Config, AuthApiService, BucketApiService, ObjectApiService, ApiClient
├── data/    StorageRepository, MockDados, RepositorioCallback
├── adapter/ BucketAdapter, ArquivoAdapter
└── util/    Sessao, Formatos, MensagensErro, PainelDetalhe
app/src/main/res/
├── layout/          activity_login, activity_cadastro, activity_buckets, activity_arquivos,
│                    activity_detalhe_arquivo, conteudo_detalhe, item_bucket, item_arquivo, dialog_novo_bucket
├── layout-sw600dp/  activity_arquivos (lista + painel de detalhe)
├── menu/            menu_buckets
├── drawable/        11 ícones vetoriais + bg_campo + bg_icone
└── values/          colors, dimens, strings, themes
```
