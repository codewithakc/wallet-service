package org.example.wallet.api.request;

import jakarta.validation.constraints.Min;

public record TopupRequest(
        @Min(1) long amount,
        String referenceId) {
}
