package org.example.wallet.api.response;

import com.fasterxml.jackson.annotation.JsonFormat;
import org.example.wallet.domain.Wallet;

import java.time.Instant;

public record WalletResponse(
        String walletId,
        String customerId,
        long balance,
        @JsonFormat(shape = JsonFormat.Shape.STRING)
        Instant createdAt) {
    public static WalletResponse from(Wallet wallet) {
        return new WalletResponse(
                wallet.getWalletId(),
                wallet.getCustomerId(),
                wallet.getBalance(),
                wallet.getCreatedAt());
    }
}
