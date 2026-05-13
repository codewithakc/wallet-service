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

    @NotNull
    private RuntimeMode runtimeMode = RuntimeMode.INMEMORY;

    @Min(1)
    private long deductionAmount = 100L;

    public AuthConfiguration getAuth() {
        return auth;
    }

    public void setAuth(AuthConfiguration auth) {
        this.auth = auth;
    }

    public RuntimeMode getRuntimeMode() {
        return runtimeMode;
    }

    public void setRuntimeMode(RuntimeMode runtimeMode) {
        this.runtimeMode = runtimeMode;
    }

    public long getDeductionAmount() {
        return deductionAmount;
    }

    public void setDeductionAmount(long deductionAmount) {
        this.deductionAmount = deductionAmount;
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
}
