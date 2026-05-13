package org.example.wallet.error;

import jakarta.ws.rs.core.Response;

public class InvalidRequestException extends DomainException {
    public InvalidRequestException(String message) {
        super("INVALID_REQUEST", message, Response.Status.BAD_REQUEST);
    }
}
