package com.example.sos.auth.security;

import com.example.sos.auth.config.ServiceClientProperties;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import org.springframework.stereotype.Component;

/** Valida credenciais de clientes de serviço em tempo constante em relação ao segredo. */
@Component
public class ServiceClientAuthenticator {

    private static final String DUMMY_SECRET = "dummy-secret-used-for-unknown-clients-only";

    private final ServiceClientProperties properties;

    public ServiceClientAuthenticator(ServiceClientProperties properties) {
        this.properties = properties;
    }

    public boolean authenticate(String clientId, String clientSecret) {
        String expected = properties.serviceClients().get(clientId);
        // Para cliente desconhecido compara contra um valor fictício, mantendo o custo semelhante.
        boolean secretMatches = constantTimeEquals(expected != null ? expected : DUMMY_SECRET,
                clientSecret == null ? "" : clientSecret);
        return expected != null && secretMatches;
    }

    private static boolean constantTimeEquals(String a, String b) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashA = digest.digest(a.getBytes(StandardCharsets.UTF_8));
            digest.reset();
            byte[] hashB = digest.digest(b.getBytes(StandardCharsets.UTF_8));
            return MessageDigest.isEqual(hashA, hashB);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 indisponível", e);
        }
    }
}
