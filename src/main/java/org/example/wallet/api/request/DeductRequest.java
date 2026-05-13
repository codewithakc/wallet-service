package org.example.wallet.api.request;

import jakarta.validation.constraints.NotBlank;

public record DeductRequest(
        @NotBlank String idempotencyKey,
        String referenceId) {
}
