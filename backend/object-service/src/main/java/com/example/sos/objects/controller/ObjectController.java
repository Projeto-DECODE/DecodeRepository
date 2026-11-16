package com.example.sos.objects.controller;

import com.example.sos.objects.dto.DownloadedObject;
import com.example.sos.objects.dto.ObjectResponse;
import com.example.sos.objects.dto.PageResponse;
import com.example.sos.objects.exception.ApiError;
import com.example.sos.objects.exception.EmptyFileException;
import com.example.sos.objects.exception.StorageException;
import com.example.sos.objects.security.Caller;
import com.example.sos.objects.service.ObjectService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.springframework.core.io.InputStreamResource;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/buckets/{bucketId}/objects")
@Tag(name = "Objetos", description = "Objetos dentro de buckets do usuário autenticado")
@ApiResponse(responseCode = "401", description = "Token ausente, inválido ou expirado",
        content = @Content(schema = @Schema(implementation = ApiError.class)))
@ApiResponse(responseCode = "404", description = "Bucket ou objeto inexistente, ou de outro usuário",
        content = @Content(schema = @Schema(implementation = ApiError.class)))
@ApiResponse(responseCode = "503", description = "bucket-service indisponível",
        content = @Content(schema = @Schema(implementation = ApiError.class)))
public class ObjectController {

    private static final int MAX_PAGE_SIZE = 100;

    private final ObjectService service;

    public ObjectController(ObjectService service) {
        this.service = service;
    }

    @Operation(summary = "Envia um objeto (multipart/form-data, campo \"file\")")
    @ApiResponse(responseCode = "201", description = "Objeto armazenado")
    @ApiResponse(responseCode = "400", description = "Arquivo ausente ou vazio",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    @ApiResponse(responseCode = "409", description = "Bucket em exclusão não aceita uploads",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    @ApiResponse(responseCode = "413", description = "Arquivo acima do limite",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ObjectResponse> upload(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID bucketId,
            @RequestPart("file") MultipartFile file) {
        if (file.isEmpty()) {
            throw new EmptyFileException();
        }
        try (InputStream in = file.getInputStream()) {
            var saved = service.upload(Caller.from(jwt), bucketId, file.getOriginalFilename(),
                    file.getContentType(), in);
            return ResponseEntity.status(HttpStatus.CREATED).body(ObjectResponse.from(saved));
        } catch (IOException e) {
            throw new StorageException("Falha ao ler o arquivo enviado", e);
        }
    }

    @Operation(summary = "Lista os objetos do bucket (paginado, mais recentes primeiro)")
    @GetMapping
    public PageResponse<ObjectResponse> list(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID bucketId,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        var pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), MAX_PAGE_SIZE),
                Sort.by(Sort.Direction.DESC, "createdAt").and(Sort.by("id")));
        return PageResponse.from(service.list(Caller.from(jwt), bucketId, pageable), ObjectResponse::from);
    }

    @Operation(summary = "Consulta os metadados de um objeto")
    @GetMapping("/{objectId}")
    public ObjectResponse metadata(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID bucketId,
            @PathVariable UUID objectId) {
        return ObjectResponse.from(service.getMetadata(Caller.from(jwt), bucketId, objectId));
    }

    @Operation(summary = "Baixa o conteúdo de um objeto")
    @ApiResponse(responseCode = "200", description = "Conteúdo binário",
            content = @Content(mediaType = MediaType.APPLICATION_OCTET_STREAM_VALUE))
    @GetMapping("/{objectId}/download")
    public ResponseEntity<InputStreamResource> download(@AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID bucketId, @PathVariable UUID objectId) {
        DownloadedObject object = service.download(Caller.from(jwt), bucketId, objectId);
        // Content-Type fixo: o tipo informado pelo cliente não é confiável e nunca é reenviado ao navegador.
        // attachment + nosniff impedem que o navegador execute ou interprete o conteúdo.
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .contentLength(object.sizeBytes())
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(object.filename(), StandardCharsets.UTF_8).build().toString())
                .header("X-Content-Type-Options", "nosniff")
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(new InputStreamResource(object.stream()));
    }

    @Operation(summary = "Exclui um objeto")
    @ApiResponse(responseCode = "204", description = "Objeto excluído")
    @DeleteMapping("/{objectId}")
    public ResponseEntity<Void> delete(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID bucketId,
            @PathVariable UUID objectId) {
        service.delete(Caller.from(jwt), bucketId, objectId);
        return ResponseEntity.noContent().build();
    }
}
