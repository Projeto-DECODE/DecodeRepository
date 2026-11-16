package br.senai.sp.meustorage;

import android.content.Intent;
import android.os.Bundle;
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

import com.google.android.material.button.MaterialButton;

import br.senai.sp.meustorage.data.RepositorioCallback;
import br.senai.sp.meustorage.data.StorageRepository;
import br.senai.sp.meustorage.util.MensagensErro;
import br.senai.sp.meustorage.util.Sessao;

public class LoginActivity extends AppCompatActivity {

    private EditText edtEmail;
    private EditText edtSenha;
    private MaterialButton btnEntrar;
    private ProgressBar progressLogin;

    private StorageRepository repositorio;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Já tem sessão válida? Vai direto para os buckets
        if (new Sessao(this).isValida()) {
            abrirBuckets();
            return;
        }

        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_login);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            // ime(): sobe o conteúdo quando o teclado abre
            Insets barras = insets.getInsets(WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.ime());
            v.setPadding(barras.left, barras.top, barras.right, barras.bottom);
            return insets;
        });

        repositorio = new StorageRepository(this);

        edtEmail = findViewById(R.id.edtEmail);
        edtSenha = findViewById(R.id.edtSenha);
        btnEntrar = findViewById(R.id.btnEntrar);
        progressLogin = findViewById(R.id.progressLogin);
        TextView txtCriarConta = findViewById(R.id.txtCriarConta);

        btnEntrar.setOnClickListener(v -> entrar());
        txtCriarConta.setOnClickListener(v -> startActivity(new Intent(this, CadastroActivity.class)));
    }

    private void entrar() {
        String email = edtEmail.getText().toString().trim();
        String senha = edtSenha.getText().toString();

        boolean valido = true;
        if (email.isEmpty()) {
            edtEmail.setError(getString(R.string.erro_obrigatorio));
            valido = false;
        }
        if (senha.isEmpty()) {
            edtSenha.setError(getString(R.string.erro_obrigatorio));
            valido = false;
        }
        if (!valido) {
            return;
        }

        carregando(true);
        repositorio.login(email, senha, new RepositorioCallback<Void>() {
            @Override
            public void onSucesso(Void resultado) {
                abrirBuckets();
            }

            @Override
            public void onErro(String codigo, String mensagem) {
                carregando(false);
                Toast.makeText(LoginActivity.this,
                        MensagensErro.texto(LoginActivity.this, codigo, mensagem),
                        Toast.LENGTH_LONG).show();
            }
        });
    }

    private void carregando(boolean sim) {
        btnEntrar.setEnabled(!sim);
        progressLogin.setVisibility(sim ? View.VISIBLE : View.GONE);
    }

    private void abrirBuckets() {
        startActivity(new Intent(this, BucketsActivity.class));
        finish();
    }
}
