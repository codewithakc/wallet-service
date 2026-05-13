package org.example.wallet.store.inmemory;

import org.example.wallet.store.WalletMutationExecutor;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;

/**
 * Serializes all balance-changing operations per wallet ID inside a single JVM.
 *
 * <p>This is what prevents concurrent in-memory requests from driving one wallet below zero while
 * still allowing different wallets to be processed in parallel.
 */
public class InMemoryWalletMutationExecutor implements WalletMutationExecutor {
    private final ConcurrentHashMap<String, ReentrantLock> lockByWalletId = new ConcurrentHashMap<>();

    /**
     * Executes the supplied operation while holding a fair wallet-specific lock.
     */
    @Override
    public <T> T execute(String walletId, Supplier<T> operation) {
        ReentrantLock lock = lockByWalletId.computeIfAbsent(walletId, ignored -> new ReentrantLock(true));
        lock.lock();
        try {
            return operation.get();
        } finally {
            lock.unlock();
        }
    }
}
