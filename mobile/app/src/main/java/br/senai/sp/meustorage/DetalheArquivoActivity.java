package br.senai.sp.meustorage;

import android.os.Bundle;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.material.appbar.MaterialToolbar;

import br.senai.sp.meustorage.data.RepositorioCallback;
import br.senai.sp.meustorage.data.StorageRepository;
import br.senai.sp.meustorage.model.Arquivo;
import br.senai.sp.meustorage.model.Bucket;
import br.senai.sp.meustorage.util.MensagensErro;
import br.senai.sp.meustorage.util.PainelDetalhe;

// Usada só no celular. No tablet, o detalhe aparece no painel da ArquivosActivity.
public class DetalheArquivoActivity extends AppCompatActivity implements PainelDetalhe.Acoes {

    public static final String EXTRA_ARQUIVO = "arquivo";
    public static final String EXTRA_BUCKET = "bucket";

    private Arquivo arquivo;
    private Bucket bucket;
    private StorageRepository repositorio;
    private PainelDetalhe painel;

    // O usuário escolhe a pasta e o nome; não precisa de permissão de armazenamento
    private final ActivityResultLauncher<String> launcherSalvar =
            registerForActivityResult(new ActivityResultContracts.CreateDocument("*/*"), uri -> {
                if (uri != null) {
                    painel.salvar(arquivo, uri);
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_detalhe_arquivo);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets barras = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(barras.left, barras.top, barras.right, barras.bottom);
            return insets;
        });

        arquivo = (Arquivo) getIntent().getSerializableExtra(EXTRA_ARQUIVO);
        bucket = (Bucket) getIntent().getSerializableExtra(EXTRA_BUCKET);
        if (arquivo == null) {
            finish();
            return;
        }

        repositorio = new StorageRepository(this);

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());

        painel = new PainelDetalhe(this, findViewById(R.id.painelDetalhe), repositorio, this);
        painel.mostrar(arquivo, bucket);
    }

    @Override
    public void aoBaixar(Arquivo alvo) {
        launcherSalvar.launch(alvo.getNomeOriginal());
    }

    @Override
    public void aoExcluir(Arquivo alvo) {
        painel.setBotoesHabilitados(false);
        repositorio.excluirArquivo(alvo.getBucketId(), alvo.getId(), new RepositorioCallback<Void>() {
            @Override
            public void onSucesso(Void resultado) {
                Toast.makeText(DetalheArquivoActivity.this, R.string.arquivo_excluido, Toast.LENGTH_SHORT).show();
                // Avisa a lista para recarregar
                setResult(RESULT_OK);
                finish();
            }

            @Override
            public void onErro(String codigo, String mensagem) {
                painel.setBotoesHabilitados(true);
                if (MensagensErro.sessaoExpirou(DetalheArquivoActivity.this, codigo)) return;
                Toast.makeText(DetalheArquivoActivity.this,
                        MensagensErro.texto(DetalheArquivoActivity.this, codigo, mensagem),
                        Toast.LENGTH_LONG).show();
            }
        });
    }
}
