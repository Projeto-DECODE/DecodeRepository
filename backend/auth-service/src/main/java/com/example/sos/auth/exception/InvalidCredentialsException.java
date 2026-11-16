package com.example.sos.auth.exception;

/** Mensagem única para e-mail inexistente e senha incorreta, para não revelar qual falhou. */
public class InvalidCredentialsException extends RuntimeException {

    public InvalidCredentialsException() {
        super("Credenciais inválidas");
    }
}
