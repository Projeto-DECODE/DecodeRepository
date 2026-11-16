package com.example.sos.objects.config;

import java.nio.file.Path;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.util.unit.DataSize;

@ConfigurationProperties(prefix = "sos.storage")
public record StorageProperties(
        Path root,
        @DefaultValue("50MB") DataSize maxFileSize) {

    public StorageProperties {
        if (root == null) {
            throw new IllegalStateException("sos.storage.root não configurado");
        }
        if (maxFileSize.toBytes() <= 0) {
            throw new IllegalStateException("sos.storage.max-file-size deve ser positivo");
        }
    }
}
