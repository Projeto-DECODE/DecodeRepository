package br.senai.sp.meustorage;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.constraintlayout.widget.Group;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.snackbar.Snackbar;

import java.util.List;
import java.util.regex.Pattern;

import br.senai.sp.meustorage.adapter.BucketAdapter;
import br.senai.sp.meustorage.data.RepositorioCallback;
import br.senai.sp.meustorage.data.StorageRepository;
import br.senai.sp.meustorage.model.Bucket;
import br.senai.sp.meustorage.util.MensagensErro;
import br.senai.sp.meustorage.util.Sessao;

public class BucketsActivity extends AppCompatActivity {

    // Mesmo regex do back-end
    private static final Pattern NOME_BUCKET = Pattern.compile("^[a-z0-9]([a-z0-9-]*[a-z0-9])?$");

    private enum Estado { CARREGANDO, CONTEUDO, VAZIO, ERRO }

    private TextView txtQuantidade;
    private RecyclerView recyclerBuckets;
    private ProgressBar progressCarregando;
    private TextView txtVazio;
    private TextView txtErro;
    private Group grupoErro;
    private FloatingActionButton fabNovoBucket;

    private BucketAdapter adapter;
    private StorageRepository repositorio;
    private Sessao sessao;

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
        View btnTentar = findViewById(R.id.btnTentar);

        adapter = new BucketAdapter(bucket -> {
            Intent intent = new Intent(this, ArquivosActivity.class);
            intent.putExtra(ArquivosActivity.EXTRA_BUCKET, bucket);
            startActivity(intent);
        });
        recyclerBuckets.setLayoutManager(new LinearLayoutManager(this));
        recyclerBuckets.setAdapter(adapter);

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
                // Bucket "Excluindo…" não pode ser arrastado
                if (pos != RecyclerView.NO_POSITION && adapter.getItem(pos).isExcluindo()) {
                    return 0;
                }
                return super.getSwipeDirs(rv, vh);
            }

            @Override
            public void onSwiped(@NonNull RecyclerView.ViewHolder vh, int direcao) {
                int pos = vh.getBindingAdapterPosition();
                if (pos != RecyclerView.NO_POSITION) {
                    excluirComDesfazer(pos);
                }
            }
        }).attachToRecyclerView(recyclerBuckets);

        fabNovoBucket.setOnClickListener(v -> mostrarDialogoNovoBucket());
        btnTentar.setOnClickListener(v -> carregar());

        carregar();
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

    private void carregar() {
        mudarEstado(Estado.CARREGANDO);
        repositorio.listarBuckets(new RepositorioCallback<List<Bucket>>() {
            @Override
            public void onSucesso(List<Bucket> lista) {
                if (isDestroyed()) return;
                adapter.setLista(lista);
                atualizarQuantidade();
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

    // Atualiza o contador e escolhe entre lista e estado vazio
    private void atualizarQuantidade() {
        int total = adapter.getItemCount();
        txtQuantidade.setText(getResources().getQuantityString(R.plurals.qtd_buckets, total, total));
        mudarEstado(total == 0 ? Estado.VAZIO : Estado.CONTEUDO);
    }

    private void mudarEstado(Estado estado) {
        progressCarregando.setVisibility(estado == Estado.CARREGANDO ? View.VISIBLE : View.GONE);
        recyclerBuckets.setVisibility(estado == Estado.CONTEUDO ? View.VISIBLE : View.GONE);
        txtVazio.setVisibility(estado == Estado.VAZIO ? View.VISIBLE : View.GONE);
        grupoErro.setVisibility(estado == Estado.ERRO ? View.VISIBLE : View.GONE);
        txtQuantidade.setVisibility(estado == Estado.CONTEUDO || estado == Estado.VAZIO ? View.VISIBLE : View.INVISIBLE);
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
        if (nome.length() < 3 || nome.length() > 63 || !NOME_BUCKET.matcher(nome).matches()) {
            edtNome.setError(getString(R.string.erro_nome_bucket));
            return;
        }

        btnCriar.setEnabled(false);
        repositorio.criarBucket(nome, new RepositorioCallback<Bucket>() {
            @Override
            public void onSucesso(Bucket bucket) {
                dialogo.dismiss();
                Toast.makeText(BucketsActivity.this, R.string.bucket_criado, Toast.LENGTH_SHORT).show();
                carregar();
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

    private void excluirComDesfazer(int posicao) {
        // Se já havia outra exclusão esperando, ela é confirmada agora
        executarExclusaoPendente();

        Bucket bucket = adapter.remover(posicao);
        bucketPendente = bucket;
        posicaoPendente = posicao;
        atualizarQuantidade();

        snackbarExclusao = Snackbar.make(findViewById(R.id.main), R.string.bucket_excluido, Snackbar.LENGTH_LONG)
                .setAnchorView(fabNovoBucket)
                .setAction(R.string.desfazer, v -> {
                    bucketPendente = null;
                    adapter.inserir(posicao, bucket);
                    atualizarQuantidade();
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
                adapter.inserir(posicao, bucket);
                atualizarQuantidade();
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
