package org.example.wallet.api.response;

import com.fasterxml.jackson.annotation.JsonFormat;

import java.time.Instant;

public record ErrorResponse(
        String errorCode,
        String message,
        @JsonFormat(shape = JsonFormat.Shape.STRING)
        Instant timestamp) {
}
