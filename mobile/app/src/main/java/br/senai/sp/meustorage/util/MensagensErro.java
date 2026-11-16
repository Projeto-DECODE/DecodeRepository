package br.senai.sp.meustorage.util;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.widget.Toast;

import br.senai.sp.meustorage.LoginActivity;
import br.senai.sp.meustorage.R;

// Converte o código de erro em uma frase em português.
public final class MensagensErro {

    // Códigos do back-end
    public static final String VALIDATION_ERROR = "VALIDATION_ERROR";
    public static final String UNAUTHORIZED = "UNAUTHORIZED";
    public static final String FORBIDDEN = "FORBIDDEN";
    public static final String INVALID_CREDENTIALS = "INVALID_CREDENTIALS";
    public static final String EMAIL_ALREADY_IN_USE = "EMAIL_ALREADY_IN_USE";
    public static final String BUCKET_NAME_ALREADY_EXISTS = "BUCKET_NAME_ALREADY_EXISTS";
    public static final String BUCKET_NOT_FOUND = "BUCKET_NOT_FOUND";
    public static final String BUCKET_NOT_EMPTY = "BUCKET_NOT_EMPTY";
    public static final String BUCKET_NOT_WRITABLE = "BUCKET_NOT_WRITABLE";
    public static final String OBJECT_NOT_FOUND = "OBJECT_NOT_FOUND";
    public static final String EMPTY_FILE = "EMPTY_FILE";
    public static final String PAYLOAD_TOO_LARGE = "PAYLOAD_TOO_LARGE";
    public static final String DEPENDENCY_UNAVAILABLE = "DEPENDENCY_UNAVAILABLE";
    public static final String STORAGE_ERROR = "STORAGE_ERROR";
    public static final String INTERNAL_ERROR = "INTERNAL_ERROR";
    public static final String HTTP_ERROR = "HTTP_ERROR";

    // Códigos só do app (não vêm do back-end)
    public static final String SEM_CONEXAO = "SEM_CONEXAO";
    public static final String ERRO_LEITURA = "ERRO_LEITURA";
    public static final String ERRO_DOWNLOAD = "ERRO_DOWNLOAD";
    public static final String ARQUIVO_GRANDE = "ARQUIVO_GRANDE";

    private MensagensErro() {
    }

    public static String texto(Context context, String codigo, String mensagemServidor) {
        if (codigo == null) {
            return context.getString(R.string.msg_generico);
        }
        int id;
        switch (codigo) {
            case SEM_CONEXAO: id = R.string.erro_conexao; break;
            case ERRO_LEITURA: id = R.string.erro_ler_arquivo; break;
            case ERRO_DOWNLOAD: id = R.string.download_erro; break;
            case ARQUIVO_GRANDE: id = R.string.erro_arquivo_grande; break;
            case VALIDATION_ERROR: id = R.string.msg_validation_error; break;
            case UNAUTHORIZED: id = R.string.sessao_expirada; break;
            case FORBIDDEN: id = R.string.msg_forbidden; break;
            case INVALID_CREDENTIALS: id = R.string.msg_invalid_credentials; break;
            case EMAIL_ALREADY_IN_USE: id = R.string.msg_email_already_in_use; break;
            case BUCKET_NAME_ALREADY_EXISTS: id = R.string.msg_bucket_name_already_exists; break;
            case BUCKET_NOT_FOUND: id = R.string.msg_bucket_not_found; break;
            case BUCKET_NOT_EMPTY: id = R.string.msg_bucket_not_empty; break;
            case BUCKET_NOT_WRITABLE: id = R.string.msg_bucket_not_writable; break;
            case OBJECT_NOT_FOUND: id = R.string.msg_object_not_found; break;
            case EMPTY_FILE: id = R.string.msg_empty_file; break;
            case PAYLOAD_TOO_LARGE: id = R.string.msg_payload_too_large; break;
            case DEPENDENCY_UNAVAILABLE: id = R.string.msg_dependency_unavailable; break;
            case STORAGE_ERROR: id = R.string.msg_storage_error; break;
            case INTERNAL_ERROR: id = R.string.msg_internal_error; break;
            default:
                // HTTP_ERROR ou código desconhecido
                if (mensagemServidor != null && !mensagemServidor.isEmpty()) {
                    return mensagemServidor;
                }
                id = R.string.msg_generico;
        }
        return context.getString(id);
    }

    // Se o erro for de sessão expirada: limpa a sessão, avisa e volta ao login
    // limpando a pilha de telas. Retorna true quando tratou o erro.
    public static boolean sessaoExpirou(Activity activity, String codigo) {
        if (!UNAUTHORIZED.equals(codigo)) {
            return false;
        }
        new Sessao(activity).limpar();
        Toast.makeText(activity, R.string.sessao_expirada, Toast.LENGTH_LONG).show();
        Intent intent = new Intent(activity, LoginActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        activity.startActivity(intent);
        return true;
    }
}
