package br.senai.sp.meustorage.util;

import android.content.Context;
import android.content.SharedPreferences;

// Escolhas do usuário que ficam salvas no celular (SharedPreferences):
// o modo de exibição dos buckets e o último bucket aberto.
public class Preferencias {

    private static final String ARQUIVO = "preferencias_meustorage";
    private static final String CHAVE_MODO = "modo_buckets";
    private static final String CHAVE_ULTIMO_BUCKET = "ultimo_bucket";

    private final SharedPreferences prefs;

    public Preferencias(Context context) {
        prefs = context.getApplicationContext().getSharedPreferences(ARQUIVO, Context.MODE_PRIVATE);
    }

    // 0 = rotativo (padrão), 1 = grade, 2 = lista (constantes do BucketAdapter)
    public int getModo() {
        return prefs.getInt(CHAVE_MODO, 0);
    }

    public void salvarModo(int modo) {
        prefs.edit().putInt(CHAVE_MODO, modo).apply();
    }

    public String getUltimoBucket() {
        return prefs.getString(CHAVE_ULTIMO_BUCKET, "");
    }

    public void salvarUltimoBucket(String bucketId) {
        prefs.edit().putString(CHAVE_ULTIMO_BUCKET, bucketId).apply();
    }
}
