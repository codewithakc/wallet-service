package org.example.wallet.api.request;

import jakarta.validation.constraints.Min;

public record CreateWalletRequest(
        @Min(0) long initialBalance) {
}
