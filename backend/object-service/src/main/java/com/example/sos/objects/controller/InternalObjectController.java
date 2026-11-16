package com.example.sos.objects.controller;

import com.example.sos.objects.dto.ObjectCountResponse;
import com.example.sos.objects.service.ObjectService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Rota de uso exclusivo do bucket-service. Exige token de serviço (scope=internal, aud=sos-internal);
 * um token de usuário recebe 403. Esta rota NÃO consulta o bucket-service de volta, o que elimina
 * o ciclo bucket-service -> object-service -> bucket-service durante a exclusão de um bucket.
 */
@RestController
@RequestMapping("/internal/buckets/{bucketId}/objects")
@Tag(name = "Interno", description = "Uso exclusivo entre serviços (token de serviço)")
public class InternalObjectController {

    private final ObjectService service;

    public InternalObjectController(ObjectService service) {
        this.service = service;
    }

    @Operation(summary = "Quantidade de objetos de um bucket")
    @GetMapping("/count")
    public ObjectCountResponse count(@PathVariable UUID bucketId) {
        return new ObjectCountResponse(service.countObjects(bucketId));
    }
}
