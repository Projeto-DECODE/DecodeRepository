package com.example.sos.objects.exception;

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
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(BucketNotFoundException.class)
    ResponseEntity<ApiError> bucketNotFound(BucketNotFoundException ex) {
        return build(HttpStatus.NOT_FOUND, "BUCKET_NOT_FOUND", ex.getMessage());
    }

    @ExceptionHandler(ObjectNotFoundException.class)
    ResponseEntity<ApiError> objectNotFound(ObjectNotFoundException ex) {
        return build(HttpStatus.NOT_FOUND, "OBJECT_NOT_FOUND", ex.getMessage());
    }

    @ExceptionHandler(BucketNotWritableException.class)
    ResponseEntity<ApiError> bucketNotWritable(BucketNotWritableException ex) {
        return build(HttpStatus.CONFLICT, "BUCKET_NOT_WRITABLE", ex.getMessage());
    }

    @ExceptionHandler(EmptyFileException.class)
    ResponseEntity<ApiError> emptyFile(EmptyFileException ex) {
        return build(HttpStatus.BAD_REQUEST, "EMPTY_FILE", ex.getMessage());
    }

    @ExceptionHandler(FileTooLargeException.class)
    ResponseEntity<ApiError> tooLarge(FileTooLargeException ex) {
        return build(HttpStatus.PAYLOAD_TOO_LARGE, "PAYLOAD_TOO_LARGE", ex.getMessage());
    }

    @ExceptionHandler(DependencyUnavailableException.class)
    ResponseEntity<ApiError> dependencyUnavailable(DependencyUnavailableException ex) {
        log.error("op=dependency result=unavailable reason={}", ex.getMessage());
        return build(HttpStatus.SERVICE_UNAVAILABLE, "DEPENDENCY_UNAVAILABLE",
                "Serviço dependente indisponível. Tente novamente em instantes");
    }

    @ExceptionHandler(UpstreamUnauthorizedException.class)
    ResponseEntity<ApiError> upstreamUnauthorized(UpstreamUnauthorizedException ex) {
        log.warn("op=dependency result=token-rejected");
        return build(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "Token ausente, inválido ou expirado");
    }

    @ExceptionHandler(InvalidTokenSubjectException.class)
    ResponseEntity<ApiError> invalidSubject(InvalidTokenSubjectException ex) {
        return build(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "Token ausente, inválido ou expirado");
    }

    @ExceptionHandler(StorageException.class)
    ResponseEntity<ApiError> storage(StorageException ex) {
        // O detalhe (que pode conter caminhos) fica só no log do servidor.
        log.error("op=storage result=error detail={}", ex.getMessage(), ex.getCause());
        return build(HttpStatus.INTERNAL_SERVER_ERROR, "STORAGE_ERROR", "Falha no armazenamento do arquivo");
    }

    @ExceptionHandler(AccessDeniedException.class)
    ResponseEntity<ApiError> accessDenied(AccessDeniedException ex) {
        return build(HttpStatus.FORBIDDEN, "FORBIDDEN", "Acesso negado");
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ApiError> unexpected(Exception ex) {
        // Se a versão do Spring não tratar o limite de upload na classe base, cai aqui e ainda vira 413.
        if (ex instanceof MaxUploadSizeExceededException) {
            return build(HttpStatus.PAYLOAD_TOO_LARGE, "PAYLOAD_TOO_LARGE",
                    "O arquivo excede o tamanho máximo permitido");
        }
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
