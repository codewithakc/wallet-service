package org.example.wallet;

import io.dropwizard.core.Configuration;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

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

    public static class AuthConfiguration {
        @NotBlank
        private String customerToken = "customer-token";

        @NotBlank
        private String orderServiceToken = "order-service-token";

        public String getCustomerToken() {
            return customerToken;
        }

        public void setCustomerToken(String customerToken) {
            this.customerToken = customerToken;
        }

        public String getOrderServiceToken() {
            return orderServiceToken;
        }

        public void setOrderServiceToken(String orderServiceToken) {
            this.orderServiceToken = orderServiceToken;
        }
    }
}
