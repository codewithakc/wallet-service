package org.example.wallet.error;

import jakarta.ws.rs.core.Response;

public class DuplicateWalletException extends DomainException {
    public DuplicateWalletException(String walletId) {
        super("DUPLICATE_WALLET", "Wallet " + walletId + " already exists.", Response.Status.CONFLICT);
    }
}
