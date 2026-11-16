package com.example.sos.objects.config;

import com.example.sos.objects.storage.FileSystemObjectStorage;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class StorageConfig {

    @Bean
    FileSystemObjectStorage objectStorage(StorageProperties properties) {
        return new FileSystemObjectStorage(properties.root(), properties.maxFileSize().toBytes());
    }
}
