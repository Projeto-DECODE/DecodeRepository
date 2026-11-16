package br.senai.sp.meustorage.data;

import android.content.ContentResolver;
import android.content.Context;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;

import com.google.gson.Gson;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import br.senai.sp.meustorage.api.ApiClient;
import br.senai.sp.meustorage.api.Config;
import br.senai.sp.meustorage.model.Arquivo;
import br.senai.sp.meustorage.model.Bucket;
import br.senai.sp.meustorage.model.ErroApi;
import br.senai.sp.meustorage.model.Pagina;
import br.senai.sp.meustorage.model.ResumoBucket;
import br.senai.sp.meustorage.model.TokenResposta;
import br.senai.sp.meustorage.model.Usuario;
import br.senai.sp.meustorage.util.MensagensErro;
import br.senai.sp.meustorage.util.Sessao;
import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.RequestBody;
import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

// Único ponto de acesso aos dados.
// Se Config.USAR_MOCK for true, responde com MockDados; senão, chama a API com Retrofit.
// As respostas sempre chegam na thread principal (pode mexer na tela direto).
public class StorageRepository {

    private final Sessao sessao;
    private final ContentResolver resolver;
    private final Handler handler = new Handler(Looper.getMainLooper());

    public StorageRepository(Context context) {
        Context app = context.getApplicationContext();
        sessao = new Sessao(app);
        resolver = app.getContentResolver();
        if (Config.USAR_MOCK) {
            MockDados.iniciar();
        }
    }

    // ===================== Autenticação =====================

    // Em caso de sucesso, a sessão (token, expiração e e-mail) já fica salva
    public void login(String email, String senha, RepositorioCallback<Void> callback) {
        if (Config.USAR_MOCK) {
            String erro = MockDados.validarLogin(email, senha);
            if (erro != null) {
                erroMock(callback, erro);
                return;
            }
            sessao.salvarLogin("token-mock-" + UUID.randomUUID(), MockDados.expiracaoSegundos(email), email);
            sucessoMock(callback, null);
            return;
        }

        Map<String, String> corpo = new HashMap<>();
        corpo.put("email", email);
        corpo.put("password", senha);

        ApiClient.auth().login(corpo).enqueue(new Callback<TokenResposta>() {
            @Override
            public void onResponse(@NonNull Call<TokenResposta> call, @NonNull Response<TokenResposta> response) {
                TokenResposta token = response.body();
                if (response.isSuccessful() && token != null) {
                    sessao.salvarLogin(token.getAccessToken(), token.getExpiresIn(), email);
                    callback.onSucesso(null);
                } else {
                    tratarErro(response, false, callback);
                }
            }

            @Override
            public void onFailure(@NonNull Call<TokenResposta> call, @NonNull Throwable t) {
                callback.onErro(MensagensErro.SEM_CONEXAO, null);
            }
        });
    }

    public void cadastrar(String nome, String email, String senha, RepositorioCallback<Usuario> callback) {
        if (Config.USAR_MOCK) {
            Usuario usuario = MockDados.cadastrar(nome, email, senha);
            if (usuario == null) {
                erroMock(callback, MensagensErro.EMAIL_ALREADY_IN_USE);
            } else {
                sucessoMock(callback, usuario);
            }
            return;
        }

        Map<String, String> corpo = new HashMap<>();
        corpo.put("name", nome);
        corpo.put("email", email);
        corpo.put("password", senha);
        ApiClient.auth().cadastrar(corpo).enqueue(repassar(false, callback));
    }

    // ===================== Buckets =====================

    public void listarBuckets(RepositorioCallback<List<Bucket>> callback) {
        if (semSessao(callback)) return;

        if (Config.USAR_MOCK) {
            sucessoMock(callback, MockDados.listarBuckets());
            return;
        }
        ApiClient.buckets().listar(sessao.getCabecalho()).enqueue(repassar(true, callback));
    }

