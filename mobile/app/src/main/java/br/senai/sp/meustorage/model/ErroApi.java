package br.senai.sp.meustorage.model;

import com.google.gson.annotations.SerializedName;

import java.util.List;

public class ErroApi {

    @SerializedName("code")
    private String codigo;

    @SerializedName("message")
    private String mensagem;

    @SerializedName("status")
    private int status;

    @SerializedName("timestamp")
    private String timestamp;

    @SerializedName("traceId")
    private String traceId;

    // Só aparece em erros de validação
    @SerializedName("details")
    private List<Detalhe> detalhes;

    public String getCodigo() {
        return codigo;
    }

    public String getMensagem() {
        return mensagem;
    }

    public int getStatus() {
        return status;
    }

    public String getTimestamp() {
        return timestamp;
    }

    public String getTraceId() {
        return traceId;
    }

    public List<Detalhe> getDetalhes() {
        return detalhes;
    }

    public static class Detalhe {

        @SerializedName("field")
        private String campo;

        @SerializedName("message")
        private String mensagem;

        public String getCampo() {
            return campo;
        }

        public String getMensagem() {
            return mensagem;
        }
    }
}
