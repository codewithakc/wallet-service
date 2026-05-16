package org.example.wallet;

import io.dropwizard.core.Configuration;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * Root configuration for the Dropwizard wallet service.
 */
public class WalletServiceConfiguration extends Configuration {
    @Valid
    @NotNull
    private AuthConfiguration auth = new AuthConfiguration();

    @Valid
    @NotNull
    private CompanyWalletConfiguration companyWallet = new CompanyWalletConfiguration();

    @Valid
    @NotNull
    private OutboxConfiguration outbox = new OutboxConfiguration();

    @NotNull
    private RuntimeMode runtimeMode = RuntimeMode.INMEMORY;

    public AuthConfiguration getAuth() {
        return auth;
    }

    public void setAuth(AuthConfiguration auth) {
        this.auth = auth;
    }

    public CompanyWalletConfiguration getCompanyWallet() {
        return companyWallet;
    }

    public void setCompanyWallet(CompanyWalletConfiguration companyWallet) {
        this.companyWallet = companyWallet;
    }

    public OutboxConfiguration getOutbox() {
        return outbox;
    }

    public void setOutbox(OutboxConfiguration outbox) {
        this.outbox = outbox;
    }

    public RuntimeMode getRuntimeMode() {
        return runtimeMode;
    }

    public void setRuntimeMode(RuntimeMode runtimeMode) {
        this.runtimeMode = runtimeMode;
    }

    public enum RuntimeMode {
        INMEMORY,
        HIBERNATE
    }

    /**
     * Authentication configuration for customer and service callers.
     */
    public static class AuthConfiguration {
        @NotBlank
        private String customerTokenPrefix = "customer-token";

        @NotBlank
        private String orderServiceToken = "order-service-token";

        public String getCustomerTokenPrefix() {
            return customerTokenPrefix;
        }

        public void setCustomerTokenPrefix(String customerTokenPrefix) {
            this.customerTokenPrefix = customerTokenPrefix;
        }

        public String getOrderServiceToken() {
            return orderServiceToken;
        }

        public void setOrderServiceToken(String orderServiceToken) {
            this.orderServiceToken = orderServiceToken;
        }
    }

    public static class CompanyWalletConfiguration {
        @NotBlank
        private String customerId = "platform-company";

        @Min(0)
        private long initialBalance = 0;

        public String getCustomerId() {
            return customerId;
        }

        public void setCustomerId(String customerId) {
            this.customerId = customerId;
        }

        public long getInitialBalance() {
            return initialBalance;
        }

        public void setInitialBalance(long initialBalance) {
            this.initialBalance = initialBalance;
        }
    }

    public static class OutboxConfiguration {
        @Min(1)
        private long pollIntervalMs = 500;

        @Min(1)
        private int batchSize = 50;

        public long getPollIntervalMs() {
            return pollIntervalMs;
        }

        public void setPollIntervalMs(long pollIntervalMs) {
            this.pollIntervalMs = pollIntervalMs;
        }

        public int getBatchSize() {
            return batchSize;
        }

        public void setBatchSize(int batchSize) {
            this.batchSize = batchSize;
        }
    }
}