    public void criarBucket(String nome, RepositorioCallback<Bucket> callback) {
        if (semSessao(callback)) return;

        if (Config.USAR_MOCK) {
            if (MockDados.existeBucketComNome(nome)) {
                erroMock(callback, MensagensErro.BUCKET_NAME_ALREADY_EXISTS);
            } else {
                sucessoMock(callback, MockDados.criarBucket(nome));
            }
            return;
        }

        Map<String, String> corpo = new HashMap<>();
        corpo.put("name", nome);
        ApiClient.buckets().criar(sessao.getCabecalho(), corpo).enqueue(repassar(true, callback));
    }

    public void excluirBucket(String bucketId, RepositorioCallback<Void> callback) {
        if (semSessao(callback)) return;

        if (Config.USAR_MOCK) {
            String erro = MockDados.excluirBucket(bucketId);
            if (erro != null) {
                erroMock(callback, erro);
            } else {
                sucessoMock(callback, null);
            }
            return;
        }
        ApiClient.buckets().excluir(sessao.getCabecalho(), bucketId).enqueue(repassar(true, callback));
    }

    // ===================== Arquivos =====================

    public void listarArquivos(String bucketId, int pagina, RepositorioCallback<Pagina<Arquivo>> callback) {
        if (semSessao(callback)) return;

        if (Config.USAR_MOCK) {
            if (!MockDados.existeBucket(bucketId)) {
                erroMock(callback, MensagensErro.BUCKET_NOT_FOUND);
            } else {
                sucessoMock(callback, MockDados.listarArquivos(bucketId, pagina, Config.TAMANHO_PAGINA));
            }
            return;
        }
        ApiClient.objetos()
                .listar(sessao.getCabecalho(), bucketId, pagina, Config.TAMANHO_PAGINA)
                .enqueue(repassar(true, callback));
    }

    // Resumo do bucket (Imagens · Docs · Outros). O back-end não manda isso pronto:
    // o app lê a lista de arquivos de 100 em 100 (limite do object-service) e conta.
    private static final int PAGINA_MAXIMA_BACKEND = 100;
    private static final int LIMITE_PAGINAS_RESUMO = 10; // até 1.000 arquivos por bucket

    public void resumirBucket(String bucketId, RepositorioCallback<ResumoBucket> callback) {
        if (semSessao(callback)) return;

        if (Config.USAR_MOCK) {
            ResumoBucket resumo = MockDados.resumir(bucketId);
            if (resumo == null) {
                erroMock(callback, MensagensErro.BUCKET_NOT_FOUND);
            } else {
                sucessoMock(callback, resumo);
            }
            return;
        }
        resumirPagina(bucketId, 0, new ResumoBucket(), callback);
    }

    private void resumirPagina(String bucketId, int pagina, ResumoBucket resumo,
                               RepositorioCallback<ResumoBucket> callback) {
        ApiClient.objetos()
                .listar(sessao.getCabecalho(), bucketId, pagina, PAGINA_MAXIMA_BACKEND)
                .enqueue(new Callback<Pagina<Arquivo>>() {
                    @Override
                    public void onResponse(@NonNull Call<Pagina<Arquivo>> call, @NonNull Response<Pagina<Arquivo>> response) {
                        Pagina<Arquivo> corpo = response.body();
                        if (!response.isSuccessful() || corpo == null) {
                            tratarErro(response, true, callback);
                            return;
                        }
                        for (Arquivo arquivo : corpo.getConteudo()) {
                            resumo.adicionar(arquivo);
                        }
                        resumo.setTotal(corpo.getTotalElementos());
                        int proxima = pagina + 1;
                        if (proxima < corpo.getTotalPaginas() && proxima < LIMITE_PAGINAS_RESUMO) {
                            resumirPagina(bucketId, proxima, resumo, callback);
                        } else {
                            callback.onSucesso(resumo);
                        }
                    }

                    @Override
                    public void onFailure(@NonNull Call<Pagina<Arquivo>> call, @NonNull Throwable t) {
                        callback.onErro(MensagensErro.SEM_CONEXAO, null);
                    }
                });
    }

