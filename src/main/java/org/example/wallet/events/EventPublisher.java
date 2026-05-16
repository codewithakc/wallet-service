package org.example.wallet.events;

import org.example.wallet.domain.DeductionResult;
import org.example.wallet.domain.Wallet;
import org.example.wallet.domain.WalletTransaction;

public interface EventPublisher {
    void publishWalletCreated(Wallet wallet);

    void publishWalletToppedUp(Wallet wallet, WalletTransaction transaction);

    void publishWalletDeducted(DeductionResult deductionResult, WalletTransaction transaction);

    void publishWalletDeductionRejected(DeductionResult deductionResult);
}
