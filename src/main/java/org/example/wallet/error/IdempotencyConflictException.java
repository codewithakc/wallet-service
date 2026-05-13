package org.example.wallet.error;

import jakarta.ws.rs.core.Response;

/**
 * Raised when the same idempotency key is reused with a different request payload.
 */
public class IdempotencyConflictException extends DomainException {
    public IdempotencyConflictException(String message) {
        super("IDEMPOTENCY_CONFLICT", message, Response.Status.CONFLICT);
    }
}
