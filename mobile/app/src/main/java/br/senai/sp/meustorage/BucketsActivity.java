package br.senai.sp.meustorage;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.constraintlayout.widget.Group;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.PagerSnapHelper;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.SimpleItemAnimator;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.button.MaterialButtonToggleGroup;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.snackbar.Snackbar;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import br.senai.sp.meustorage.adapter.BucketAdapter;
import br.senai.sp.meustorage.data.RepositorioCallback;
import br.senai.sp.meustorage.data.StorageRepository;
import br.senai.sp.meustorage.model.Bucket;
import br.senai.sp.meustorage.model.ResumoBucket;
import br.senai.sp.meustorage.util.Formatos;
import br.senai.sp.meustorage.util.MensagensErro;
import br.senai.sp.meustorage.util.Preferencias;
import br.senai.sp.meustorage.util.Sessao;

public class BucketsActivity extends AppCompatActivity implements BucketAdapter.Acao {

    // Mesmo regex do back-end

    private static final int ORDEM_RECENTES = 0;
    private static final int ORDEM_NOME = 1;
    private static final int ORDEM_ARQUIVOS = 2;

    private enum Estado { CARREGANDO, CONTEUDO, VAZIO, ERRO }

    private TextView txtQuantidade;
    private RecyclerView recyclerBuckets;
    private ProgressBar progressCarregando;
    private TextView txtVazio;
    private TextView txtErro;
    private Group grupoErro;
    private FloatingActionButton fabNovoBucket;
    private View rodapeRotativo;
    private View brilho;
    private LinearLayout pontos;
    private MaterialButton btnCriarNovo;

    private BucketAdapter adapter;
    private StorageRepository repositorio;
    private Sessao sessao;
    private Preferencias preferencias;

    // Todos os buckets do servidor e os que aparecem (depois da busca e da ordem)
    private final List<Bucket> todos = new ArrayList<>();
    private final List<Bucket> visiveis = new ArrayList<>();
    private String busca = "";
    private int ordem = ORDEM_RECENTES;
    private Estado estado = Estado.CARREGANDO;
    private boolean jaCarregou = false;

    // Modo rotativo
    private final PagerSnapHelper snapHelper = new PagerSnapHelper();
    private LinearLayoutManager layoutRotativo;
    private String idCentro = ""; // bucket que está no centro

