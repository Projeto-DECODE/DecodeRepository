package br.senai.sp.meustorage.model;

import com.google.gson.annotations.SerializedName;

import java.io.Serializable;

public class Usuario implements Serializable {

    @SerializedName("id")
    private String id;

    @SerializedName("name")
    private String nome;

    @SerializedName("email")
    private String email;

    @SerializedName("createdAt")
    private String criadoEm;

    public Usuario() {
    }

    public Usuario(String id, String nome, String email, String criadoEm) {
        this.id = id;
        this.nome = nome;
        this.email = email;
        this.criadoEm = criadoEm;
    }

    public String getId() {
        return id;
    }

    public String getNome() {
        return nome;
    }

    public String getEmail() {
        return email;
    }

    public String getCriadoEm() {
        return criadoEm;
    }
}
