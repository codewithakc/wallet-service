package org.example.wallet.store.inmemory;

import org.example.wallet.domain.Wallet;
import org.example.wallet.error.DuplicateWalletException;
import org.example.wallet.store.WalletRepository;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory implementation of {@link WalletRepository} for local development and tests.
 */
public class InMemoryWalletRepository implements WalletRepository {
    private final ConcurrentHashMap<String, Wallet> wallets = new ConcurrentHashMap<>();

    @Override
    public Wallet create(Wallet wallet) {
        Wallet existing = wallets.putIfAbsent(wallet.getWalletId(), wallet);
        if (existing != null) {
            throw new DuplicateWalletException(wallet.getWalletId());
        }
        return wallet;
    }

    @Override
    public Optional<Wallet> findById(String walletId) {
        return Optional.ofNullable(wallets.get(walletId));
    }

    @Override
    public Wallet save(Wallet wallet) {
        wallets.put(wallet.getWalletId(), wallet);
        return wallet;
    }

    @Override
    public boolean exists(String walletId) {
        return wallets.containsKey(walletId);
    }
}
