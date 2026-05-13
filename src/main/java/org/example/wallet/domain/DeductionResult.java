package org.example.wallet.domain;

public record DeductionResult(
        String walletId,
        DeductionStatus status,
        long deductedAmount,
        long balanceAfter,
        String transactionId,
        String errorCode,
        String errorMessage,
        boolean servedFromIdempotencyCache) {
    public boolean isSuccess() {
        return status == DeductionStatus.SUCCESS;
    }

    public static DeductionResult fromRecord(IdempotencyRecord record, boolean servedFromIdempotencyCache) {
        return new DeductionResult(
                record.walletId(),
                record.status(),
                record.requestedAmount(),
                record.balanceAfter(),
                record.transactionId(),
                record.errorCode(),
                record.errorMessage(),
                servedFromIdempotencyCache);
    }
}
