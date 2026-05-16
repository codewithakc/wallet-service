package org.example.wallet.store.inmemory;

import org.example.wallet.domain.OutboxEvent;
import org.example.wallet.domain.OutboxEventStatus;
import org.example.wallet.store.OutboxRepository;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

public class InMemoryOutboxRepository implements OutboxRepository {
    private final ConcurrentHashMap<String, OutboxEvent> events = new ConcurrentHashMap<>();

    @Override
    public OutboxEvent append(OutboxEvent event) {
        events.put(event.eventId(), event);
        return event;
    }

    @Override
    public List<OutboxEvent> findPending(int limit) {
        return events.values().stream()
                .filter(event -> event.status() == OutboxEventStatus.PENDING)
                .sorted(Comparator.comparing(OutboxEvent::createdAt))
                .limit(limit)
                .toList();
    }

    @Override
    public void markPublished(String eventId) {
        OutboxEvent existing = events.get(eventId);
        if (existing == null) {
            return;
        }
        events.put(
                eventId,
                new OutboxEvent(
                        existing.eventId(),
                        existing.eventType(),
                        existing.payload(),
                        OutboxEventStatus.PUBLISHED,
                        existing.createdAt(),
                        Instant.now()));
    }
}
