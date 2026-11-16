package com.example.sos.auth.exception;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.util.List;
import org.slf4j.MDC;

/** Formato único de erro. Nunca carrega stack trace, caminhos internos, tokens ou valores rejeitados. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiError(
        String code,
        String message,
        int status,
        Instant timestamp,
        String traceId,
        List<FieldViolation> details) {

    public record FieldViolation(String field, String message) {
    }

    public static ApiError of(String code, String message, int status) {
        return new ApiError(code, message, status, Instant.now(), MDC.get("traceId"), null);
    }

    public static ApiError of(String code, String message, int status, List<FieldViolation> details) {
        return new ApiError(code, message, status, Instant.now(), MDC.get("traceId"), details);
    }
}
