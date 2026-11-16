package com.example.sos.objects.exception;

/** O bucket-service recusou o token encaminhado (por exemplo, expirou entre as duas validações). */
public class UpstreamUnauthorizedException extends RuntimeException {

    public UpstreamUnauthorizedException() {
        super("Token recusado pelo bucket-service");
    }
}
