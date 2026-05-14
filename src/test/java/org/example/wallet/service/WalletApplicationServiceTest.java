package org.example.wallet.service;

import org.example.wallet.domain.DeductionResult;
import org.example.wallet.domain.DeductionStatus;
import org.example.wallet.domain.TopupResult;
import org.example.wallet.domain.Wallet;
import org.example.wallet.error.IdempotencyConflictException;
import org.example.wallet.error.WalletNotFoundException;
import org.example.wallet.events.NoOpEventPublisher;
import org.example.wallet.metrics.NoOpMetricsPort;
import org.example.wallet.store.inmemory.InMemoryIdempotencyRepository;
import org.example.wallet.store.inmemory.InMemoryTransactionRepository;
import org.example.wallet.store.inmemory.InMemoryWalletMutationExecutor;
import org.example.wallet.store.inmemory.InMemoryWalletRepository;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WalletApplicationServiceTest {
    private static final Logger LOGGER = LoggerFactory.getLogger(WalletApplicationServiceTest.class);

    private final WalletApplicationService service = new WalletApplicationService(
            new InMemoryWalletRepository(),
            new InMemoryTransactionRepository(),
            new InMemoryIdempotencyRepository(),
            new InMemoryWalletMutationExecutor(),
            new NoOpMetricsPort(),
            new NoOpEventPublisher());

    @Test
    void createWalletAndTopupShouldUpdateBalance() {
        LOGGER.info("Running top-up flow test.");
        Wallet wallet = service.createWallet("cust-1", 200);

        TopupResult topupResult = service.topup(wallet.getWalletId(), 300, "topup-1");
        LOGGER.info("Top-up completed for wallet {} with final balance {}.", wallet.getWalletId(), topupResult.balanceAfter());

        assertEquals(wallet.getWalletId(), topupResult.walletId());
        assertEquals(500, topupResult.balanceAfter());
        assertEquals(2, service.getTransactions(wallet.getWalletId()).size());
    }

    @Test
    void deductShouldSucceedOnceAndReplayAfterThat() {
        LOGGER.info("Running idempotent deduct success and replay test.");
        Wallet wallet = service.createWallet("cust-1", 300);

        DeductionResult first = service.deduct(wallet.getWalletId(), "order-1", 100, "order-1");
        DeductionResult replay = service.deduct(wallet.getWalletId(), "order-1", 100, "order-1");
        LOGGER.info(
                "Deduct results for wallet {}: first status={}, replay servedFromCache={}.",
                wallet.getWalletId(),
                first.status(),
                replay.servedFromIdempotencyCache());

        assertEquals(DeductionStatus.SUCCESS, first.status());
        assertFalse(first.servedFromIdempotencyCache());
        assertEquals(200, first.balanceAfter());

        assertEquals(DeductionStatus.SUCCESS, replay.status());
        assertTrue(replay.servedFromIdempotencyCache());
        assertEquals(first.transactionId(), replay.transactionId());
        assertEquals(200, service.getBalance(wallet.getWalletId()));
        assertEquals(2, service.getTransactions(wallet.getWalletId()).size());
    }

    @Test
    void deductShouldRejectWhenBalanceIsInsufficient() {
        LOGGER.info("Running insufficient balance test.");
        Wallet wallet = service.createWallet("cust-1", 50);

        DeductionResult result = service.deduct(wallet.getWalletId(), "order-2", 100, "order-2");
        LOGGER.info("Deduct rejected for wallet {} with error {}.", wallet.getWalletId(), result.errorCode());

        assertEquals(DeductionStatus.REJECTED, result.status());
        assertEquals("INSUFFICIENT_BALANCE", result.errorCode());
        assertEquals(50, service.getBalance(wallet.getWalletId()));
        assertEquals(1, service.getTransactions(wallet.getWalletId()).size());
    }

    @Test
    void getBalanceShouldFailForUnknownWallet() {
        LOGGER.info("Running missing wallet balance lookup test.");
        assertThrows(WalletNotFoundException.class, () -> service.getBalance("missing-wallet"));
    }

    @Test
    void deductShouldRejectIdempotencyKeyReuseWithDifferentAmount() {
        LOGGER.info("Running idempotency conflict test.");
        Wallet wallet = service.createWallet("cust-1", 300);

        service.deduct(wallet.getWalletId(), "order-3", 100, "order-3");

        assertThrows(
                IdempotencyConflictException.class,
                () -> service.deduct(wallet.getWalletId(), "order-3", 125, "order-3-retry"));
    }
}
