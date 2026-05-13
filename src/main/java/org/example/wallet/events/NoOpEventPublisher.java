package org.example.wallet.events;

import org.example.wallet.domain.DeductionResult;
import org.example.wallet.domain.Wallet;
import org.example.wallet.domain.WalletTransaction;

public class NoOpEventPublisher implements EventPublisher {
    @Override
    public void publishWalletCreated(Wallet wallet) {
    }

    @Override
    public void publishWalletToppedUp(Wallet wallet, WalletTransaction transaction) {
    }

    @Override
    public void publishWalletDeducted(DeductionResult deductionResult) {
    }

    @Override
    public void publishWalletDeductionRejected(DeductionResult deductionResult) {
    }
}
