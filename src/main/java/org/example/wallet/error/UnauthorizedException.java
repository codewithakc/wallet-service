package org.example.wallet.error;

import jakarta.ws.rs.core.Response;

public class UnauthorizedException extends DomainException {
    public UnauthorizedException(String message) {
        super("UNAUTHORIZED", message, Response.Status.UNAUTHORIZED);
    }
}
