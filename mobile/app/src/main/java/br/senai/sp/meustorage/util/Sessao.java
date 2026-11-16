package br.senai.sp.meustorage.util;

import android.content.Context;
import android.content.SharedPreferences;

// Sessão do usuário salva em SharedPreferences.
// Nunca registrar o token no Logcat.
public class Sessao {

    private static final String ARQUIVO = "sessao_meustorage";
    private static final String CHAVE_TOKEN = "token";
    private static final String CHAVE_EXPIRA_EM = "expira_em";
    private static final String CHAVE_EMAIL = "email";
    private static final String CHAVE_NOME = "nome";

    private final SharedPreferences prefs;

    public Sessao(Context context) {
        prefs = context.getApplicationContext().getSharedPreferences(ARQUIVO, Context.MODE_PRIVATE);
    }

    // expiresInSegundos vem do back-end (900 = 15 min)
    public void salvarLogin(String token, long expiresInSegundos, String email) {
        SharedPreferences.Editor editor = prefs.edit();
        // Entrou outra pessoa: o nome guardado não é dela
        if (!email.equalsIgnoreCase(getEmail())) {
            editor.remove(CHAVE_NOME);
        }
        editor.putString(CHAVE_TOKEN, token);
        editor.putLong(CHAVE_EXPIRA_EM, System.currentTimeMillis() + expiresInSegundos * 1000L);
        editor.putString(CHAVE_EMAIL, email);
        editor.apply();
    }

    // O token não traz o nome; ele só é conhecido no cadastro
    public void salvarNome(String nome) {
        prefs.edit().putString(CHAVE_NOME, nome).apply();
    }

    public boolean isValida() {
        return getToken() != null && System.currentTimeMillis() < prefs.getLong(CHAVE_EXPIRA_EM, 0);
    }

    public String getToken() {
        return prefs.getString(CHAVE_TOKEN, null);
    }

    // Valor do cabeçalho Authorization
    public String getCabecalho() {
        return "Bearer " + getToken();
    }

    public String getEmail() {
        return prefs.getString(CHAVE_EMAIL, "");
    }

    public String getNome() {
        return prefs.getString(CHAVE_NOME, null);
    }

    // Nome, ou só o e-mail se o nome não estiver salvo
    public String getNomeExibicao() {
        String nome = getNome();
        return (nome != null && !nome.isEmpty()) ? nome : getEmail();
    }

    // Remove o token: a sessão deixa de valer.
    // E-mail e nome ficam guardados para quando a mesma pessoa entrar de novo.
    public void limpar() {
        prefs.edit()
                .remove(CHAVE_TOKEN)
                .remove(CHAVE_EXPIRA_EM)
                .apply();
    }
}