    // Exclusão adiada: só acontece de verdade quando o Snackbar some
    private Snackbar snackbarExclusao;
    private Bucket bucketPendente;
    private int posicaoPendente;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_buckets);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets barras = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(barras.left, barras.top, barras.right, barras.bottom);
            return insets;
        });

        repositorio = new StorageRepository(this);
        sessao = new Sessao(this);
        preferencias = new Preferencias(this);

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.inflateMenu(R.menu.menu_buckets);
        toolbar.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == R.id.acao_perfil) {
                mostrarPerfil();
                return true;
            } else if (item.getItemId() == R.id.acao_sair) {
                sair();
                return true;
            }
            return false;
        });

        TextView txtSaudacao = findViewById(R.id.txtSaudacao);
        txtSaudacao.setText(getString(R.string.saudacao, sessao.getNomeExibicao()));

        txtQuantidade = findViewById(R.id.txtQuantidade);
        recyclerBuckets = findViewById(R.id.recyclerBuckets);
        progressCarregando = findViewById(R.id.progressCarregando);
        txtVazio = findViewById(R.id.txtVazio);
        txtErro = findViewById(R.id.txtErro);
        grupoErro = findViewById(R.id.grupoErro);
        fabNovoBucket = findViewById(R.id.fabNovoBucket);
        rodapeRotativo = findViewById(R.id.rodapeRotativo);
        brilho = findViewById(R.id.brilho);
        pontos = findViewById(R.id.pontos);
        btnCriarNovo = findViewById(R.id.btnCriarNovo);
        View btnTentar = findViewById(R.id.btnTentar);
        EditText edtBusca = findViewById(R.id.edtBusca);
        Spinner spnOrdem = findViewById(R.id.spnOrdem);
        MaterialButtonToggleGroup grupoModo = findViewById(R.id.grupoModo);

        adapter = new BucketAdapter(this);
        recyclerBuckets.setAdapter(adapter);
        // Sem a animação de "piscar" quando o cartão do centro muda
        if (recyclerBuckets.getItemAnimator() instanceof SimpleItemAnimator) {
            ((SimpleItemAnimator) recyclerBuckets.getItemAnimator()).setSupportsChangeAnimations(false);
        }

        // Rotativo: aumenta o cartão do centro e diminui os dos lados enquanto rola
        recyclerBuckets.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView rv, int dx, int dy) {
                if (adapter.getModo() == BucketAdapter.MODO_ROTATIVO) {
                    aplicarEfeitoRotativo();
                }
            }

            @Override
            public void onScrollStateChanged(@NonNull RecyclerView rv, int novoEstado) {
                if (novoEstado == RecyclerView.SCROLL_STATE_IDLE && adapter.getModo() == BucketAdapter.MODO_ROTATIVO) {
                    atualizarCentro();
                }
            }
        });

        // Grade e lista: arrastar o cartão para o lado exclui (no rotativo o arraste troca de bucket)
        new ItemTouchHelper(new ItemTouchHelper.SimpleCallback(0,
                ItemTouchHelper.LEFT | ItemTouchHelper.RIGHT) {
            @Override
            public boolean onMove(@NonNull RecyclerView rv, @NonNull RecyclerView.ViewHolder vh,
                                  @NonNull RecyclerView.ViewHolder alvo) {
                return false;
            }

            @Override
            public int getSwipeDirs(@NonNull RecyclerView rv, @NonNull RecyclerView.ViewHolder vh) {
                int pos = vh.getBindingAdapterPosition();
                if (adapter.getModo() == BucketAdapter.MODO_ROTATIVO || pos == RecyclerView.NO_POSITION
                        || adapter.getItem(pos).isExcluindo()) {
                    return 0;
                }
                return super.getSwipeDirs(rv, vh);
            }

            @Override
            public void onSwiped(@NonNull RecyclerView.ViewHolder vh, int direcao) {
                int pos = vh.getBindingAdapterPosition();
                if (pos != RecyclerView.NO_POSITION) {
                    excluirComDesfazer(adapter.getItem(pos));
                }
            }
        }).attachToRecyclerView(recyclerBuckets);

        // Busca instantânea
        edtBusca.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                busca = s.toString().trim().toLowerCase(Locale.ROOT);
                atualizarLista();
            }
        });

        spnOrdem.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int posicao, long id) {
                if (posicao != ordem) {
                    ordem = posicao;
                    atualizarLista();
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });

        // Modo de exibição (fica salvo no celular)
        int modoSalvo = preferencias.getModo();
        grupoModo.check(modoSalvo == BucketAdapter.MODO_GRADE ? R.id.btnModoGrade
                : modoSalvo == BucketAdapter.MODO_LISTA ? R.id.btnModoLista : R.id.btnModoRotativo);
        grupoModo.addOnButtonCheckedListener((grupo, idBotao, marcado) -> {
            if (!marcado) return;
            int modo = idBotao == R.id.btnModoGrade ? BucketAdapter.MODO_GRADE
                    : idBotao == R.id.btnModoLista ? BucketAdapter.MODO_LISTA : BucketAdapter.MODO_ROTATIVO;
            preferencias.salvarModo(modo);
            aplicarModo(modo);
        });
        aplicarModo(modoSalvo);

        fabNovoBucket.setOnClickListener(v -> mostrarDialogoNovoBucket());
        btnCriarNovo.setOnClickListener(v -> mostrarDialogoNovoBucket());
        btnTentar.setOnClickListener(v -> carregar());

        carregar();
    }

    @Override
    protected void onResume() {
        super.onResume();
        adapter.setIdDestaque(preferencias.getUltimoBucket());
        // Voltou de um bucket: os números podem ter mudado (envio ou exclusão de arquivos)
        if (jaCarregou) {
            resumirTodos();
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        // Saiu da tela com uma exclusão pendente: executa agora
        executarExclusaoPendente();
        if (snackbarExclusao != null) {
            snackbarExclusao.dismiss();
        }
    }

    // ---------- Carregar ----------

    private void carregar() {
        mudarEstado(Estado.CARREGANDO);
        repositorio.listarBuckets(new RepositorioCallback<List<Bucket>>() {
            @Override
            public void onSucesso(List<Bucket> lista) {
                if (isDestroyed()) return;
                jaCarregou = true;
                todos.clear();
                todos.addAll(lista);
                atualizarLista();
                resumirTodos();
            }

            @Override
            public void onErro(String codigo, String mensagem) {
                if (isDestroyed()) return;
                if (MensagensErro.sessaoExpirou(BucketsActivity.this, codigo)) return;
                txtErro.setText(MensagensErro.texto(BucketsActivity.this, codigo, mensagem));
                mudarEstado(Estado.ERRO);
            }
        });
    }

    // Números de cada bucket: Imagens · Docs · Outros
    private void resumirTodos() {
        for (Bucket bucket : todos) {
            resumir(bucket.getId());
        }
    }

    private void resumir(String bucketId) {
        repositorio.resumirBucket(bucketId, new RepositorioCallback<ResumoBucket>() {
            @Override
            public void onSucesso(ResumoBucket resumo) {
                if (isDestroyed()) return;
                adapter.setResumo(bucketId, resumo);
                atualizarQuantidade();
            }

            @Override
            public void onErro(String codigo, String mensagem) {
                // Sem o resumo, o cartão mostra "—"
            }
        });
    }

    // Aplica a busca e a ordem, e escolhe entre lista, vazio ou "nenhum resultado"
    private void atualizarLista() {
        visiveis.clear();
        for (Bucket bucket : todos) {
            if (bucket.getNome().contains(busca)) {
                visiveis.add(bucket);
            }
        }
        visiveis.sort((a, b) -> {
            if (ordem == ORDEM_NOME) {
                return a.getNome().compareTo(b.getNome());
            }
            if (ordem == ORDEM_ARQUIVOS) {
                return Long.compare(totalDe(b), totalDe(a));
            }
            String dataA = a.getCriadoEm() != null ? a.getCriadoEm() : "";
            String dataB = b.getCriadoEm() != null ? b.getCriadoEm() : "";
            return dataB.compareTo(dataA);
        });
        adapter.setLista(visiveis);

        atualizarQuantidade();
        if (todos.isEmpty()) {
            txtVazio.setText(R.string.buckets_vazio);
            mudarEstado(Estado.VAZIO);
        } else if (visiveis.isEmpty()) {
            txtVazio.setText(getString(R.string.buckets_nenhum_resultado, busca));
            mudarEstado(Estado.VAZIO);
        } else {
            mudarEstado(Estado.CONTEUDO);
            if (adapter.getModo() == BucketAdapter.MODO_ROTATIVO) {
                posicionarRotativo();
            }
        }
    }

    private long totalDe(Bucket bucket) {
        ResumoBucket resumo = adapter.getResumo(bucket.getId());
        return resumo != null ? resumo.getTotal() : 0;
    }

    // "6 buckets · 39 arquivos"
    private void atualizarQuantidade() {
        String textoBuckets = getResources().getQuantityString(R.plurals.qtd_buckets, todos.size(), todos.size());
        long totalArquivos = 0;
        boolean temResumo = false;
        for (Bucket bucket : todos) {
            ResumoBucket resumo = adapter.getResumo(bucket.getId());
            if (resumo != null) {
                totalArquivos += resumo.getTotal();
                temResumo = true;
            }
        }
        if (temResumo) {
            int total = (int) totalArquivos;
            String textoArquivos = getResources().getQuantityString(R.plurals.qtd_arquivos, total, total);
            txtQuantidade.setText(getString(R.string.resumo_buckets, textoBuckets, textoArquivos));
        } else {
            txtQuantidade.setText(textoBuckets);
        }
    }

    private void mudarEstado(Estado novo) {
        estado = novo;
        boolean conteudo = novo == Estado.CONTEUDO;
        boolean rotativo = adapter.getModo() == BucketAdapter.MODO_ROTATIVO;
        progressCarregando.setVisibility(novo == Estado.CARREGANDO ? View.VISIBLE : View.GONE);
        recyclerBuckets.setVisibility(conteudo ? View.VISIBLE : View.INVISIBLE);
        txtVazio.setVisibility(novo == Estado.VAZIO ? View.VISIBLE : View.GONE);
        grupoErro.setVisibility(novo == Estado.ERRO ? View.VISIBLE : View.GONE);
        rodapeRotativo.setVisibility(conteudo && rotativo ? View.VISIBLE : View.GONE);
        brilho.setVisibility(conteudo && rotativo ? View.VISIBLE : View.GONE);
        // No rotativo, o "Criar novo bucket" fica embaixo do carrossel
        if (conteudo && rotativo) {
            fabNovoBucket.hide();
        } else {
            fabNovoBucket.show();
        }
    }

    // ---------- Modos: rotativo, grade e lista ----------

    private void aplicarModo(int modo) {
        adapter.setModo(modo);
        snapHelper.attachToRecyclerView(null);
        int margem = getResources().getDimensionPixelSize(R.dimen.margem);
        int espacoFab = Math.round(88 * getResources().getDisplayMetrics().density);

        if (modo == BucketAdapter.MODO_ROTATIVO) {
            layoutRotativo = new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false);
            recyclerBuckets.setLayoutManager(layoutRotativo);
            snapHelper.attachToRecyclerView(recyclerBuckets);
            recyclerBuckets.setPadding(0, 0, 0, 0);
        } else if (modo == BucketAdapter.MODO_GRADE) {
            int colunas = getResources().getInteger(R.integer.colunas_grade);
            recyclerBuckets.setLayoutManager(new GridLayoutManager(this, colunas));
            recyclerBuckets.setPadding(margem - 6, 0, margem - 6, espacoFab);
        } else {
            recyclerBuckets.setLayoutManager(new LinearLayoutManager(this));
            recyclerBuckets.setPadding(margem, 0, margem, espacoFab);
        }

        if (estado == Estado.CONTEUDO) {
            mudarEstado(Estado.CONTEUDO);
            if (modo == BucketAdapter.MODO_ROTATIVO) {
                posicionarRotativo();
            }
        }
    }

    // Deixa o cartão escolhido no centro (sem animação)
    private void posicionarRotativo() {
        int posicao = indiceDe(idCentro);
        if (posicao < 0) posicao = indiceDe(preferencias.getUltimoBucket());
        if (posicao < 0) posicao = 0;
        final int alvo = posicao;
        adapter.setPosicaoAtiva(alvo);
        idCentro = visiveis.get(alvo).getId();
        montarPontos();

        recyclerBuckets.post(() -> {
            if (layoutRotativo == null || adapter.getModo() != BucketAdapter.MODO_ROTATIVO) return;
            // Espaço dos lados para o primeiro e o último cartão também ficarem no centro
            int larguraCartao = getResources().getDimensionPixelSize(R.dimen.largura_cartao_rotativo);
            int lateral = Math.max(0, (recyclerBuckets.getWidth() - larguraCartao) / 2);
            recyclerBuckets.setPadding(lateral, 0, lateral, 0);
            layoutRotativo.scrollToPositionWithOffset(alvo, 0);
            recyclerBuckets.post(this::aplicarEfeitoRotativo);
        });
    }

    private int indiceDe(String bucketId) {
        if (bucketId == null || bucketId.isEmpty()) return -1;
        for (int i = 0; i < visiveis.size(); i++) {
            if (bucketId.equals(visiveis.get(i).getId())) return i;
        }
        return -1;
    }

    // Quanto mais longe do centro, menor e mais transparente
    private void aplicarEfeitoRotativo() {
        float centro = recyclerBuckets.getWidth() / 2f;
        for (int i = 0; i < recyclerBuckets.getChildCount(); i++) {
            View filho = recyclerBuckets.getChildAt(i);
            float meio = (filho.getLeft() + filho.getRight()) / 2f;
            float distancia = Math.min(1f, Math.abs(meio - centro) / Math.max(1, filho.getWidth()));
            float escala = 1f - 0.14f * distancia;
            filho.setScaleX(escala);
            filho.setScaleY(escala);
            filho.setAlpha(1f - 0.5f * distancia);
        }
    }

    // Terminou de rolar: o cartão do centro ganha o destaque verde
    private void atualizarCentro() {
        if (layoutRotativo == null) return;
        View vista = snapHelper.findSnapView(layoutRotativo);
        if (vista == null) return;
        int posicao = layoutRotativo.getPosition(vista);
        if (posicao == RecyclerView.NO_POSITION || posicao >= visiveis.size()) return;
        adapter.setPosicaoAtiva(posicao);
        idCentro = visiveis.get(posicao).getId();
        atualizarPontos(posicao);
    }

    private void centralizar(int posicao) {
        if (layoutRotativo == null) return;
        View vista = layoutRotativo.findViewByPosition(posicao);
        if (vista != null) {
            int meio = (vista.getLeft() + vista.getRight()) / 2;
            recyclerBuckets.smoothScrollBy(meio - recyclerBuckets.getWidth() / 2, 0);
        } else {
            recyclerBuckets.smoothScrollToPosition(posicao);
        }
    }

    // Bolinhas de posição embaixo do carrossel
    private void montarPontos() {
        pontos.removeAllViews();
        int dp = Math.round(getResources().getDisplayMetrics().density);
        for (int i = 0; i < visiveis.size(); i++) {
            final int posicao = i;
            // Moldura de 24 dp para facilitar o toque; a bolinha fica dentro dela
            FrameLayout moldura = new FrameLayout(this);
            moldura.setLayoutParams(new LinearLayout.LayoutParams(28 * dp, 24 * dp));
            moldura.setContentDescription(visiveis.get(i).getNome());
            moldura.setOnClickListener(v -> centralizar(posicao));

            View bolinha = new View(this);
            FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(8 * dp, 8 * dp);
            params.gravity = android.view.Gravity.CENTER;
            bolinha.setLayoutParams(params);
            bolinha.setBackgroundResource(R.drawable.bg_ponto);
            moldura.addView(bolinha);
            pontos.addView(moldura);
        }
        atualizarPontos(adapter.getPosicaoAtiva());
    }

    private void atualizarPontos(int ativo) {
        int dp = Math.round(getResources().getDisplayMetrics().density);
        for (int i = 0; i < pontos.getChildCount(); i++) {
            View bolinha = ((ViewGroup) pontos.getChildAt(i)).getChildAt(0);
            ViewGroup.LayoutParams params = bolinha.getLayoutParams();
            params.width = (i == ativo ? 22 : 8) * dp;
            bolinha.setLayoutParams(params);
            bolinha.getBackground().mutate().setTint(ContextCompat.getColor(this,
                    i == ativo ? R.color.acento : R.color.borda));
        }
    }

    // ---------- Cliques nos cartões (BucketAdapter.Acao) ----------

    @Override
    public void aoClicar(Bucket bucket, int posicao) {
        if (posicao == RecyclerView.NO_POSITION) return;
        // Rotativo: tocar num cartão do lado traz ele para o centro
        if (adapter.getModo() == BucketAdapter.MODO_ROTATIVO && posicao != adapter.getPosicaoAtiva()) {
            centralizar(posicao);
            return;
        }
        preferencias.salvarUltimoBucket(bucket.getId());
        Intent intent = new Intent(this, ArquivosActivity.class);
        intent.putExtra(ArquivosActivity.EXTRA_BUCKET, bucket);
        startActivity(intent);
    }

    @Override
    public void aoExcluir(Bucket bucket, int posicao) {
        excluirComDesfazer(bucket);
    }

    // ---------- Criar ----------

    private void mostrarDialogoNovoBucket() {
        View conteudo = getLayoutInflater().inflate(R.layout.dialog_novo_bucket, null);
        EditText edtNome = conteudo.findViewById(R.id.edtNomeBucket);

        AlertDialog dialogo = new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.novo_bucket_titulo)
                .setView(conteudo)
                .setNegativeButton(R.string.botao_cancelar, null)
                .setPositiveButton(R.string.botao_criar, null) // o clique é tratado abaixo
                .create();

        // Assim o diálogo não fecha sozinho quando o nome é inválido
        dialogo.setOnShowListener(d -> {
            Button btnCriar = dialogo.getButton(AlertDialog.BUTTON_POSITIVE);
            btnCriar.setOnClickListener(v -> criarBucket(dialogo, edtNome, btnCriar));
        });
        dialogo.show();
    }

    private void criarBucket(AlertDialog dialogo, EditText edtNome, Button btnCriar) {
        String nome = edtNome.getText().toString().trim();
        if (!Formatos.nomeBucketValido(nome)) {
            edtNome.setError(getString(R.string.erro_nome_bucket));
            return;
        }

        btnCriar.setEnabled(false);
        repositorio.criarBucket(nome, new RepositorioCallback<Bucket>() {
            @Override
            public void onSucesso(Bucket bucket) {
                dialogo.dismiss();
                Toast.makeText(BucketsActivity.this, R.string.bucket_criado, Toast.LENGTH_SHORT).show();
                todos.add(bucket);
                adapter.setResumo(bucket.getId(), new ResumoBucket()); // começa vazio
                idCentro = bucket.getId(); // no rotativo, o novo bucket vai para o centro
                preferencias.salvarUltimoBucket(bucket.getId());
                adapter.setIdDestaque(bucket.getId());
                atualizarLista();
            }

            @Override
            public void onErro(String codigo, String mensagem) {
                btnCriar.setEnabled(true);
                if (MensagensErro.sessaoExpirou(BucketsActivity.this, codigo)) {
                    dialogo.dismiss();
                    return;
                }
                edtNome.setError(MensagensErro.texto(BucketsActivity.this, codigo, mensagem));
            }
        });
    }

    // ---------- Excluir com "Desfazer" ----------
    // O back-end não restaura um bucket excluído. Por isso o item sai só da lista
    // e a exclusão real fica adiada até o Snackbar sumir. Se o usuário tocar em
    // "Desfazer", o item volta para a lista e nada é enviado ao servidor.

    private void excluirComDesfazer(Bucket bucket) {
        // Se já havia outra exclusão esperando, ela é confirmada agora
        executarExclusaoPendente();

        bucketPendente = bucket;
        posicaoPendente = todos.indexOf(bucket);
        todos.remove(bucket);
        atualizarLista();

        // O aviso fica acima do botão que estiver na tela (rodapé do rotativo ou botão +)
        View ancora = rodapeRotativo.getVisibility() == View.VISIBLE ? rodapeRotativo : fabNovoBucket;
        snackbarExclusao = Snackbar.make(findViewById(R.id.main), R.string.bucket_excluido, Snackbar.LENGTH_LONG)
                .setAnchorView(ancora)
                .setAction(R.string.desfazer, v -> {
                    bucketPendente = null;
                    reinserir(bucket, posicaoPendente);
                })
                .addCallback(new Snackbar.Callback() {
                    @Override
                    public void onDismissed(Snackbar barra, int evento) {
                        if (evento != DISMISS_EVENT_ACTION && bucketPendente == bucket) {
                            executarExclusaoPendente();
                        }
                    }
                });
        snackbarExclusao.show();
    }

    private void reinserir(Bucket bucket, int posicao) {
        todos.add(Math.max(0, Math.min(posicao, todos.size())), bucket);
        atualizarLista();
    }

    private void executarExclusaoPendente() {
        if (bucketPendente == null) {
            return;
        }
        Bucket bucket = bucketPendente;
        int posicao = posicaoPendente;
        bucketPendente = null;

        repositorio.excluirBucket(bucket.getId(), new RepositorioCallback<Void>() {
            @Override
            public void onSucesso(Void resultado) {
                // Já saiu da lista: nada a fazer
            }

            @Override
            public void onErro(String codigo, String mensagem) {
                if (isDestroyed()) return;
                if (MensagensErro.sessaoExpirou(BucketsActivity.this, codigo)) return;
                // Não deu para excluir (ex.: bucket com arquivos): o item volta
                reinserir(bucket, posicao);
                Toast.makeText(BucketsActivity.this,
                        MensagensErro.texto(BucketsActivity.this, codigo, mensagem),
                        Toast.LENGTH_LONG).show();
            }
        });
    }

    // ---------- Perfil e sair ----------

    private void mostrarPerfil() {
        StringBuilder texto = new StringBuilder();
        String nome = sessao.getNome();
        if (nome != null && !nome.isEmpty()) {
            texto.append(getString(R.string.perfil_nome, nome)).append('\n');
        }
        texto.append(getString(R.string.perfil_email, sessao.getEmail()));

        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.perfil_titulo)
                .setMessage(texto.toString())
                .setPositiveButton(R.string.botao_ok, null)
                .show();
    }

    private void sair() {
        // Cancela a exclusão pendente (sem sessão ela falharia)
        bucketPendente = null;
        if (snackbarExclusao != null) {
            snackbarExclusao.dismiss();
        }
        sessao.limpar();
        Intent intent = new Intent(this, LoginActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
    }
}
