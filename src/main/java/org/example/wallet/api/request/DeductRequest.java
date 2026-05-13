package org.example.wallet.api.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

public record DeductRequest(
        @NotBlank String idempotencyKey,
        @Min(1) long amount,
        String referenceId) {
}
