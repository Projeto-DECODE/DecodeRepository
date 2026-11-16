package br.senai.sp.meustorage.model;

import com.google.gson.annotations.SerializedName;

import java.io.Serializable;

public class Arquivo implements Serializable {

    @SerializedName("id")
    private String id;

    @SerializedName("bucketId")
    private String bucketId;

    @SerializedName("originalFilename")
    private String nomeOriginal;

    @SerializedName("sizeBytes")
    private long tamanhoBytes;

    @SerializedName("contentType")
    private String tipoConteudo;

    @SerializedName("sha256")
    private String sha256;

    @SerializedName("createdAt")
    private String criadoEm;

    public Arquivo() {
    }

    public Arquivo(String id, String bucketId, String nomeOriginal, long tamanhoBytes,
                   String tipoConteudo, String sha256, String criadoEm) {
        this.id = id;
        this.bucketId = bucketId;
        this.nomeOriginal = nomeOriginal;
        this.tamanhoBytes = tamanhoBytes;
        this.tipoConteudo = tipoConteudo;
        this.sha256 = sha256;
        this.criadoEm = criadoEm;
    }

    public String getId() {
        return id;
    }

    public String getBucketId() {
        return bucketId;
    }

    public String getNomeOriginal() {
        return nomeOriginal;
    }

    public long getTamanhoBytes() {
        return tamanhoBytes;
    }

    public String getTipoConteudo() {
        return tipoConteudo;
    }

    public String getSha256() {
        return sha256;
    }

    public String getCriadoEm() {
        return criadoEm;
    }

    public boolean isImagem() {
        return tipoConteudo != null && tipoConteudo.startsWith("image/");
    }
}