    // Lê o arquivo escolhido (fora da thread principal) e envia
    public void enviarArquivo(String bucketId, Uri uri, String nome, String tipo,
                              RepositorioCallback<Arquivo> callback) {
        if (semSessao(callback)) return;

        new Thread(() -> {
            byte[] lidos;
            try {
                lidos = lerComLimite(uri, Config.LIMITE_UPLOAD_BYTES);
            } catch (IOException e) {
                handler.post(() -> callback.onErro(MensagensErro.ERRO_LEITURA, null));
                return;
            }
            final byte[] dados = lidos;
            handler.post(() -> enviarBytes(bucketId, nome, tipo, dados, callback));
        }).start();
    }

    private void enviarBytes(String bucketId, String nome, String tipo, byte[] dados,
                             RepositorioCallback<Arquivo> callback) {
        if (dados == null) {
            callback.onErro(MensagensErro.ARQUIVO_GRANDE, null);
            return;
        }
        if (dados.length == 0) {
            callback.onErro(MensagensErro.EMPTY_FILE, null);
            return;
        }

        if (Config.USAR_MOCK) {
            Arquivo arquivo = MockDados.adicionarArquivo(bucketId, nome, tipo, dados);
            if (arquivo == null) {
                erroMock(callback, MensagensErro.BUCKET_NOT_FOUND);
            } else {
                sucessoMock(callback, arquivo);
            }
            return;
        }

        MediaType mediaType = MediaType.parse(tipo);
        if (mediaType == null) {
            mediaType = MediaType.parse("application/octet-stream");
        }
        RequestBody corpo = RequestBody.create(mediaType, dados);
        MultipartBody.Part parte = MultipartBody.Part.createFormData("file", nome, corpo);
        ApiClient.objetos().enviar(sessao.getCabecalho(), bucketId, parte).enqueue(repassar(true, callback));
    }

    public void excluirArquivo(String bucketId, String arquivoId, RepositorioCallback<Void> callback) {
        if (semSessao(callback)) return;

        if (Config.USAR_MOCK) {
            String erro = MockDados.excluirArquivo(bucketId, arquivoId);
            if (erro != null) {
                erroMock(callback, erro);
            } else {
                sucessoMock(callback, null);
            }
            return;
        }
        ApiClient.objetos().excluir(sessao.getCabecalho(), bucketId, arquivoId).enqueue(repassar(true, callback));
    }

    // Bytes do arquivo para a prévia (só chamar para arquivos de até 10 MB)
    public void carregarPrevia(Arquivo arquivo, RepositorioCallback<byte[]> callback) {
        baixarConteudo(arquivo, StorageRepository::lerTudo, callback);
    }

    // Grava o arquivo no local escolhido pelo usuário (CreateDocument)
    public void salvarArquivo(Arquivo arquivo, Uri destino, RepositorioCallback<Void> callback) {
        baixarConteudo(arquivo, entrada -> {
            try (OutputStream saida = resolver.openOutputStream(destino)) {
                if (saida == null) {
                    throw new IOException("Destino indisponível");
                }
                byte[] buffer = new byte[8192];
                int lidos;
                while ((lidos = entrada.read(buffer)) != -1) {
                    saida.write(buffer, 0, lidos);
                }
            }
            return null;
        }, callback);
    }

    // ===================== Auxiliares =====================

    // O que fazer com os bytes baixados (roda fora da thread principal)
    private interface Leitor<T> {
        T ler(InputStream entrada) throws IOException;
    }

    private <T> void baixarConteudo(Arquivo arquivo, Leitor<T> leitor, RepositorioCallback<T> callback) {
        if (semSessao(callback)) return;

        if (Config.USAR_MOCK) {
            handler.postDelayed(() -> {
                byte[] dados = MockDados.conteudo(arquivo);
                if (dados == null) {
                    callback.onErro(MensagensErro.OBJECT_NOT_FOUND, null);
                } else {
                    lerEmSegundoPlano(new ByteArrayInputStream(dados), leitor, callback);
                }
            }, Config.ATRASO_MOCK_MS);
            return;
        }

        ApiClient.objetos()
                .baixar(sessao.getCabecalho(), arquivo.getBucketId(), arquivo.getId())
                .enqueue(new Callback<ResponseBody>() {
                    @Override
                    public void onResponse(@NonNull Call<ResponseBody> call, @NonNull Response<ResponseBody> response) {
                        ResponseBody corpo = response.body();
                        if (response.isSuccessful() && corpo != null) {
                            // Com @Streaming, ler os bytes usa a rede: não pode ser na thread principal
                            lerEmSegundoPlano(corpo.byteStream(), leitor, callback);
                        } else {
                            tratarErro(response, true, callback);
                        }
                    }

                    @Override
                    public void onFailure(@NonNull Call<ResponseBody> call, @NonNull Throwable t) {
                        callback.onErro(MensagensErro.SEM_CONEXAO, null);
                    }
                });
    }

