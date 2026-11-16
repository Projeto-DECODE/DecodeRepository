package br.senai.sp.meustorage;

import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.constraintlayout.widget.Group;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.snackbar.Snackbar;

import br.senai.sp.meustorage.adapter.ArquivoAdapter;
import br.senai.sp.meustorage.api.Config;
import br.senai.sp.meustorage.data.RepositorioCallback;
import br.senai.sp.meustorage.data.StorageRepository;
import br.senai.sp.meustorage.model.Arquivo;
import br.senai.sp.meustorage.model.Bucket;
import br.senai.sp.meustorage.model.Pagina;
import br.senai.sp.meustorage.util.MensagensErro;
import br.senai.sp.meustorage.util.PainelDetalhe;

public class ArquivosActivity extends AppCompatActivity implements PainelDetalhe.Acoes {

    public static final String EXTRA_BUCKET = "bucket";
    private static final String ESTADO_SELECIONADO = "selecionado";
    private static final String ESTADO_PARA_BAIXAR = "paraBaixar";

    private enum Estado { CARREGANDO, CONTEUDO, VAZIO, ERRO }

    private Bucket bucket;
    private StorageRepository repositorio;
    private ArquivoAdapter adapter;

    private MaterialToolbar toolbar;
    private RecyclerView recyclerArquivos;
    private ProgressBar progressCarregando;
    private ProgressBar progressEnvio;
    private TextView txtVazio;
    private TextView txtErro;
    private Group grupoErro;
    private FloatingActionButton fabEnviar;

    // Só existem no tablet (layout-sw600dp)
    private PainelDetalhe painel;
    private View txtSelecione;

    // Paginação
    private int paginaAtual = 0;
    private int totalPaginas = 0;
    private boolean carregandoPagina = false;
    private int geracao = 0; // ignora respostas antigas depois de um recarregar()

    // Exclusão adiada (mesma ideia da BucketsActivity)
    private Snackbar snackbarExclusao;
    private Arquivo arquivoPendente;

    private Arquivo arquivoSelecionado; // tablet
    private Arquivo arquivoParaBaixar;

    private final ActivityResultLauncher<String> launcherEscolher =
            registerForActivityResult(new ActivityResultContracts.GetContent(), uri -> {
                if (uri != null) {
                    enviar(uri);
                }
            });

