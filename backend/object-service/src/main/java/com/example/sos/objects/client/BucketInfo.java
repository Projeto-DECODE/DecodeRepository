package com.example.sos.objects.client;

import java.util.UUID;

/** Visão mínima de um bucket, na resposta do bucket-service. Campos desconhecidos são ignorados. */
public record BucketInfo(UUID id, String name, String status) {

    public boolean isActive() {
        return "ACTIVE".equals(status);
    }
}
