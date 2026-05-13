package org.example.wallet.service;

import org.example.wallet.domain.DeductionResult;
import org.example.wallet.domain.DeductionStatus;
import org.example.wallet.domain.Wallet;
import org.example.wallet.events.NoOpEventPublisher;
import org.example.wallet.metrics.NoOpMetricsPort;
import org.example.wallet.store.inmemory.InMemoryIdempotencyRepository;
import org.example.wallet.store.inmemory.InMemoryTransactionRepository;
import org.example.wallet.store.inmemory.InMemoryWalletMutationExecutor;
import org.example.wallet.store.inmemory.InMemoryWalletRepository;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.assertEquals;

class WalletConcurrencyTest {
    @Test
    void concurrentUniqueDeductRequestsShouldNeverOverdrawWallet() throws Exception {
        WalletApplicationService service = newService();
        Wallet wallet = service.createWallet("cust-1", 100);

        List<DeductionResult> results = executeConcurrent(service, wallet.getWalletId(), List.of(
                "order-1", "order-2", "order-3", "order-4", "order-5"));

        long successCount = results.stream().filter(result -> result.status() == DeductionStatus.SUCCESS).count();
        long rejectedCount = results.stream().filter(result -> result.status() == DeductionStatus.REJECTED).count();

        assertEquals(1, successCount);
        assertEquals(4, rejectedCount);
        assertEquals(0, service.getBalance(wallet.getWalletId()));
    }

    @Test
    void concurrentSameIdempotencyKeyShouldChargeOnlyOnce() throws Exception {
        WalletApplicationService service = newService();
        Wallet wallet = service.createWallet("cust-1", 500);

        List<DeductionResult> results = executeConcurrent(service, wallet.getWalletId(), List.of(
                "order-1", "order-1", "order-1", "order-1", "order-1"));

        long successCount = results.stream().filter(result -> result.status() == DeductionStatus.SUCCESS).count();
        long replayCount = results.stream().filter(DeductionResult::servedFromIdempotencyCache).count();

        assertEquals(5, successCount);
        assertEquals(4, replayCount);
        assertEquals(400, service.getBalance(wallet.getWalletId()));
        assertEquals(2, service.getTransactions(wallet.getWalletId()).size());
    }

    private WalletApplicationService newService() {
        return new WalletApplicationService(
                new InMemoryWalletRepository(),
                new InMemoryTransactionRepository(),
                new InMemoryIdempotencyRepository(),
                new InMemoryWalletMutationExecutor(),
                new NoOpMetricsPort(),
                new NoOpEventPublisher(),
                100L);
    }

    private List<DeductionResult> executeConcurrent(
            WalletApplicationService service,
            String walletId,
            List<String> idempotencyKeys) throws InterruptedException, ExecutionException {
        ExecutorService executorService = Executors.newFixedThreadPool(idempotencyKeys.size());
        try {
            List<Callable<DeductionResult>> tasks = new ArrayList<>();
            for (String idempotencyKey : idempotencyKeys) {
                tasks.add(() -> service.deduct(walletId, idempotencyKey, idempotencyKey));
            }

            List<Future<DeductionResult>> futures = executorService.invokeAll(tasks);
            List<DeductionResult> results = new ArrayList<>();
            for (Future<DeductionResult> future : futures) {
                results.add(future.get());
            }
            return results;
        } finally {
            executorService.shutdownNow();
        }
    }
}
