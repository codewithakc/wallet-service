package org.example.wallet.service;

import org.example.wallet.domain.DeductionResult;
import org.example.wallet.domain.DeductionStatus;
import org.example.wallet.domain.TopupResult;
import org.example.wallet.domain.Wallet;
import org.example.wallet.error.WalletNotFoundException;
import org.example.wallet.events.NoOpEventPublisher;
import org.example.wallet.metrics.NoOpMetricsPort;
import org.example.wallet.store.inmemory.InMemoryIdempotencyRepository;
import org.example.wallet.store.inmemory.InMemoryTransactionRepository;
import org.example.wallet.store.inmemory.InMemoryWalletMutationExecutor;
import org.example.wallet.store.inmemory.InMemoryWalletRepository;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WalletApplicationServiceTest {
    private final WalletApplicationService service = new WalletApplicationService(
            new InMemoryWalletRepository(),
            new InMemoryTransactionRepository(),
            new InMemoryIdempotencyRepository(),
            new InMemoryWalletMutationExecutor(),
            new NoOpMetricsPort(),
            new NoOpEventPublisher(),
            100L);

    @Test
    void createWalletAndTopupShouldUpdateBalance() {
        Wallet wallet = service.createWallet("cust-1", 200);

        TopupResult topupResult = service.topup(wallet.getWalletId(), 300, "topup-1");

        assertEquals(wallet.getWalletId(), topupResult.walletId());
        assertEquals(500, topupResult.balanceAfter());
        assertEquals(2, service.getTransactions(wallet.getWalletId()).size());
    }

    @Test
    void deductShouldSucceedOnceAndReplayAfterThat() {
        Wallet wallet = service.createWallet("cust-1", 300);

        DeductionResult first = service.deduct(wallet.getWalletId(), "order-1", "order-1");
        DeductionResult replay = service.deduct(wallet.getWalletId(), "order-1", "order-1");

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
        Wallet wallet = service.createWallet("cust-1", 50);

        DeductionResult result = service.deduct(wallet.getWalletId(), "order-2", "order-2");

        assertEquals(DeductionStatus.REJECTED, result.status());
        assertEquals("INSUFFICIENT_BALANCE", result.errorCode());
        assertEquals(50, service.getBalance(wallet.getWalletId()));
        assertEquals(1, service.getTransactions(wallet.getWalletId()).size());
    }

    @Test
    void getBalanceShouldFailForUnknownWallet() {
        assertThrows(WalletNotFoundException.class, () -> service.getBalance("missing-wallet"));
    }
}
