package org.example.wallet.outbox;

import org.example.wallet.domain.Wallet;
import org.example.wallet.support.WalletServiceTestSupport;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OutboxProcessorTest {
    @Test
    void processOnceShouldPublishPendingEventsAndMirrorOnce() {
        WalletServiceTestSupport.WiredComponents components = WalletServiceTestSupport.wiredComponents();
        Wallet customerWallet = components.walletApplicationService().createWallet("cust-outbox-1", 400);

        components.walletApplicationService().deduct(customerWallet.getWalletId(), "order-o-1", 100, "order-o-1");
        assertEquals(1, components.outboxRepository().findPending(10).size());

        components.outboxProcessor().processOnce();
        assertTrue(components.outboxRepository().findPending(10).isEmpty());
        assertEquals(100, components.walletRepository().findById(components.companyWalletId()).orElseThrow().getBalance());

        components.outboxProcessor().processOnce();
        assertEquals(100, components.walletRepository().findById(components.companyWalletId()).orElseThrow().getBalance());
        assertEquals(1, components.transactionRepository().findByWalletId(components.companyWalletId()).size());
    }

    @Test
    void idempotentCustomerDeductReplayShouldNotCreateDuplicateOutboxOrMirror() {
        WalletServiceTestSupport.WiredComponents components = WalletServiceTestSupport.wiredComponents();
        Wallet customerWallet = components.walletApplicationService().createWallet("cust-outbox-2", 300);

        components.walletApplicationService().deduct(customerWallet.getWalletId(), "order-o-2", 100, "order-o-2");
        components.walletApplicationService().deduct(customerWallet.getWalletId(), "order-o-2", 100, "order-o-2");
        assertEquals(1, components.outboxRepository().findPending(10).size());

        components.outboxProcessor().processOnce();
        assertEquals(100, components.walletRepository().findById(components.companyWalletId()).orElseThrow().getBalance());
        assertEquals(0, components.outboxRepository().findPending(10).size());
    }
}