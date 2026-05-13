package org.example.wallet.api.response;

import org.example.wallet.domain.DeductionResult;

public record DeductResponse(
        String walletId,
        long balance,
        String transactionId,
        String status,
        long deductedAmount,
        boolean servedFromIdempotencyCache) {
    public static DeductResponse from(DeductionResult result) {
        return new DeductResponse(
                result.walletId(),
                result.balanceAfter(),
                result.transactionId(),
                result.status().name(),
                result.deductedAmount(),
                result.servedFromIdempotencyCache());
    }
}
