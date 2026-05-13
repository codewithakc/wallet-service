# Wallet Service

This repository contains a production-shaped implementation of the `Wallet Service` for a logistics platform.

The service is built with `Dropwizard` and structured so the runtime stays lightweight and locally runnable today, while the codebase can evolve toward a transactional `Hibernate + RDBMS` deployment later.

## What is implemented
- `POST /wallets` to create a wallet
- `GET /wallets/{walletId}` to read wallet metadata and current balance
- `POST /wallets/{walletId}/topup` to add funds
- `POST /wallets/{walletId}/deduct` to deduct an amount supplied by `Order Service`
- `GET /wallets/{walletId}/balance` to read the current balance
- `GET /wallets/{walletId}/transactions` to read the ledger
- idempotent `deduct` using a caller-provided `idempotencyKey`
- lightweight auth for customer and order-service callers
- no-op placeholders for metrics and Kafka/event publishing
- Hibernate entity skeletons for future database-backed persistence

## Design documents
- High-level design: [`docs/HLD.md`](docs/HLD.md)
- Low-level design: [`docs/LLD.md`](docs/LLD.md)

## Why this shape
The exercise asks for an in-memory implementation, but also for production-level thinking. This solution intentionally separates:
- business logic in `WalletApplicationService`
- storage behind repository interfaces
- in-memory concurrency control behind `WalletMutationExecutor`
- operational concerns behind auth, metrics, and event publisher abstractions

That makes the current service easy to run, while preserving a clean path to:
- Hibernate-backed repositories
- database transactions and optimistic locking
- Kafka via an outbox pattern
- richer metrics and tracing

## Core correctness decisions
### No negative balances
Every wallet mutation is serialized per wallet ID using a lock manager. The critical section covers:
- balance validation
- wallet update
- ledger append
- idempotency record write

This ensures one wallet cannot be overdrawn even under concurrent deduct requests.

### Idempotent deduct
`POST /wallets/{walletId}/deduct` requires an `idempotencyKey`.

The first call stores the outcome against `walletId + idempotencyKey`.
Repeated calls with the same key return the same logical outcome:
- same success result if the first call succeeded
- same rejection if the first call failed due to insufficient funds

### Ledger-first auditability
Every successful money movement creates a `WalletTransaction` entry. The current balance is stored on the wallet for fast reads, and the ledger provides traceability.

## Authentication model
This submission uses a deliberately lightweight auth model:
- `customer-token:<customerId>` for customer-facing operations
- `order-service-token` for the order service

Authorization matrix:
- customer token: create wallet, top-up, balance, transactions
- order-service token: deduct, balance, transactions

For customer-facing calls, the service derives `customerId` from the bearer token and enforces wallet ownership on customer reads and top-ups. This is still not a full IAM design, but it avoids trusting request parameters for customer identity.

## Project structure
```text
docs/                         design artifacts
examples/                     integration stub
src/main/java/org/example/wallet/
  api/                        HTTP resources and DTOs
  auth/                       auth filter and caller roles
  domain/                     wallet, ledger, idempotency models
  error/                      exception hierarchy and mappers
  events/                     event publisher placeholders
  health/                     health check
  metrics/                    metrics placeholders
  persistence/hibernate/      future database extension path
  service/                    core business logic
  store/                      repository abstractions and in-memory adapters
src/main/resources/env/dev/   development runtime configuration
```

## Tech stack
- Java 21 target
- Maven
- Dropwizard 5.0.1
- Hibernate entity model placeholders via `dropwizard-hibernate`
- JUnit 5 and Dropwizard testing

## How to start the service
### 1. Build the project
```bash
mvn clean package
```

### 2. Run the service
```bash
java -jar target/wallet-service-1.0-SNAPSHOT.jar server src/main/resources/env/dev/config.yaml
```

The application API starts on `http://localhost:8080`.
The admin port starts on `http://localhost:8081`.

### 3. Health check
```bash
curl http://localhost:8081/healthcheck
```

## How to run the tests
```bash
mvn test
```

The suite currently covers:
- service-level correctness
- idempotency behavior
- concurrency behavior for same-wallet deductions
- resource-level auth and HTTP contract checks

