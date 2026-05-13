package org.example.wallet.store.inmemory;

import org.example.wallet.domain.IdempotencyRecord;
import org.example.wallet.store.IdempotencyRepository;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory idempotency outcome store keyed by {@code walletId + idempotencyKey}.
 */
public class InMemoryIdempotencyRepository implements IdempotencyRepository {
    private final ConcurrentHashMap<String, IdempotencyRecord> records = new ConcurrentHashMap<>();

    @Override
    public Optional<IdempotencyRecord> find(String walletId, String idempotencyKey) {
        return Optional.ofNullable(records.get(key(walletId, idempotencyKey)));
    }

    @Override
    public IdempotencyRecord save(IdempotencyRecord record) {
        records.putIfAbsent(key(record.walletId(), record.idempotencyKey()), record);
        return records.get(key(record.walletId(), record.idempotencyKey()));
    }

    private String key(String walletId, String idempotencyKey) {
        return walletId + "::" + idempotencyKey;
    }
}
