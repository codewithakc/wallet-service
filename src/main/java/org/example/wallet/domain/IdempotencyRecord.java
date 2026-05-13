package org.example.wallet.domain;

import java.time.Instant;

public record IdempotencyRecord(
        String walletId,
        String idempotencyKey,
        long requestedAmount,
        DeductionStatus status,
        String transactionId,
        long balanceAfter,
        String errorCode,
        String errorMessage,
        Instant createdAt) {
}
