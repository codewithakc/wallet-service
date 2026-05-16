package org.example.wallet.company;

import org.example.wallet.domain.OutboxEvent;
import org.example.wallet.domain.WalletEventType;
import org.example.wallet.events.CustomerMovementPayload;
import org.example.wallet.events.WalletEventPayloadSerializer;
import org.example.wallet.outbox.WalletEventHandler;

public class CompanyMirrorLedgerHandler implements WalletEventHandler {
    private final CompanyMirrorService companyMirrorService;
    private final WalletEventPayloadSerializer payloadSerializer;

    public CompanyMirrorLedgerHandler(
            CompanyMirrorService companyMirrorService,
            WalletEventPayloadSerializer payloadSerializer) {
        this.companyMirrorService = companyMirrorService;
        this.payloadSerializer = payloadSerializer;
    }

    @Override
    public boolean supports(WalletEventType eventType) {
        return eventType == WalletEventType.CUSTOMER_DEDUCTED || eventType == WalletEventType.CUSTOMER_TOPPED_UP;
    }

    @Override
    public void handle(OutboxEvent event) {
        CustomerMovementPayload payload = payloadSerializer.deserialize(event.payload());
        switch (event.eventType()) {
            case CUSTOMER_DEDUCTED -> companyMirrorService.mirrorCustomerDeduct(payload);
            case CUSTOMER_TOPPED_UP -> companyMirrorService.mirrorCustomerTopup(payload);
            default -> throw new IllegalStateException("Unsupported wallet event type: " + event.eventType());
        }
    }
}
