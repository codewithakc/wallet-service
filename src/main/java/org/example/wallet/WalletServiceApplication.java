package org.example.wallet;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import io.dropwizard.core.Application;
import io.dropwizard.core.setup.Bootstrap;
import io.dropwizard.core.setup.Environment;
import org.example.wallet.api.WalletResource;
import org.example.wallet.auth.AuthFilter;
import org.example.wallet.company.CompanyMirrorLedgerHandler;
import org.example.wallet.company.CompanyMirrorService;
import org.example.wallet.company.CompanyWalletBootstrap;
import org.example.wallet.error.DomainExceptionMapper;
import org.example.wallet.error.GenericExceptionMapper;
import org.example.wallet.events.EventPublisher;
import org.example.wallet.events.OutboxEventPublisher;
import org.example.wallet.events.WalletEventPayloadSerializer;
import org.example.wallet.health.WalletHealthCheck;
import org.example.wallet.metrics.MetricsPort;
import org.example.wallet.metrics.NoOpMetricsPort;
import org.example.wallet.outbox.OutboxProcessor;
import org.example.wallet.outbox.WalletEventHandler;
import org.example.wallet.service.WalletApplicationService;
import org.example.wallet.store.MirrorIdempotencyRepository;
import org.example.wallet.store.OutboxRepository;
import org.example.wallet.store.hibernate.HibernateModeNotYetSupported;
import org.example.wallet.store.IdempotencyRepository;
import org.example.wallet.store.TransactionRepository;
import org.example.wallet.store.WalletMutationExecutor;
import org.example.wallet.store.WalletRepository;
import org.example.wallet.store.inmemory.InMemoryIdempotencyRepository;
import org.example.wallet.store.inmemory.InMemoryMirrorIdempotencyRepository;
import org.example.wallet.store.inmemory.InMemoryOutboxRepository;
import org.example.wallet.store.inmemory.InMemoryTransactionRepository;
import org.example.wallet.store.inmemory.InMemoryWalletMutationExecutor;
import org.example.wallet.store.inmemory.InMemoryWalletRepository;

import java.util.List;

/**
 * Dropwizard bootstrap class for the wallet service.
 */
public class WalletServiceApplication extends Application<WalletServiceConfiguration> {
    public static void main(String[] args) throws Exception {
        new WalletServiceApplication().run(args);
    }

    @Override
    public String getName() {
        return "wallet-service";
    }

    @Override
    public void initialize(Bootstrap<WalletServiceConfiguration> bootstrap) {
        // Hibernate entities are modeled for future persistence, but the runnable mode for this
        // exercise intentionally stays in-memory to keep setup light.
    }

    @Override
    public void run(WalletServiceConfiguration configuration, Environment environment) {
        if (configuration.getRuntimeMode() == WalletServiceConfiguration.RuntimeMode.HIBERNATE) {
            throw new HibernateModeNotYetSupported(
                    "Runtime mode HIBERNATE is a placeholder in this submission. "
                            + "The service is intentionally shipped with in-memory storage and "
                            + "Hibernate-ready entities/repositories for extension.");
        }

        WalletRepository walletRepository = new InMemoryWalletRepository();
        TransactionRepository transactionRepository = new InMemoryTransactionRepository();
        IdempotencyRepository idempotencyRepository = new InMemoryIdempotencyRepository();
        OutboxRepository outboxRepository = new InMemoryOutboxRepository();
        MirrorIdempotencyRepository mirrorIdempotencyRepository = new InMemoryMirrorIdempotencyRepository();
        WalletMutationExecutor mutationExecutor = new InMemoryWalletMutationExecutor();
        MetricsPort metricsPort = new NoOpMetricsPort();

        String companyWalletId = CompanyWalletBootstrap.ensureCompanyWallet(walletRepository, configuration);

        ObjectMapper objectMapper = environment.getObjectMapper().copy();
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

        List<WalletEventHandler> eventHandlers =
                List.of(new CompanyMirrorLedgerHandler(companyMirrorService, payloadSerializer));

        OutboxProcessor outboxProcessor =
                new OutboxProcessor(
                        outboxRepository,
                        eventHandlers,
                        configuration.getOutbox().getPollIntervalMs(),
                        configuration.getOutbox().getBatchSize());

        WalletApplicationService service =
                new WalletApplicationService(
                        walletRepository,
                        transactionRepository,
                        idempotencyRepository,
                        mutationExecutor,
                        metricsPort,
                        eventPublisher);

        environment.lifecycle().manage(outboxProcessor);
        environment.getObjectMapper().disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        environment.healthChecks().register("wallet-service", new WalletHealthCheck());
        environment.jersey().register(new AuthFilter(configuration.getAuth()));
        environment.jersey().register(new WalletResource(service));
        environment.jersey().register(new DomainExceptionMapper());
        environment.jersey().register(new GenericExceptionMapper());
    }
}
