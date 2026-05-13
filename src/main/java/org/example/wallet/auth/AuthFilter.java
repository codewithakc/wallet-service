package org.example.wallet.auth;

import io.dropwizard.auth.Auth;
import jakarta.annotation.Priority;
import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.ext.Provider;
import org.example.wallet.WalletServiceConfiguration;
import org.example.wallet.error.UnauthorizedException;

@Provider
@Auth
@Priority(Priorities.AUTHENTICATION)
public class AuthFilter implements ContainerRequestFilter {
    public static final String CALLER_CONTEXT_PROPERTY = "wallet.caller-context";

    private static final String BEARER_PREFIX = "Bearer ";

    private final String customerToken;
    private final String orderServiceToken;

    public AuthFilter(WalletServiceConfiguration.AuthConfiguration configuration) {
        this.customerToken = configuration.getCustomerToken();
        this.orderServiceToken = configuration.getOrderServiceToken();
    }

    @Override
    public void filter(ContainerRequestContext requestContext) {
        String authorizationHeader = requestContext.getHeaderString("Authorization");
        if (authorizationHeader == null || !authorizationHeader.startsWith(BEARER_PREFIX)) {
            throw new UnauthorizedException("Missing or invalid Authorization header.");
        }

        String token = authorizationHeader.substring(BEARER_PREFIX.length()).trim();
        CallerContext callerContext = authenticate(token);
        requestContext.setProperty(CALLER_CONTEXT_PROPERTY, callerContext);
    }

    private CallerContext authenticate(String token) {
        if (customerToken.equals(token)) {
            return new CallerContext("customer-client", CallerRole.CUSTOMER);
        }
        if (orderServiceToken.equals(token)) {
            return new CallerContext("order-service", CallerRole.ORDER_SERVICE);
        }
        throw new UnauthorizedException("Bearer token is not recognized.");
    }
}
