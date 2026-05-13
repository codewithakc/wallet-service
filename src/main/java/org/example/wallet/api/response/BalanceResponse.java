package org.example.wallet.api.response;

public record BalanceResponse(
        String walletId,
        long balance) {
}
