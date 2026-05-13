package org.example.wallet.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.dropwizard.testing.junit5.ResourceExtension;
import io.dropwizard.testing.junit5.DropwizardExtensionsSupport;
import jakarta.ws.rs.client.Entity;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.example.wallet.WalletServiceConfiguration;
import org.example.wallet.api.request.CreateWalletRequest;
import org.example.wallet.api.request.DeductRequest;
import org.example.wallet.api.response.BalanceResponse;
import org.example.wallet.api.response.DeductResponse;
import org.example.wallet.api.response.ErrorResponse;
import org.example.wallet.api.response.WalletResponse;
import org.example.wallet.auth.AuthFilter;
import org.example.wallet.error.DomainExceptionMapper;
import org.example.wallet.error.GenericExceptionMapper;
import org.example.wallet.events.NoOpEventPublisher;
import org.example.wallet.metrics.NoOpMetricsPort;
import org.example.wallet.service.WalletApplicationService;
import org.example.wallet.store.inmemory.InMemoryIdempotencyRepository;
import org.example.wallet.store.inmemory.InMemoryTransactionRepository;
import org.example.wallet.store.inmemory.InMemoryWalletMutationExecutor;
import org.example.wallet.store.inmemory.InMemoryWalletRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ExtendWith(DropwizardExtensionsSupport.class)
class WalletResourceTest {
    private static final String CUSTOMER_AUTH = "Bearer customer-token:cust-api";
    private static final String SECOND_CUSTOMER_AUTH = "Bearer customer-token:other-customer";
    private static final String ORDER_AUTH = "Bearer order-service-token";

    static final ResourceExtension RESOURCES = ResourceExtension.builder()
            .addProvider(new AuthFilter(new WalletServiceConfiguration.AuthConfiguration()))
            .addProvider(new DomainExceptionMapper())
            .addProvider(new GenericExceptionMapper())
            .addResource(new WalletResource(new WalletApplicationService(
                    new InMemoryWalletRepository(),
                    new InMemoryTransactionRepository(),
                    new InMemoryIdempotencyRepository(),
                    new InMemoryWalletMutationExecutor(),
                    new NoOpMetricsPort(),
                    new NoOpEventPublisher(),
                    100L)))
            .build();

    @Test
    void createWalletShouldReturnCreated() {
        Response response = RESOURCES.target("/wallets")
                .request()
                .header(HttpHeaders.AUTHORIZATION, CUSTOMER_AUTH)
                .post(Entity.entity(new CreateWalletRequest(200), MediaType.APPLICATION_JSON_TYPE));

        assertEquals(201, response.getStatus());
        WalletResponse body = response.readEntity(WalletResponse.class);
        assertEquals("cust-api", body.customerId());
        assertEquals(200, body.balance());
    }

    @Test
    void createWalletShouldSerializeCreatedAtAsIsoString() throws Exception {
        Response response = RESOURCES.target("/wallets")
                .request()
                .header(HttpHeaders.AUTHORIZATION, CUSTOMER_AUTH)
                .post(Entity.entity(new CreateWalletRequest(200), MediaType.APPLICATION_JSON_TYPE));

        String rawBody = response.readEntity(String.class);
        JsonNode jsonNode = new ObjectMapper().readTree(rawBody);

        assertTrue(jsonNode.get("createdAt").isTextual());
        assertTrue(jsonNode.get("createdAt").asText().contains("T"));
    }

    @Test
    void deductShouldRejectCustomerCaller() {
        WalletResponse wallet = createWallet(300);

        Response response = RESOURCES.target("/wallets/" + wallet.walletId() + "/deduct")
                .request()
                .header(HttpHeaders.AUTHORIZATION, CUSTOMER_AUTH)
                .post(Entity.entity(new DeductRequest("order-api-1", "order-api-1"), MediaType.APPLICATION_JSON_TYPE));

        assertEquals(403, response.getStatus());
        ErrorResponse error = response.readEntity(ErrorResponse.class);
        assertEquals("FORBIDDEN", error.errorCode());
    }

    @Test
    void getWalletShouldReturnWalletDetails() {
        WalletResponse createdWallet = createWallet(250);

        WalletResponse fetchedWallet = RESOURCES.target("/wallets/" + createdWallet.walletId())
                .request()
                .header(HttpHeaders.AUTHORIZATION, CUSTOMER_AUTH)
                .get(WalletResponse.class);

        assertEquals(createdWallet.walletId(), fetchedWallet.walletId());
        assertEquals("cust-api", fetchedWallet.customerId());
        assertEquals(250, fetchedWallet.balance());
    }

    @Test
    void deductShouldSucceedForOrderService() {
        WalletResponse wallet = createWallet(300);

        Response response = RESOURCES.target("/wallets/" + wallet.walletId() + "/deduct")
                .request()
                .header(HttpHeaders.AUTHORIZATION, ORDER_AUTH)
                .post(Entity.entity(new DeductRequest("order-api-2", "order-api-2"), MediaType.APPLICATION_JSON_TYPE));

        assertEquals(200, response.getStatus());
        DeductResponse body = response.readEntity(DeductResponse.class);
        assertEquals("SUCCESS", body.status());
        assertEquals(200, body.balance());

        BalanceResponse balanceResponse = RESOURCES.target("/wallets/" + wallet.walletId() + "/balance")
                .request()
                .header(HttpHeaders.AUTHORIZATION, ORDER_AUTH)
                .get(BalanceResponse.class);
        assertEquals(200, balanceResponse.balance());
    }

    @Test
    void requestWithoutAuthorizationShouldReturnUnauthorized() {
        Response response = RESOURCES.target("/wallets")
                .request()
                .post(Entity.entity(new CreateWalletRequest(100), MediaType.APPLICATION_JSON_TYPE));

        assertEquals(401, response.getStatus());
        ErrorResponse error = response.readEntity(ErrorResponse.class);
        assertEquals("UNAUTHORIZED", error.errorCode());
    }

    @Test
    void getWalletShouldRejectDifferentCustomer() {
        WalletResponse createdWallet = createWallet(250);

        Response response = RESOURCES.target("/wallets/" + createdWallet.walletId())
                .request()
                .header(HttpHeaders.AUTHORIZATION, SECOND_CUSTOMER_AUTH)
                .get();

        assertEquals(403, response.getStatus());
        ErrorResponse error = response.readEntity(ErrorResponse.class);
        assertEquals("FORBIDDEN", error.errorCode());
    }

    private WalletResponse createWallet(long initialBalance) {
        Response response = RESOURCES.target("/wallets")
                .request()
                .header(HttpHeaders.AUTHORIZATION, CUSTOMER_AUTH)
                .post(Entity.entity(new CreateWalletRequest(initialBalance), MediaType.APPLICATION_JSON_TYPE));
        return response.readEntity(WalletResponse.class);
    }
}