    private <T> void lerEmSegundoPlano(InputStream entrada, Leitor<T> leitor, RepositorioCallback<T> callback) {
        new Thread(() -> {
            try (InputStream in = entrada) {
                T resultado = leitor.ler(in);
                handler.post(() -> callback.onSucesso(resultado));
            } catch (IOException e) {
                handler.post(() -> callback.onErro(MensagensErro.ERRO_DOWNLOAD, null));
            }
        }).start();
    }

    private static byte[] lerTudo(InputStream entrada) throws IOException {
        ByteArrayOutputStream saida = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int lidos;
        while ((lidos = entrada.read(buffer)) != -1) {
            saida.write(buffer, 0, lidos);
        }
        return saida.toByteArray();
    }

    // Retorna null se passar do limite
    private byte[] lerComLimite(Uri uri, long limite) throws IOException {
        try (InputStream entrada = resolver.openInputStream(uri)) {
            if (entrada == null) {
                throw new IOException("Arquivo indisponível");
            }
            ByteArrayOutputStream saida = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            long total = 0;
            int lidos;
            while ((lidos = entrada.read(buffer)) != -1) {
                total += lidos;
                if (total > limite) {
                    return null;
                }
                saida.write(buffer, 0, lidos);
            }
            return saida.toByteArray();
        }
    }

    // Sessão vencida (passou dos 15 min): nem chama o servidor
    private boolean semSessao(RepositorioCallback<?> callback) {
        if (sessao.isValida()) {
            return false;
        }
        handler.post(() -> callback.onErro(MensagensErro.UNAUTHORIZED, null));
        return true;
    }

    private <T> void sucessoMock(RepositorioCallback<T> callback, T valor) {
        handler.postDelayed(() -> callback.onSucesso(valor), Config.ATRASO_MOCK_MS);
    }

    private void erroMock(RepositorioCallback<?> callback, String codigo) {
        handler.postDelayed(() -> callback.onErro(codigo, null), Config.ATRASO_MOCK_MS);
    }

    // Callback padrão do Retrofit para o RepositorioCallback
    private <T> Callback<T> repassar(boolean rotaProtegida, RepositorioCallback<T> callback) {
        return new Callback<T>() {
            @Override
            public void onResponse(@NonNull Call<T> call, @NonNull Response<T> response) {
                if (response.isSuccessful()) {
                    callback.onSucesso(response.body());
                } else {
                    tratarErro(response, rotaProtegida, callback);
                }
            }

            @Override
            public void onFailure(@NonNull Call<T> call, @NonNull Throwable t) {
                callback.onErro(MensagensErro.SEM_CONEXAO, null);
            }
        };
    }

    // Lê o JSON de erro do back-end ({ code, message, ... })
    private void tratarErro(Response<?> response, boolean rotaProtegida, RepositorioCallback<?> callback) {
        // 401 em rota protegida = token vencido ou inválido
        if (rotaProtegida && response.code() == 401) {
            callback.onErro(MensagensErro.UNAUTHORIZED, null);
            return;
        }
        ErroApi erro = null;
        try (ResponseBody corpoErro = response.errorBody()) {
            if (corpoErro != null) {
                erro = new Gson().fromJson(corpoErro.string(), ErroApi.class);
            }
        } catch (Exception e) {
            erro = null;
        }
        String codigo = (erro != null && erro.getCodigo() != null) ? erro.getCodigo() : MensagensErro.HTTP_ERROR;
        String mensagem = erro != null ? erro.getMensagem() : null;
        callback.onErro(codigo, mensagem);
    }
}
