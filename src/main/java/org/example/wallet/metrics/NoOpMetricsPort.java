package org.example.wallet.metrics;

import java.time.Duration;

public class NoOpMetricsPort implements MetricsPort {
    @Override
    public void recordCreateWallet() {
    }

    @Override
    public void recordTopupSuccess() {
    }

    @Override
    public void recordDeductSuccess() {
    }

    @Override
    public void recordDeductRejected() {
    }

    @Override
    public void recordIdempotentReplay() {
    }

    @Override
    public void recordLatency(String operation, Duration duration) {
    }
}
