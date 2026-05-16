package org.example.wallet.outbox;

import org.example.wallet.domain.OutboxEvent;
import org.example.wallet.domain.WalletEventType;

public interface WalletEventHandler {
    boolean supports(WalletEventType eventType);

    void handle(OutboxEvent event);
}
