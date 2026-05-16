package org.example.wallet.company;

import org.example.wallet.domain.MoneyMovementType;
import org.example.wallet.domain.Wallet;
import org.example.wallet.support.WalletServiceTestSupport;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CompanyMirrorServiceTest {
    @Test
    void mirrorCustomerDeductShouldCreditCompanyWallet() {
        WalletServiceTestSupport.WiredComponents components = WalletServiceTestSupport.wiredComponents();
        Wallet customerWallet = components.walletApplicationService().createWallet("cust-mirror-1", 500);

        components.walletApplicationService().deduct(customerWallet.getWalletId(), "order-m-1", 100, "order-m-1");
        components.outboxProcessor().processOnce();

        assertEquals(100, components.walletRepository().findById(components.companyWalletId()).orElseThrow().getBalance());
        assertEquals(
                1,
                components.transactionRepository().findByWalletId(components.companyWalletId()).size());
        assertEquals(
                MoneyMovementType.TOPUP,
                components.transactionRepository().findByWalletId(components.companyWalletId()).getFirst().type());
    }

    @Test
    void mirrorCustomerTopupShouldDebitCompanyWallet() {
        WalletServiceTestSupport.WiredComponents components = WalletServiceTestSupport.wiredComponents();
        Wallet customerWallet = components.walletApplicationService().createWallet("cust-mirror-2", 0);
        components.walletApplicationService().topup(customerWallet.getWalletId(), 300, "topup-m-1");
        components.outboxProcessor().processOnce();

        assertEquals(-300, components.walletRepository().findById(components.companyWalletId()).orElseThrow().getBalance());
        assertEquals(
                MoneyMovementType.DEDUCT,
                components.transactionRepository().findByWalletId(components.companyWalletId()).getFirst().type());
    }
}
