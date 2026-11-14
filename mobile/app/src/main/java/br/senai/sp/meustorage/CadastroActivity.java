package br.senai.sp.meustorage;

import android.content.Intent;
import android.os.Bundle;
import android.util.Patterns;
import android.view.View;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;

import br.senai.sp.meustorage.data.RepositorioCallback;
import br.senai.sp.meustorage.data.StorageRepository;
import br.senai.sp.meustorage.model.Usuario;
import br.senai.sp.meustorage.util.MensagensErro;
import br.senai.sp.meustorage.util.Sessao;

public class CadastroActivity extends AppCompatActivity {

    private EditText edtNome;
    private EditText edtEmail;
    private EditText edtSenha;
    private MaterialButton btnCadastrar;
    private ProgressBar progressCadastro;

    private StorageRepository repositorio;
    private Sessao sessao;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_cadastro);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets barras = insets.getInsets(WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.ime());
            v.setPadding(barras.left, barras.top, barras.right, barras.bottom);
            return insets;
        });

        repositorio = new StorageRepository(this);
        sessao = new Sessao(this);

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());

        edtNome = findViewById(R.id.edtNome);
        edtEmail = findViewById(R.id.edtEmail);
        edtSenha = findViewById(R.id.edtSenha);
        btnCadastrar = findViewById(R.id.btnCadastrar);
        progressCadastro = findViewById(R.id.progressCadastro);
        TextView txtEntrar = findViewById(R.id.txtEntrar);

        btnCadastrar.setOnClickListener(v -> cadastrar());
        txtEntrar.setOnClickListener(v -> finish());
    }

    private void cadastrar() {
        String nome = edtNome.getText().toString().trim();
        String email = edtEmail.getText().toString().trim();
        String senha = edtSenha.getText().toString();

        boolean valido = true;
        if (nome.isEmpty()) {
            edtNome.setError(getString(R.string.erro_obrigatorio));
            valido = false;
        } else if (nome.length() > 100) {
            edtNome.setError(getString(R.string.erro_nome_longo));
            valido = false;
        }
        if (email.isEmpty()) {
            edtEmail.setError(getString(R.string.erro_obrigatorio));
            valido = false;
        } else if (email.length() > 254 || !Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            edtEmail.setError(getString(R.string.erro_email_invalido));
            valido = false;
        }
        if (senha.length() < 8) {
            edtSenha.setError(getString(R.string.erro_senha_curta));
            valido = false;
        } else if (senha.length() > 128) {
            edtSenha.setError(getString(R.string.erro_senha_longa));
            valido = false;
        }
        if (!valido) {
            return;
        }

        carregando(true);
        repositorio.cadastrar(nome, email, senha, new RepositorioCallback<Usuario>() {
            @Override
            public void onSucesso(Usuario usuario) {
                entrarAutomaticamente(nome, email, senha);
            }

            @Override
            public void onErro(String codigo, String mensagem) {
                carregando(false);
                String texto = MensagensErro.texto(CadastroActivity.this, codigo, mensagem);
                if (MensagensErro.EMAIL_ALREADY_IN_USE.equals(codigo)) {
                    edtEmail.setError(texto);
                } else {
                    Toast.makeText(CadastroActivity.this, texto, Toast.LENGTH_LONG).show();
                }
            }
        });
    }

    // Depois do cadastro, faz o login sozinho
    private void entrarAutomaticamente(String nome, String email, String senha) {
        repositorio.login(email, senha, new RepositorioCallback<Void>() {
            @Override
            public void onSucesso(Void resultado) {
                // O token não traz o nome: ele é guardado aqui
                sessao.salvarNome(nome);
                Intent intent = new Intent(CadastroActivity.this, BucketsActivity.class);
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                startActivity(intent);
            }

            @Override
            public void onErro(String codigo, String mensagem) {
                // Conta criada, mas o login falhou: volta para a tela de login
                carregando(false);
                Toast.makeText(CadastroActivity.this,
                        MensagensErro.texto(CadastroActivity.this, codigo, mensagem),
                        Toast.LENGTH_LONG).show();
                finish();
            }
        });
    }

    private void carregando(boolean sim) {
        btnCadastrar.setEnabled(!sim);
        progressCadastro.setVisibility(sim ? View.VISIBLE : View.GONE);
    }
}