    // Celular: a tela de detalhe avisa (RESULT_OK) quando excluiu o arquivo
    private final ActivityResultLauncher<Intent> launcherDetalhe =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), resultado -> {
                if (resultado.getResultCode() == RESULT_OK) {
                    recarregar();
                }
            });

    // Tablet: o usuário escolhe onde salvar o download
    private final ActivityResultLauncher<String> launcherSalvar =
            registerForActivityResult(new ActivityResultContracts.CreateDocument("*/*"), uri -> {
                if (uri != null && arquivoParaBaixar != null && painel != null) {
                    painel.salvar(arquivoParaBaixar, uri);
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_arquivos);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets barras = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(barras.left, barras.top, barras.right, barras.bottom);
            return insets;
        });

        bucket = (Bucket) getIntent().getSerializableExtra(EXTRA_BUCKET);
        if (bucket == null) {
            finish();
            return;
        }

        repositorio = new StorageRepository(this);

        toolbar = findViewById(R.id.toolbar);
        toolbar.setTitle(bucket.getNome());
        toolbar.setNavigationOnClickListener(v -> finish());

        recyclerArquivos = findViewById(R.id.recyclerArquivos);
        progressCarregando = findViewById(R.id.progressCarregando);
        progressEnvio = findViewById(R.id.progressEnvio);
        txtVazio = findViewById(R.id.txtVazio);
        txtErro = findViewById(R.id.txtErro);
        grupoErro = findViewById(R.id.grupoErro);
        fabEnviar = findViewById(R.id.fabEnviar);
        View btnTentar = findViewById(R.id.btnTentar);

        adapter = new ArquivoAdapter(this::aoClicarArquivo);
        recyclerArquivos.setLayoutManager(new LinearLayoutManager(this));
        recyclerArquivos.setAdapter(adapter);

        // Chegou no fim da lista: busca a próxima página
        recyclerArquivos.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView rv, int dx, int dy) {
                if (dy > 0 && !rv.canScrollVertically(1)) {
                    carregarProximaPagina();
                }
            }
        });

        new ItemTouchHelper(new ItemTouchHelper.SimpleCallback(0,
                ItemTouchHelper.LEFT | ItemTouchHelper.RIGHT) {
            @Override
            public boolean onMove(@NonNull RecyclerView rv, @NonNull RecyclerView.ViewHolder vh,
                                  @NonNull RecyclerView.ViewHolder alvo) {
                return false;
            }

            @Override
            public void onSwiped(@NonNull RecyclerView.ViewHolder vh, int direcao) {
                int pos = vh.getBindingAdapterPosition();
                if (pos != RecyclerView.NO_POSITION) {
                    excluirComDesfazer(pos);
                }
            }
        }).attachToRecyclerView(recyclerArquivos);

        fabEnviar.setOnClickListener(v -> launcherEscolher.launch("*/*"));
        btnTentar.setOnClickListener(v -> recarregar());

        // Tablet ou celular? Se o painel existe no layout, é tablet.
        View painelView = findViewById(R.id.painelDetalhe);
        if (painelView != null) {
            painel = new PainelDetalhe(this, painelView, repositorio, this);
            txtSelecione = findViewById(R.id.txtSelecione);
            esconderPainel();
        }

        if (savedInstanceState != null) {
            arquivoSelecionado = (Arquivo) savedInstanceState.getSerializable(ESTADO_SELECIONADO);
            arquivoParaBaixar = (Arquivo) savedInstanceState.getSerializable(ESTADO_PARA_BAIXAR);
            if (painel != null && arquivoSelecionado != null) {
                mostrarNoPainel(arquivoSelecionado);
            }
        }

        recarregar();
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putSerializable(ESTADO_SELECIONADO, arquivoSelecionado);
        outState.putSerializable(ESTADO_PARA_BAIXAR, arquivoParaBaixar);
    }

    @Override
    protected void onStop() {
        super.onStop();
        executarExclusaoPendente();
        if (snackbarExclusao != null) {
            snackbarExclusao.dismiss();
        }
    }

    // ---------- Lista paginada ----------

    // Volta para a página 0
    private void recarregar() {
        geracao++;
        paginaAtual = 0;
        totalPaginas = 0;
        mudarEstado(Estado.CARREGANDO);
        carregarPagina(0);
    }

    private void carregarProximaPagina() {
        if (!carregandoPagina && paginaAtual + 1 < totalPaginas) {
            carregarPagina(paginaAtual + 1);
        }
    }

    private void carregarPagina(int pagina) {
        carregandoPagina = true;
        final int minhaGeracao = geracao;

        repositorio.listarArquivos(bucket.getId(), pagina, new RepositorioCallback<Pagina<Arquivo>>() {
            @Override
            public void onSucesso(Pagina<Arquivo> resultado) {
                if (isDestroyed() || minhaGeracao != geracao) return;
                carregandoPagina = false;
                paginaAtual = pagina;
                totalPaginas = resultado.getTotalPaginas();

                int total = (int) resultado.getTotalElementos();
                toolbar.setSubtitle(getResources().getQuantityString(R.plurals.qtd_arquivos, total, total));

                if (pagina == 0) {
                    adapter.setLista(resultado.getConteudo());
                } else {
                    adapter.adicionar(resultado.getConteudo());
                }
                mudarEstado(adapter.getItemCount() == 0 ? Estado.VAZIO : Estado.CONTEUDO);
            }

            @Override
            public void onErro(String codigo, String mensagem) {
                if (isDestroyed() || minhaGeracao != geracao) return;
                carregandoPagina = false;
                if (MensagensErro.sessaoExpirou(ArquivosActivity.this, codigo)) return;

                String texto = MensagensErro.texto(ArquivosActivity.this, codigo, mensagem);
                if (pagina == 0) {
                    txtErro.setText(texto);
                    mudarEstado(Estado.ERRO);
                } else {
                    Toast.makeText(ArquivosActivity.this, texto, Toast.LENGTH_LONG).show();
                }
            }
        });
    }

    private void mudarEstado(Estado estado) {
        progressCarregando.setVisibility(estado == Estado.CARREGANDO ? View.VISIBLE : View.GONE);
        recyclerArquivos.setVisibility(estado == Estado.CONTEUDO ? View.VISIBLE : View.GONE);
        txtVazio.setVisibility(estado == Estado.VAZIO ? View.VISIBLE : View.GONE);
        grupoErro.setVisibility(estado == Estado.ERRO ? View.VISIBLE : View.GONE);
    }

    // ---------- Clique: celular abre a tela, tablet usa o painel ----------

    private void aoClicarArquivo(Arquivo arquivo) {
        if (painel != null) {
            arquivoSelecionado = arquivo;
            mostrarNoPainel(arquivo);
        } else {
            Intent intent = new Intent(this, DetalheArquivoActivity.class);
            intent.putExtra(DetalheArquivoActivity.EXTRA_ARQUIVO, arquivo);
            intent.putExtra(DetalheArquivoActivity.EXTRA_BUCKET, bucket);
            launcherDetalhe.launch(intent);
        }
    }

    private void mostrarNoPainel(Arquivo arquivo) {
        txtSelecione.setVisibility(View.GONE);
        painel.mostrar(arquivo, bucket);
    }

    private void esconderPainel() {
        arquivoSelecionado = null;
        painel.esconder();
        txtSelecione.setVisibility(View.VISIBLE);
    }

    // Botões do painel do tablet (PainelDetalhe.Acoes)
    @Override
    public void aoBaixar(Arquivo arquivo) {
        arquivoParaBaixar = arquivo;
        launcherSalvar.launch(arquivo.getNomeOriginal());
    }

    @Override
    public void aoExcluir(Arquivo arquivo) {
        painel.setBotoesHabilitados(false);
        repositorio.excluirArquivo(bucket.getId(), arquivo.getId(), new RepositorioCallback<Void>() {
            @Override
            public void onSucesso(Void resultado) {
                if (isDestroyed()) return;
                Toast.makeText(ArquivosActivity.this, R.string.arquivo_excluido, Toast.LENGTH_SHORT).show();
                esconderPainel();
                recarregar();
            }

            @Override
            public void onErro(String codigo, String mensagem) {
                if (isDestroyed()) return;
                painel.setBotoesHabilitados(true);
                if (MensagensErro.sessaoExpirou(ArquivosActivity.this, codigo)) return;
                Toast.makeText(ArquivosActivity.this,
                        MensagensErro.texto(ArquivosActivity.this, codigo, mensagem),
                        Toast.LENGTH_LONG).show();
            }
        });
    }

    // ---------- Envio ----------

    private void enviar(Uri uri) {
        String nome = null;
        long tamanho = -1; // -1 = desconhecido

        try (Cursor cursor = getContentResolver().query(uri, null, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                int colNome = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                int colTamanho = cursor.getColumnIndex(OpenableColumns.SIZE);
                if (colNome >= 0) {
                    nome = cursor.getString(colNome);
                }
                if (colTamanho >= 0 && !cursor.isNull(colTamanho)) {
                    tamanho = cursor.getLong(colTamanho);
                }
            }
        } catch (Exception e) {
            // Segue sem nome/tamanho; o Repository confere de novo ao ler
        }

        if (nome == null || nome.isEmpty()) {
            nome = "arquivo";
        }
        if (tamanho == 0) {
            Toast.makeText(this, R.string.erro_arquivo_vazio, Toast.LENGTH_LONG).show();
            return;
        }
        if (tamanho > Config.LIMITE_UPLOAD_BYTES) {
            Toast.makeText(this, R.string.erro_arquivo_grande, Toast.LENGTH_LONG).show();
            return;
        }

        String tipo = getContentResolver().getType(uri);
        if (tipo == null) {
            tipo = "application/octet-stream";
        }

        enviando(true);
        repositorio.enviarArquivo(bucket.getId(), uri, nome, tipo, new RepositorioCallback<Arquivo>() {
            @Override
            public void onSucesso(Arquivo arquivo) {
                if (isDestroyed()) return;
                enviando(false);
                Toast.makeText(ArquivosActivity.this, R.string.arquivo_enviado, Toast.LENGTH_SHORT).show();
                recarregar();
            }

            @Override
            public void onErro(String codigo, String mensagem) {
                if (isDestroyed()) return;
                enviando(false);
                if (MensagensErro.sessaoExpirou(ArquivosActivity.this, codigo)) return;
                Toast.makeText(ArquivosActivity.this,
                        MensagensErro.texto(ArquivosActivity.this, codigo, mensagem),
                        Toast.LENGTH_LONG).show();
            }
        });
    }

    private void enviando(boolean sim) {
        progressEnvio.setVisibility(sim ? View.VISIBLE : View.GONE);
        fabEnviar.setEnabled(!sim);
    }

    // ---------- Excluir com "Desfazer" ----------

    private void excluirComDesfazer(int posicao) {
        executarExclusaoPendente();

        Arquivo arquivo = adapter.remover(posicao);
        arquivoPendente = arquivo;
        if (adapter.getItemCount() == 0) {
            mudarEstado(Estado.VAZIO);
        }
        if (painel != null && arquivoSelecionado != null && arquivoSelecionado.getId().equals(arquivo.getId())) {
            esconderPainel();
        }

        snackbarExclusao = Snackbar.make(findViewById(R.id.main), R.string.arquivo_excluido, Snackbar.LENGTH_LONG)
                .setAnchorView(fabEnviar)
                .setAction(R.string.desfazer, v -> {
                    arquivoPendente = null;
                    adapter.inserir(posicao, arquivo);
                    mudarEstado(Estado.CONTEUDO);
                })
                .addCallback(new Snackbar.Callback() {
                    @Override
                    public void onDismissed(Snackbar barra, int evento) {
                        if (evento != DISMISS_EVENT_ACTION && arquivoPendente == arquivo) {
                            executarExclusaoPendente();
                        }
                    }
                });
        snackbarExclusao.show();
    }

    private void executarExclusaoPendente() {
        if (arquivoPendente == null) {
            return;
        }
        Arquivo arquivo = arquivoPendente;
        arquivoPendente = null;

        repositorio.excluirArquivo(bucket.getId(), arquivo.getId(), new RepositorioCallback<Void>() {
            @Override
            public void onSucesso(Void resultado) {
                if (isDestroyed()) return;
                // Recarrega da página 0 (se não houver outra exclusão esperando)
                if (arquivoPendente == null) {
                    recarregar();
                }
            }

            @Override
            public void onErro(String codigo, String mensagem) {
                if (isDestroyed()) return;
                if (MensagensErro.sessaoExpirou(ArquivosActivity.this, codigo)) return;
                Toast.makeText(ArquivosActivity.this,
                        MensagensErro.texto(ArquivosActivity.this, codigo, mensagem),
                        Toast.LENGTH_LONG).show();
                // O arquivo continua no servidor: recarregar faz ele voltar
                if (arquivoPendente == null) {
                    recarregar();
                }
            }
        });
    }
}
