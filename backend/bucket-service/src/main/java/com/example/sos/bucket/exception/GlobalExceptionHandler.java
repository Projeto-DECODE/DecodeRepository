package com.example.sos.bucket.exception;

import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(BucketNotFoundException.class)
    ResponseEntity<ApiError> notFound(BucketNotFoundException ex) {
        return build(HttpStatus.NOT_FOUND, "BUCKET_NOT_FOUND", ex.getMessage());
    }

    @ExceptionHandler(BucketNameAlreadyExistsException.class)
    ResponseEntity<ApiError> nameExists(BucketNameAlreadyExistsException ex) {
        return build(HttpStatus.CONFLICT, "BUCKET_NAME_ALREADY_EXISTS", ex.getMessage());
    }

    @ExceptionHandler(BucketNotEmptyException.class)
    ResponseEntity<ApiError> notEmpty(BucketNotEmptyException ex) {
        return build(HttpStatus.CONFLICT, "BUCKET_NOT_EMPTY", ex.getMessage());
    }

    @ExceptionHandler(DependencyUnavailableException.class)
    ResponseEntity<ApiError> dependencyUnavailable(DependencyUnavailableException ex) {
        log.error("op=dependency result=unavailable reason={}", ex.getMessage());
        return build(HttpStatus.SERVICE_UNAVAILABLE, "DEPENDENCY_UNAVAILABLE",
                "Serviço dependente indisponível. Tente novamente em instantes");
    }

    @ExceptionHandler(InvalidTokenSubjectException.class)
    ResponseEntity<ApiError> invalidSubject(InvalidTokenSubjectException ex) {
        return build(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "Token ausente, inválido ou expirado");
    }

    @ExceptionHandler(AccessDeniedException.class)
    ResponseEntity<ApiError> accessDenied(AccessDeniedException ex) {
        return build(HttpStatus.FORBIDDEN, "FORBIDDEN", "Acesso negado");
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ApiError> unexpected(Exception ex) {
        log.error("op=unhandled result=error", ex);
        return build(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "Erro interno inesperado");
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        List<ApiError.FieldViolation> violations = ex.getBindingResult().getFieldErrors().stream()
                .map(f -> new ApiError.FieldViolation(f.getField(), f.getDefaultMessage()))
                .toList();
        ApiError body = ApiError.of("VALIDATION_ERROR", "Dados de entrada inválidos", 400, violations);
        return ResponseEntity.badRequest().body(body);
    }

    @Override
    protected ResponseEntity<Object> handleExceptionInternal(Exception ex, Object body, HttpHeaders headers,
            HttpStatusCode statusCode, WebRequest request) {
        HttpStatus status = HttpStatus.resolve(statusCode.value());
        String code = status != null ? status.name() : "HTTP_ERROR";
        String message = status != null ? status.getReasonPhrase() : "Erro na requisição";
        return ResponseEntity.status(statusCode).body(ApiError.of(code, message, statusCode.value()));
    }

    private static ResponseEntity<ApiError> build(HttpStatus status, String code, String message) {
        return ResponseEntity.status(status).body(ApiError.of(code, message, status.value()));
    }
}
