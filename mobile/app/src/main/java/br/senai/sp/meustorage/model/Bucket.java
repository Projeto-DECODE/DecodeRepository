package br.senai.sp.meustorage.model;

import com.google.gson.annotations.SerializedName;

import java.io.Serializable;

public class Bucket implements Serializable {

    public static final String STATUS_ATIVO = "ACTIVE";
    public static final String STATUS_EXCLUINDO = "DELETING";

    @SerializedName("id")
    private String id;

    @SerializedName("name")
    private String nome;

    @SerializedName("status")
    private String status;

    @SerializedName("createdAt")
    private String criadoEm;

    public Bucket() {
    }

    public Bucket(String id, String nome, String status, String criadoEm) {
        this.id = id;
        this.nome = nome;
        this.status = status;
        this.criadoEm = criadoEm;
    }

    public String getId() {
        return id;
    }

    public String getNome() {
        return nome;
    }

    public String getStatus() {
        return status;
    }

    public String getCriadoEm() {
        return criadoEm;
    }

    public boolean isExcluindo() {
        return STATUS_EXCLUINDO.equals(status);
    }
}
