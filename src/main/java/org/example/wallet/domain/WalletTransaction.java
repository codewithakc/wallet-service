package org.example.wallet.domain;

import java.time.Instant;

public record WalletTransaction(
        String transactionId,
        String walletId,
        MoneyMovementType type,
        long amount,
        String referenceId,
        String idempotencyKey,
        long balanceAfter,
        Instant createdAt) {
}
