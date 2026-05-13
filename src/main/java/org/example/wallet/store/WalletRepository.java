package org.example.wallet.store;

import org.example.wallet.domain.Wallet;

import java.util.Optional;

/**
 * Persists the current wallet aggregate state.
 *
 * <p>The wallet keeps the latest balance for fast reads, while the append-only ledger stores the
 * detailed money movement history separately.
 */
public interface WalletRepository {
    /**
     * Creates a new wallet aggregate.
     *
     * @throws org.example.wallet.error.DuplicateWalletException if the wallet already exists
     */
    Wallet create(Wallet wallet);

    /**
     * Looks up the current wallet snapshot by wallet ID.
     */
    Optional<Wallet> findById(String walletId);

    /**
     * Persists an updated wallet snapshot after a balance-changing operation.
     */
    Wallet save(Wallet wallet);

    /**
     * Checks whether a wallet ID is already present in the store.
     */
    boolean exists(String walletId);
}
