package org.example.wallet.store;

import org.example.wallet.domain.OutboxEvent;

import java.util.List;

public interface OutboxRepository {
    OutboxEvent append(OutboxEvent event);

    List<OutboxEvent> findPending(int limit);

    void markPublished(String eventId);
}
