package org.example.wallet.events;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

public final class WalletEventPayloadSerializer {
    private final ObjectMapper objectMapper;

    public WalletEventPayloadSerializer(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public String serialize(CustomerMovementPayload payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to serialize wallet event payload.", exception);
        }
    }

    public CustomerMovementPayload deserialize(String payload) {
        try {
            return objectMapper.readValue(payload, CustomerMovementPayload.class);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to deserialize wallet event payload.", exception);
        }
    }
}
