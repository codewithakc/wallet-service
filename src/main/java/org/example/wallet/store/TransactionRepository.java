package org.example.wallet.store;

import org.example.wallet.domain.WalletTransaction;

import java.util.List;

/**
 * Stores the append-only ledger of wallet money movements.
 */
public interface TransactionRepository {
    /**
     * Appends a new ledger entry after a successful money movement.
     */
    WalletTransaction append(WalletTransaction transaction);

    /**
     * Returns the full ledger history for a wallet, ordered by creation time.
     */
    List<WalletTransaction> findByWalletId(String walletId);
}
