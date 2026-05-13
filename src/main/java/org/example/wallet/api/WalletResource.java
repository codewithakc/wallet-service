package org.example.wallet.api;

import jakarta.validation.Valid;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.example.wallet.api.request.CreateWalletRequest;
import org.example.wallet.api.request.DeductRequest;
import org.example.wallet.api.request.TopupRequest;
import org.example.wallet.api.response.BalanceResponse;
import org.example.wallet.api.response.DeductResponse;
import org.example.wallet.api.response.ErrorResponse;
import org.example.wallet.api.response.TopupResponse;
import org.example.wallet.api.response.TransactionResponse;
import org.example.wallet.api.response.WalletResponse;
import org.example.wallet.auth.AuthorizationSupport;
import org.example.wallet.auth.CallerContext;
import org.example.wallet.auth.CallerRole;
import org.example.wallet.domain.DeductionResult;
import org.example.wallet.domain.Wallet;
import org.example.wallet.error.ForbiddenException;
import org.example.wallet.service.WalletApplicationService;

import java.time.Instant;
import java.util.List;

/**
 * HTTP resource that exposes wallet operations and enforces caller-level authorization rules.
 *
 * <p>Customer identity is derived from the bearer token and used to enforce wallet ownership for
 * customer-facing reads and top-ups.
 */
@Path("/wallets")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class WalletResource {
    private final WalletApplicationService walletApplicationService;

    public WalletResource(WalletApplicationService walletApplicationService) {
        this.walletApplicationService = walletApplicationService;
    }

    /**
     * Creates a new wallet for the authenticated customer.
     */
    @POST
    public Response createWallet(@Valid CreateWalletRequest request, @Context ContainerRequestContext requestContext) {
        CallerContext callerContext = AuthorizationSupport.requireCustomerCaller(requestContext);
        return Response.status(Response.Status.CREATED)
                .entity(WalletResponse.from(
                        walletApplicationService.createWallet(callerContext.customerId(), request.initialBalance())))
                .build();
    }

    /**
     * Returns wallet metadata and current balance.
     */
    @GET
    @Path("/{walletId}")
    public WalletResponse getWallet(
            @PathParam("walletId") String walletId,
            @Context ContainerRequestContext requestContext) {
        AuthorizationSupport.requireRole(requestContext, CallerRole.CUSTOMER, CallerRole.ORDER_SERVICE);
        Wallet wallet = walletApplicationService.getWallet(walletId);
        enforceCustomerWalletOwnershipIfNeeded(requestContext, wallet);
        return WalletResponse.from(wallet);
    }

    /**
     * Adds funds to an existing wallet owned by the authenticated customer.
     */
    @POST
    @Path("/{walletId}/topup")
    public TopupResponse topup(
            @PathParam("walletId") String walletId,
            @Valid TopupRequest request,
            @Context ContainerRequestContext requestContext) {
        AuthorizationSupport.requireCustomerCaller(requestContext);
        Wallet wallet = walletApplicationService.getWallet(walletId);
        enforceCustomerWalletOwnershipIfNeeded(requestContext, wallet);
        return TopupResponse.from(walletApplicationService.topup(walletId, request.amount(), request.referenceId()));
    }

    /**
     * Deducts the fixed order charge for an order placement attempt.
     */
    @POST
    @Path("/{walletId}/deduct")
    public Response deduct(
            @PathParam("walletId") String walletId,
            @Valid DeductRequest request,
            @Context ContainerRequestContext requestContext) {
        AuthorizationSupport.requireRole(requestContext, CallerRole.ORDER_SERVICE);
        DeductionResult result =
                walletApplicationService.deduct(
                        walletId,
                        request.idempotencyKey(),
                        request.amount(),
                        request.referenceId());

        if (result.isSuccess()) {
            return Response.ok(DeductResponse.from(result)).build();
        }

        return Response.status(Response.Status.CONFLICT)
                .entity(new ErrorResponse(result.errorCode(), result.errorMessage(), Instant.now()))
                .build();
    }

    /**
     * Returns the wallet balance for either the owning customer or the order service.
     */
    @GET
    @Path("/{walletId}/balance")
    public BalanceResponse getBalance(
            @PathParam("walletId") String walletId,
            @Context ContainerRequestContext requestContext) {
        AuthorizationSupport.requireRole(requestContext, CallerRole.CUSTOMER, CallerRole.ORDER_SERVICE);
        Wallet wallet = walletApplicationService.getWallet(walletId);
        enforceCustomerWalletOwnershipIfNeeded(requestContext, wallet);
        return new BalanceResponse(walletId, wallet.getBalance());
    }

    /**
     * Returns the append-only ledger history for a wallet.
     */
    @GET
    @Path("/{walletId}/transactions")
    public List<TransactionResponse> getTransactions(
            @PathParam("walletId") String walletId,
            @Context ContainerRequestContext requestContext) {
        AuthorizationSupport.requireRole(requestContext, CallerRole.CUSTOMER, CallerRole.ORDER_SERVICE);
        Wallet wallet = walletApplicationService.getWallet(walletId);
        enforceCustomerWalletOwnershipIfNeeded(requestContext, wallet);
        return walletApplicationService.getTransactions(walletId).stream()
                .map(TransactionResponse::from)
                .toList();
    }

    /**
     * Rejects customer requests for wallets that belong to a different customer.
     */
    private void enforceCustomerWalletOwnershipIfNeeded(
            ContainerRequestContext requestContext,
            Wallet wallet) {
        CallerContext callerContext = AuthorizationSupport.getCaller(requestContext);
        if (callerContext.role() == CallerRole.ORDER_SERVICE) {
            return;
        }
        if (!wallet.getCustomerId().equals(callerContext.customerId())) {
            throw new ForbiddenException("Wallet does not belong to the authenticated customer.");
        }
    }
}
