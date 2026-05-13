package org.example.wallet.auth;

import jakarta.annotation.Priority;
import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.ext.Provider;
import org.example.wallet.WalletServiceConfiguration;
import org.example.wallet.error.UnauthorizedException;

/**
 * Authenticates bearer tokens and derives caller identity for downstream authorization.
 *
 * <p>Customer tokens are expected in the form {@code customer-token:&lt;customerId&gt;}, while the
 * order service uses a single configured service token.
 */
@Provider
@Priority(Priorities.AUTHENTICATION)
public class AuthFilter implements ContainerRequestFilter {
    public static final String CALLER_CONTEXT_PROPERTY = "wallet.caller-context";

    private static final String BEARER_PREFIX = "Bearer ";
    private static final String CUSTOMER_ID_DELIMITER = ":";

    private final String customerTokenPrefix;
    private final String orderServiceToken;

    public AuthFilter(WalletServiceConfiguration.AuthConfiguration configuration) {
        this.customerTokenPrefix = configuration.getCustomerTokenPrefix();
        this.orderServiceToken = configuration.getOrderServiceToken();
    }

    /**
     * Validates the incoming bearer token and stores the resolved {@link CallerContext} on the
     * request for later authorization checks.
     */
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
        String expectedCustomerPrefix = customerTokenPrefix + CUSTOMER_ID_DELIMITER;
        if (token.startsWith(expectedCustomerPrefix)) {
            String customerId = token.substring(expectedCustomerPrefix.length()).trim();
            if (customerId.isBlank()) {
                throw new UnauthorizedException("Customer token does not contain a customer ID.");
            }
            return new CallerContext(customerId, CallerRole.CUSTOMER, customerId);
        }
        if (orderServiceToken.equals(token)) {
            return new CallerContext("order-service", CallerRole.ORDER_SERVICE, null);
        }
        throw new UnauthorizedException("Bearer token is not recognized.");
    }
}
