package org.example.wallet.store;

/**
 * Tracks mirror ledger operations so outbox redelivery does not double-apply company entries.
 */
public interface MirrorIdempotencyRepository {
    boolean exists(String companyWalletId, String mirrorIdempotencyKey);

    void save(String companyWalletId, String mirrorIdempotencyKey);
}
