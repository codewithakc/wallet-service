package org.example.wallet.store;

import java.util.function.Supplier;

/**
 * Defines the atomic execution boundary for wallet mutations.
 *
 * <p>The in-memory implementation uses per-wallet locks, while a future database-backed
 * implementation can map the same contract to a transaction with optimistic or pessimistic locking.
 */
public interface WalletMutationExecutor {
    /**
     * Executes a wallet-scoped state transition atomically for the supplied wallet ID.
     */
    <T> T execute(String walletId, Supplier<T> operation);
}
