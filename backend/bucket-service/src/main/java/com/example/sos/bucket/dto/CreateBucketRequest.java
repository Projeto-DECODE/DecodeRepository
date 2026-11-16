package com.example.sos.bucket.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Não há campo de proprietário: ele é sempre derivado do JWT. */
public record CreateBucketRequest(
        @Schema(description = "3 a 63 caracteres: letras minúsculas, números e hífen",
                example = "documentos")
        @NotBlank
        @Size(min = 3, max = 63)
        @Pattern(regexp = "^[a-z0-9]([a-z0-9-]*[a-z0-9])?$",
                message = "use apenas letras minúsculas, números e hífen, sem hífen no início ou no fim")
        String name) {
}
