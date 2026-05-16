package org.example.wallet.events;

import org.example.wallet.domain.DeductionResult;
import org.example.wallet.domain.OutboxEvent;
import org.example.wallet.domain.OutboxEventStatus;
import org.example.wallet.domain.Wallet;
import org.example.wallet.domain.WalletEventType;
import org.example.wallet.domain.WalletTransaction;
import org.example.wallet.store.OutboxRepository;

import java.time.Instant;
import java.util.UUID;

/**
 * Persists wallet domain events to the transactional outbox instead of publishing directly.
 */
public class OutboxEventPublisher implements EventPublisher {
    private final OutboxRepository outboxRepository;
    private final WalletEventPayloadSerializer payloadSerializer;
    private final String companyWalletId;

    public OutboxEventPublisher(
            OutboxRepository outboxRepository,
            WalletEventPayloadSerializer payloadSerializer,
            String companyWalletId) {
        this.outboxRepository = outboxRepository;
        this.payloadSerializer = payloadSerializer;
        this.companyWalletId = companyWalletId;
    }

    @Override
    public void publishWalletCreated(Wallet wallet) {
        // Company mirror on wallet creation is out of scope.
    }

    @Override
    public void publishWalletToppedUp(Wallet wallet, WalletTransaction transaction) {
        appendCustomerMovement(WalletEventType.CUSTOMER_TOPPED_UP, wallet, transaction);
    }

    @Override
    public void publishWalletDeducted(DeductionResult deductionResult, WalletTransaction transaction) {
        if (!deductionResult.isSuccess() || transaction == null) {
            return;
        }
        CustomerMovementPayload payload =
                new CustomerMovementPayload(
                        transaction.walletId(),
                        transaction.transactionId(),
                        transaction.amount(),
                        transaction.referenceId(),
                        transaction.idempotencyKey(),
                        companyWalletId);
        appendEvent(WalletEventType.CUSTOMER_DEDUCTED, payload);
    }

    @Override
    public void publishWalletDeductionRejected(DeductionResult deductionResult) {
        // Rejected customer deducts must not mirror to the company ledger.
    }

    private void appendCustomerMovement(WalletEventType eventType, Wallet wallet, WalletTransaction transaction) {
        CustomerMovementPayload payload =
                new CustomerMovementPayload(
                        wallet.getWalletId(),
                        transaction.transactionId(),
                        transaction.amount(),
                        transaction.referenceId(),
                        transaction.idempotencyKey(),
                        companyWalletId);
        appendEvent(eventType, payload);
    }

    private void appendEvent(WalletEventType eventType, CustomerMovementPayload payload) {
        OutboxEvent event =
                new OutboxEvent(
                        UUID.randomUUID().toString(),
                        eventType,
                        payloadSerializer.serialize(payload),
                        OutboxEventStatus.PENDING,
                        Instant.now(),
                        null);
        outboxRepository.append(event);
    }
}
