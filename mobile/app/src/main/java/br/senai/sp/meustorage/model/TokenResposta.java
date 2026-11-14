package br.senai.sp.meustorage.model;

import com.google.gson.annotations.SerializedName;

public class TokenResposta {

    @SerializedName("accessToken")
    private String accessToken;

    @SerializedName("tokenType")
    private String tokenType;

    // Em segundos (900 = 15 minutos)
    @SerializedName("expiresIn")
    private long expiresIn;

    public String getAccessToken() {
        return accessToken;
    }

    public String getTokenType() {
        return tokenType;
    }

    public long getExpiresIn() {
        return expiresIn;
    }
}
