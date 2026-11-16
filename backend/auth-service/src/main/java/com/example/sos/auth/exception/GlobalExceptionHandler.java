package com.example.sos.auth.exception;

import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(EmailAlreadyInUseException.class)
    ResponseEntity<ApiError> emailInUse(EmailAlreadyInUseException ex) {
        return build(HttpStatus.CONFLICT, "EMAIL_ALREADY_IN_USE", ex.getMessage());
    }

    @ExceptionHandler(InvalidCredentialsException.class)
    ResponseEntity<ApiError> invalidCredentials(InvalidCredentialsException ex) {
        return build(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS", ex.getMessage());
    }

    @ExceptionHandler(InvalidClientException.class)
    ResponseEntity<ApiError> invalidClient(InvalidClientException ex) {
        return build(HttpStatus.UNAUTHORIZED, "INVALID_CLIENT", ex.getMessage());
    }

    @ExceptionHandler(UnsupportedGrantTypeException.class)
    ResponseEntity<ApiError> unsupportedGrant(UnsupportedGrantTypeException ex) {
        return build(HttpStatus.BAD_REQUEST, "UNSUPPORTED_GRANT_TYPE", ex.getMessage());
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ApiError> unexpected(Exception ex) {
        log.error("op=unhandled result=error", ex);
        return build(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "Erro interno inesperado");
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        // Só campo e mensagem: o valor rejeitado nunca é devolvido (poderia ser uma senha).
        List<ApiError.FieldViolation> violations = ex.getBindingResult().getFieldErrors().stream()
                .map(f -> new ApiError.FieldViolation(f.getField(), f.getDefaultMessage()))
                .toList();
        ApiError body = ApiError.of("VALIDATION_ERROR", "Dados de entrada inválidos", 400, violations);
        return ResponseEntity.badRequest().body(body);
    }

    /** Todas as demais exceções do Spring MVC (corpo ilegível, método não permitido, etc.). */
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