## Sample API usage
### Create a wallet
```bash
curl -X POST http://localhost:8080/wallets \
  -H "Authorization: Bearer customer-token:cust-101" \
  -H "Content-Type: application/json" \
  -d '{
    "initialBalance": 500
  }'
```

### Top up
```bash
curl -X POST http://localhost:8080/wallets/<wallet-id>/topup \
  -H "Authorization: Bearer customer-token:cust-101" \
  -H "Content-Type: application/json" \
  -d '{
    "amount": 300,
    "referenceId": "topup-001"
  }'
```

### Get wallet
```bash
curl http://localhost:8080/wallets/<wallet-id> \
  -H "Authorization: Bearer customer-token:cust-101"
```

### Deduct from Order Service
```bash
curl -X POST http://localhost:8080/wallets/<wallet-id>/deduct \
  -H "Authorization: Bearer order-service-token" \
  -H "Content-Type: application/json" \
  -d '{
    "idempotencyKey": "order-9001",
    "amount": 125,
    "referenceId": "order-9001"
  }'
```

### Read balance
```bash
curl http://localhost:8080/wallets/<wallet-id>/balance \
  -H "Authorization: Bearer customer-token:cust-101"
```

### Read transactions
```bash
curl http://localhost:8080/wallets/<wallet-id>/transactions \
  -H "Authorization: Bearer customer-token:cust-101"
```

## Full order service stub
A broader order-service simulator is available at [`examples/order-service-full-stub.sh`](examples/order-service-full-stub.sh).

It covers:
- setup wallet creation for demo scenarios
- successful order placement
- retry of the same order ID
- insufficient-funds rejection
- concurrent retries for the same order ID
- concurrent distinct order placement against limited balance
- wallet and ledger reads after the order flows

Usage:
```bash
./examples/order-service-full-stub.sh
```

This script behaves more like a mock `Order Service`: it places orders by calling `/wallets/{id}/deduct`, interprets the HTTP result, and shows which orders were accepted or rejected.

## Full flow stub
A broader end-to-end smoke script is available at [`examples/full-flow-stub.sh`](examples/full-flow-stub.sh).

It covers:
- wallet creation
- `GET /wallets/{id}`
- `GET /wallets/{id}/balance`
- top-up
- successful deduct
- retry of the same idempotency key
- transaction history read
- concurrent same-key deduct requests to demonstrate idempotent replay under concurrency

Usage:
```bash
./examples/full-flow-stub.sh
```

## Data model
### Runtime model
- `Wallet`
- `WalletTransaction`
- `IdempotencyRecord`

### Relational shape for future migration
- `wallets`
- `wallet_transactions`
- `deduction_idempotency`

Hibernate-ready entities already exist under `src/main/java/org/example/wallet/persistence/hibernate/entity`.

## Testing methodology
I focused tests on the failure modes that matter most for a wallet service:
- repeated deduct requests with the same idempotency key
- reuse of the same idempotency key with a different amount
- concurrent deduct requests against the same wallet
- insufficient balance handling
- auth and authorization errors

I intentionally did not spend time on large amounts of controller boilerplate testing because the main interview risk is correctness under retries and concurrency.

## Trade-offs
### What is intentionally simplified
- in-memory storage is not durable across restarts
- auth uses static tokens instead of full identity integration
- transaction history is not paginated
- Kafka is represented as a no-op abstraction
- Hibernate runtime mode is modeled, but not fully activated

### Why those choices are reasonable here
- the exercise prioritizes correctness and clarity over infrastructure setup
- the current design keeps the service easy to run locally
- the repository and mutation abstractions preserve a clean migration path

## What I would do next with more time
1. Activate the Hibernate runtime path with H2 or PostgreSQL and real transactional boundaries.
2. Add optimistic locking or `SELECT ... FOR UPDATE` depending on the database strategy.
3. Introduce a transactional outbox for wallet events before publishing to Kafka.
4. Add pagination and filtering for transaction history.
5. Replace static tokens with service identity and tenant-aware authorization.
6. Add structured metrics, tracing, and request correlation IDs.
