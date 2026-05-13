package org.example.wallet.error;

import jakarta.ws.rs.core.Response;

public class DomainException extends RuntimeException {
    private final String errorCode;
    private final Response.Status status;

    public DomainException(String errorCode, String message, Response.Status status) {
        super(message);
        this.errorCode = errorCode;
        this.status = status;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public Response.Status getStatus() {
        return status;
    }
}
