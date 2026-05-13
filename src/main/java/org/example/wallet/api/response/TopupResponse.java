package org.example.wallet.api.response;

import org.example.wallet.domain.TopupResult;

public record TopupResponse(
        String walletId,
        long balance,
        String transactionId,
        String status) {
    public static TopupResponse from(TopupResult result) {
        return new TopupResponse(
                result.walletId(),
                result.balanceAfter(),
                result.transactionId(),
                "SUCCESS");
    }
}
