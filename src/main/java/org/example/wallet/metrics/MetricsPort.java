package org.example.wallet.metrics;

import java.time.Duration;

public interface MetricsPort {
    void recordCreateWallet();

    void recordTopupSuccess();

    void recordDeductSuccess();

    void recordDeductRejected();

    void recordIdempotentReplay();

    void recordLatency(String operation, Duration duration);
}
