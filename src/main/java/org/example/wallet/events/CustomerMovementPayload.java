package org.example.wallet.events;

public record CustomerMovementPayload(
        String customerWalletId,
        String customerTransactionId,
        long amount,
        String referenceId,
        String idempotencyKey,
        String companyWalletId) {
}
