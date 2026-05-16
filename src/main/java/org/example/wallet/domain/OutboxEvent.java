package org.example.wallet.domain;

import java.time.Instant;

public record OutboxEvent(
        String eventId,
        WalletEventType eventType,
        String payload,
        OutboxEventStatus status,
        Instant createdAt,
        Instant publishedAt) {
}
