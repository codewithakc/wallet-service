package org.example.wallet.auth;

/**
 * Authenticated caller identity derived from the bearer token.
 *
 * @param principalId stable principal name for the caller
 * @param role caller role used for authorization
 * @param customerId customer identity for customer-facing tokens, or {@code null} for service callers
 */
public record CallerContext(String principalId, CallerRole role, String customerId) {
}
