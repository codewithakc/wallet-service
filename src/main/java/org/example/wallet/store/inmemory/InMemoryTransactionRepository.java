package org.example.wallet.store.inmemory;

import org.example.wallet.domain.WalletTransaction;
import org.example.wallet.store.TransactionRepository;

import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * In-memory ledger store used by the runnable local profile.
 */
public class InMemoryTransactionRepository implements TransactionRepository {
    private final ConcurrentHashMap<String, CopyOnWriteArrayList<WalletTransaction>> transactions = new ConcurrentHashMap<>();

    @Override
    public WalletTransaction append(WalletTransaction transaction) {
        transactions.computeIfAbsent(transaction.walletId(), ignored -> new CopyOnWriteArrayList<>()).add(transaction);
        return transaction;
    }

    @Override
    public List<WalletTransaction> findByWalletId(String walletId) {
        return transactions.getOrDefault(walletId, new CopyOnWriteArrayList<>()).stream()
                .sorted(Comparator.comparing(WalletTransaction::createdAt))
                .toList();
    }
}
