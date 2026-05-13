package org.example.wallet.error;

import jakarta.ws.rs.core.Response;

public class InsufficientBalanceException extends DomainException {
    public InsufficientBalanceException(String message) {
        super("INSUFFICIENT_BALANCE", message, Response.Status.CONFLICT);
    }
}
