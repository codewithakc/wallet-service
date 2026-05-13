package org.example.wallet.error;

import jakarta.ws.rs.core.Response;

public class WalletNotFoundException extends DomainException {
    public WalletNotFoundException(String walletId) {
        super("WALLET_NOT_FOUND", "Wallet " + walletId + " was not found.", Response.Status.NOT_FOUND);
    }
}
