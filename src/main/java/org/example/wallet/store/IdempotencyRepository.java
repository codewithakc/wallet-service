package org.example.wallet.store;

import org.example.wallet.domain.IdempotencyRecord;

import java.util.Optional;

/**
 * Stores the final outcome for idempotent deduction requests.
 *
 * <p>This repository is intentionally separate from the transaction ledger because it must also
 * remember rejected deduction attempts where no money movement occurred.
 */
public interface IdempotencyRepository {
    /**
     * Looks up a previously stored deduction outcome for the given wallet and idempotency key.
     */
    Optional<IdempotencyRecord> find(String walletId, String idempotencyKey);

    /**
     * Stores the canonical outcome for a deduction request.
     */
    IdempotencyRecord save(IdempotencyRecord record);
}
