package org.example.wallet.store.inmemory;

import org.example.wallet.store.MirrorIdempotencyRepository;

import java.util.concurrent.ConcurrentHashMap;

public class InMemoryMirrorIdempotencyRepository implements MirrorIdempotencyRepository {
    private final ConcurrentHashMap<String, Boolean> processedKeys = new ConcurrentHashMap<>();

    @Override
    public boolean exists(String companyWalletId, String mirrorIdempotencyKey) {
        return processedKeys.containsKey(compositeKey(companyWalletId, mirrorIdempotencyKey));
    }

    @Override
    public void save(String companyWalletId, String mirrorIdempotencyKey) {
        processedKeys.put(compositeKey(companyWalletId, mirrorIdempotencyKey), Boolean.TRUE);
    }

    private static String compositeKey(String companyWalletId, String mirrorIdempotencyKey) {
        return companyWalletId + ":" + mirrorIdempotencyKey;
    }
}
