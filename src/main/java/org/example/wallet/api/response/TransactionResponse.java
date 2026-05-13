package org.example.wallet.api.response;

import com.fasterxml.jackson.annotation.JsonFormat;
import org.example.wallet.domain.WalletTransaction;

import java.time.Instant;

public record TransactionResponse(
        String transactionId,
        String type,
        long amount,
        String referenceId,
        String idempotencyKey,
        long balanceAfter,
        @JsonFormat(shape = JsonFormat.Shape.STRING)
        Instant createdAt) {
    public static TransactionResponse from(WalletTransaction transaction) {
        return new TransactionResponse(
                transaction.transactionId(),
                transaction.type().name(),
                transaction.amount(),
                transaction.referenceId(),
                transaction.idempotencyKey(),
                transaction.balanceAfter(),
                transaction.createdAt());
    }
}
