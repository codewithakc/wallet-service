package org.example.wallet.support;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.example.wallet.WalletServiceConfiguration;
import org.example.wallet.company.CompanyMirrorLedgerHandler;
import org.example.wallet.company.CompanyMirrorService;
import org.example.wallet.company.CompanyWalletBootstrap;
import org.example.wallet.events.EventPublisher;
import org.example.wallet.events.OutboxEventPublisher;
import org.example.wallet.events.WalletEventPayloadSerializer;
import org.example.wallet.metrics.NoOpMetricsPort;
import org.example.wallet.outbox.OutboxProcessor;
import org.example.wallet.outbox.WalletEventHandler;
import org.example.wallet.service.WalletApplicationService;
import org.example.wallet.store.MirrorIdempotencyRepository;
import org.example.wallet.store.OutboxRepository;
import org.example.wallet.store.inmemory.InMemoryIdempotencyRepository;
import org.example.wallet.store.inmemory.InMemoryMirrorIdempotencyRepository;
import org.example.wallet.store.inmemory.InMemoryOutboxRepository;
import org.example.wallet.store.inmemory.InMemoryTransactionRepository;
import org.example.wallet.store.inmemory.InMemoryWalletMutationExecutor;
import org.example.wallet.store.inmemory.InMemoryWalletRepository;

import java.util.List;

public final class WalletServiceTestSupport {
    private WalletServiceTestSupport() {
    }

    public static WiredComponents wiredComponents() {
        WalletServiceConfiguration configuration = new WalletServiceConfiguration();
        InMemoryWalletRepository walletRepository = new InMemoryWalletRepository();
        InMemoryTransactionRepository transactionRepository = new InMemoryTransactionRepository();
        InMemoryIdempotencyRepository idempotencyRepository = new InMemoryIdempotencyRepository();
        InMemoryOutboxRepository outboxRepository = new InMemoryOutboxRepository();
        InMemoryMirrorIdempotencyRepository mirrorIdempotencyRepository = new InMemoryMirrorIdempotencyRepository();
        InMemoryWalletMutationExecutor mutationExecutor = new InMemoryWalletMutationExecutor();

        String companyWalletId = CompanyWalletBootstrap.ensureCompanyWallet(walletRepository, configuration);

        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        WalletEventPayloadSerializer payloadSerializer = new WalletEventPayloadSerializer(objectMapper);

        EventPublisher eventPublisher =
                new OutboxEventPublisher(outboxRepository, payloadSerializer, companyWalletId);

        CompanyMirrorService companyMirrorService =
                new CompanyMirrorService(
                        walletRepository,
                        transactionRepository,
                        mutationExecutor,
                        mirrorIdempotencyRepository);

        List<WalletEventHandler> handlers =
                List.of(new CompanyMirrorLedgerHandler(companyMirrorService, payloadSerializer));

        OutboxProcessor outboxProcessor =
                new OutboxProcessor(outboxRepository, handlers, 500, 50);

        WalletApplicationService walletApplicationService =
                new WalletApplicationService(
                        walletRepository,
                        transactionRepository,
                        idempotencyRepository,
                        mutationExecutor,
                        new NoOpMetricsPort(),
                        eventPublisher);

        return new WiredComponents(
                walletApplicationService,
                walletRepository,
                transactionRepository,
                outboxRepository,
                outboxProcessor,
                companyWalletId);
    }

    public record WiredComponents(
            WalletApplicationService walletApplicationService,
            InMemoryWalletRepository walletRepository,
            InMemoryTransactionRepository transactionRepository,
            InMemoryOutboxRepository outboxRepository,
            OutboxProcessor outboxProcessor,
            String companyWalletId) {
    }
}
