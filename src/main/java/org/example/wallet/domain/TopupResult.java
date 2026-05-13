package org.example.wallet.domain;

public record TopupResult(
        String walletId,
        long balanceAfter,
        String transactionId,
        String referenceId) {
}
