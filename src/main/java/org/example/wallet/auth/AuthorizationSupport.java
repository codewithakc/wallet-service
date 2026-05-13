package org.example.wallet.auth;

import jakarta.ws.rs.container.ContainerRequestContext;
import org.example.wallet.error.ForbiddenException;
import org.example.wallet.error.UnauthorizedException;

import java.util.Arrays;

/**
 * Centralizes request-time authorization helpers so resource methods stay concise and consistent.
 */
public final class AuthorizationSupport {
    private AuthorizationSupport() {
    }

    /**
     * Returns the caller identity established by {@link AuthFilter}.
     */
    public static CallerContext getCaller(ContainerRequestContext requestContext) {
        Object property = requestContext.getProperty(AuthFilter.CALLER_CONTEXT_PROPERTY);
        if (property instanceof CallerContext callerContext) {
            return callerContext;
        }
        throw new UnauthorizedException("Caller context was not established.");
    }

    /**
     * Ensures that the authenticated caller has one of the allowed roles.
     */
    public static void requireRole(ContainerRequestContext requestContext, CallerRole... allowedRoles) {
        CallerContext callerContext = getCaller(requestContext);
        boolean allowed = Arrays.stream(allowedRoles).anyMatch(role -> role == callerContext.role());
        if (!allowed) {
            throw new ForbiddenException("Caller is not allowed to access this endpoint.");
        }
    }

    /**
     * Ensures that the caller is a customer and that the token carries customer identity.
     */
    public static CallerContext requireCustomerCaller(ContainerRequestContext requestContext) {
        requireRole(requestContext, CallerRole.CUSTOMER);
        CallerContext callerContext = getCaller(requestContext);
        if (callerContext.customerId() == null || callerContext.customerId().isBlank()) {
            throw new UnauthorizedException("Customer identity was not present in the token.");
        }
        return callerContext;
    }
}
