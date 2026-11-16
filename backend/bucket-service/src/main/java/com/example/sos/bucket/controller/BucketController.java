package com.example.sos.bucket.controller;

import com.example.sos.bucket.dto.BucketResponse;
import com.example.sos.bucket.dto.CreateBucketRequest;
import com.example.sos.bucket.exception.ApiError;
import com.example.sos.bucket.security.CurrentUser;
import com.example.sos.bucket.service.BucketService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/buckets")
@Tag(name = "Buckets", description = "Buckets do usuário autenticado")
@ApiResponse(responseCode = "401", description = "Token ausente, inválido ou expirado",
        content = @Content(schema = @Schema(implementation = ApiError.class)))
@ApiResponse(responseCode = "403", description = "Token sem o escopo necessário",
        content = @Content(schema = @Schema(implementation = ApiError.class)))
public class BucketController {

    private final BucketService service;

    public BucketController(BucketService service) {
        this.service = service;
    }

    @Operation(summary = "Cria um bucket",
            description = "O proprietário é sempre o usuário do token; o corpo não aceita esse campo.")
    @ApiResponse(responseCode = "201", description = "Bucket criado")
    @ApiResponse(responseCode = "400", description = "Nome inválido",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    @ApiResponse(responseCode = "409", description = "Nome já usado por este usuário",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    @PostMapping
    public ResponseEntity<BucketResponse> create(@AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody CreateBucketRequest request) {
        var bucket = service.create(CurrentUser.id(jwt), request.name());
        return ResponseEntity.status(HttpStatus.CREATED).body(BucketResponse.from(bucket));
    }

    @Operation(summary = "Lista os buckets do usuário autenticado")
    @GetMapping
    public List<BucketResponse> list(@AuthenticationPrincipal Jwt jwt) {
        return service.list(CurrentUser.id(jwt)).stream().map(BucketResponse::from).toList();
    }

    @Operation(summary = "Consulta um bucket")
    @ApiResponse(responseCode = "404", description = "Bucket inexistente ou de outro usuário",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    @GetMapping("/{bucketId}")
    public BucketResponse get(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID bucketId) {
        return BucketResponse.from(service.get(CurrentUser.id(jwt), bucketId));
    }

    @Operation(summary = "Exclui um bucket vazio")
    @ApiResponse(responseCode = "204", description = "Bucket excluído")
    @ApiResponse(responseCode = "404", description = "Bucket inexistente ou de outro usuário",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    @ApiResponse(responseCode = "409", description = "Bucket ainda contém objetos",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    @ApiResponse(responseCode = "503", description = "object-service indisponível",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    @DeleteMapping("/{bucketId}")
    public ResponseEntity<Void> delete(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID bucketId) {
        service.delete(CurrentUser.id(jwt), bucketId);
        return ResponseEntity.noContent().build();
    }
}
