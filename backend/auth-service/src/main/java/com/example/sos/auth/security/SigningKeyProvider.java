package com.example.sos.auth.security;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.KeyUse;
import com.nimbusds.jose.jwk.RSAKey;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.interfaces.RSAPrivateCrtKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.RSAPublicKeySpec;
import java.util.Base64;
import java.util.Map;

/**
 * Carrega a chave privada RSA (PEM PKCS#8) e deriva dela a chave pública publicada no JWKS.
 * A chave privada nunca é exposta: apenas {@link #publicJwkSet()} é servido externamente.
 */
public final class SigningKeyProvider {

    private static final int MIN_RSA_BITS = 2048;
    private static final String PEM_BEGIN = "-----BEGIN PRIVATE KEY-----";
    private static final String PEM_END = "-----END PRIVATE KEY-----";

    private final RSAKey signingKey;

    private SigningKeyProvider(RSAKey signingKey) {
        this.signingKey = signingKey;
    }

    public static SigningKeyProvider fromPemFile(Path path) {
        String pem;
        try {
            pem = Files.readString(path);
        } catch (IOException e) {
            throw new IllegalStateException("Não foi possível ler a chave privada em " + path, e);
        }
        if (pem.contains("BEGIN RSA PRIVATE KEY")) {
            throw new IllegalStateException(
                    "Chave em formato PKCS#1 não é suportada. Converta para PKCS#8: "
                            + "openssl pkcs8 -topk8 -nocrypt -in chave.pem -out private.pem");
        }
        if (!pem.contains(PEM_BEGIN)) {
            throw new IllegalStateException("Arquivo de chave privada inválido em " + path);
        }
        String base64 = pem.replace(PEM_BEGIN, "").replace(PEM_END, "").replaceAll("\\s", "");
        try {
            byte[] der = Base64.getDecoder().decode(base64);
            PrivateKey key = KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(der));
            if (!(key instanceof RSAPrivateCrtKey crtKey)) {
                throw new IllegalStateException("A chave privada deve ser RSA com parâmetros CRT");
            }
            return fromPrivateKey(crtKey);
        } catch (GeneralSecurityException | IllegalArgumentException e) {
            // A mensagem original é omitida de propósito para não vazar conteúdo da chave.
            throw new IllegalStateException("Chave privada em " + path + " não pôde ser interpretada");
        }
    }

    public static SigningKeyProvider fromPrivateKey(RSAPrivateCrtKey privateKey) {
        if (privateKey.getModulus().bitLength() < MIN_RSA_BITS) {
            throw new IllegalStateException("A chave RSA deve ter ao menos " + MIN_RSA_BITS + " bits");
        }
        try {
            RSAPublicKey publicKey = (RSAPublicKey) KeyFactory.getInstance("RSA").generatePublic(
                    new RSAPublicKeySpec(privateKey.getModulus(), privateKey.getPublicExponent()));
            String kid = new RSAKey.Builder(publicKey).build().computeThumbprint().toString();
            RSAKey jwk = new RSAKey.Builder(publicKey)
                    .privateKey(privateKey)
                    .keyID(kid)
                    .keyUse(KeyUse.SIGNATURE)
                    .algorithm(JWSAlgorithm.RS256)
                    .build();
            return new SigningKeyProvider(jwk);
        } catch (GeneralSecurityException | JOSEException e) {
            throw new IllegalStateException("Falha ao derivar a chave pública", e);
        }
    }

    /** JWK com a parte privada. Uso interno, apenas para assinar. */
    public RSAKey signingKey() {
        return signingKey;
    }

    public String keyId() {
        return signingKey.getKeyID();
    }

    /** JWKS contendo somente a chave pública, pronto para serialização JSON. */
    public Map<String, Object> publicJwkSet() {
        return new JWKSet(signingKey.toPublicJWK()).toJSONObject();
    }
}
