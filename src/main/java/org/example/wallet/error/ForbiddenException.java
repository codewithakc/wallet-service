package org.example.wallet.error;

import jakarta.ws.rs.core.Response;

public class ForbiddenException extends DomainException {
    public ForbiddenException(String message) {
        super("FORBIDDEN", message, Response.Status.FORBIDDEN);
    }
}
