package org.example.wallet.service;

import org.example.wallet.domain.DeductionStatus;
import org.example.wallet.domain.MoneyMovementType;
import org.example.wallet.domain.Wallet;
import org.example.wallet.support.WalletServiceTestSupport;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
class CompanyLedgerIntegrationTest {
    @Test
    void successfulDeductShouldEventuallyMirrorCreditToCompanyWallet() {
        WalletServiceTestSupport.WiredComponents components = WalletServiceTestSupport.wiredComponents();
        Wallet customerWallet = components.walletApplicationService().createWallet("cust-int-1", 500);

        var result = components.walletApplicationService().deduct(customerWallet.getWalletId(), "order-int-1", 100, "order-int-1");
        assertEquals(DeductionStatus.SUCCESS, result.status());
        assertEquals(400, components.walletApplicationService().getBalance(customerWallet.getWalletId()));

        components.outboxProcessor().processOnce();

        assertEquals(100, components.walletApplicationService().getBalance(components.companyWalletId()));
        assertEquals(
                MoneyMovementType.TOPUP,
                components.transactionRepository().findByWalletId(components.companyWalletId()).getFirst().type());
    }

    @Test
    void rejectedDeductShouldNotEnqueueCompanyMirror() {
        WalletServiceTestSupport.WiredComponents components = WalletServiceTestSupport.wiredComponents();
        Wallet customerWallet = components.walletApplicationService().createWallet("cust-int-2", 50);

        var result = components.walletApplicationService().deduct(customerWallet.getWalletId(), "order-int-2", 100, "order-int-2");
        assertEquals(DeductionStatus.REJECTED, result.status());
        assertEquals(0, components.outboxRepository().findPending(10).size());
        assertEquals(0, components.walletApplicationService().getBalance(components.companyWalletId()));

        components.outboxProcessor().processOnce();
        assertEquals(0, components.walletApplicationService().getBalance(components.companyWalletId()));
    }

    @Test
    void topupShouldMirrorDebitOnCompanyWallet() {
        WalletServiceTestSupport.WiredComponents components = WalletServiceTestSupport.wiredComponents();
        Wallet customerWallet = components.walletApplicationService().createWallet("cust-int-3", 0);

        components.walletApplicationService().topup(customerWallet.getWalletId(), 250, "topup-int-1");
        assertEquals(1, components.outboxRepository().findPending(10).size());

        components.outboxProcessor().processOnce();
        assertEquals(-250, components.walletApplicationService().getBalance(components.companyWalletId()));
        assertEquals(0, components.outboxRepository().findPending(10).size());
    }
}
