package org.example.wallet.outbox;

import io.dropwizard.lifecycle.Managed;
import org.example.wallet.domain.OutboxEvent;
import org.example.wallet.store.OutboxRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Polls pending outbox events and dispatches them to registered handlers.
 */
public class OutboxProcessor implements Managed {
    private static final Logger LOGGER = LoggerFactory.getLogger(OutboxProcessor.class);

    private final OutboxRepository outboxRepository;
    private final List<WalletEventHandler> handlers;
    private final long pollIntervalMs;
    private final int batchSize;
    private ScheduledExecutorService executor;

    public OutboxProcessor(
            OutboxRepository outboxRepository,
            List<WalletEventHandler> handlers,
            long pollIntervalMs,
            int batchSize) {
        this.outboxRepository = outboxRepository;
        this.handlers = handlers;
        this.pollIntervalMs = pollIntervalMs;
        this.batchSize = batchSize;
    }

    @Override
    public void start() {
        executor = Executors.newSingleThreadScheduledExecutor();
        executor.scheduleWithFixedDelay(this::processPendingSafely, pollIntervalMs, pollIntervalMs, TimeUnit.MILLISECONDS);
        LOGGER.info("Outbox processor started with pollIntervalMs={} batchSize={}.", pollIntervalMs, batchSize);
    }

    @Override
    public void stop() {
        if (executor != null) {
            executor.shutdownNow();
        }
    }

    public void processOnce() {
        List<OutboxEvent> pendingEvents = outboxRepository.findPending(batchSize);
        for (OutboxEvent event : pendingEvents) {
            dispatch(event);
            outboxRepository.markPublished(event.eventId());
        }
    }

    private void processPendingSafely() {
        try {
            processOnce();
        } catch (RuntimeException exception) {
            LOGGER.error("Outbox processing failed.", exception);
        }
    }

    private void dispatch(OutboxEvent event) {
        for (WalletEventHandler handler : handlers) {
            if (handler.supports(event.eventType())) {
                handler.handle(event);
                return;
            }
        }
        throw new IllegalStateException("No handler registered for event type " + event.eventType());
    }
}
