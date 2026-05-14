# Wallet Service

This repository contains a production-shaped `Wallet Service` for a logistics platform.

It is built with `Dropwizard`. It runs in memory today, but the code is organized so it can move to `Hibernate + RDBMS` later.

## Tech stack
- Java 21
- Maven
- Dropwizard 5.0.1
- Jackson for JSON serialization
- Hibernate model placeholders via `dropwizard-hibernate`
- JUnit 5 and Dropwizard testing
- In-memory runtime adapters for local execution

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

## Deliverables coverage
- Working implementation:
  HTTP API under `src/main/java/org/example/wallet/` with create, top-up, deduct, balance, wallet, and transaction endpoints.
- DB schema / data model:
  Runtime model and future relational model are described under `Data model`, with Hibernate entities under `src/main/java/org/example/wallet/persistence/hibernate/entity/`.
- Tests:
  Test commands and test approach are documented in this README, with automated tests under `src/test/java/org/example/wallet/`.
- README:
  This file explains setup, design choices, trade-offs, and next steps.
- Order Service stub:
  A working order integration stub is available at `examples/order-service-full-stub.sh`.

## Why this shape
The service runs in memory, but the code is split so moving to a database later is straightforward:
- business logic in `WalletApplicationService`
- storage behind repository interfaces
- in-memory concurrency control behind `WalletMutationExecutor`
- operational concerns behind auth, metrics, and event publisher abstractions

This keeps the current service easy to run and leaves a clean path to:
- Hibernate-backed repositories
- database transactions and optimistic locking
- Kafka via an outbox pattern
- richer metrics and tracing

## Core correctness decisions
### No negative balances
Each wallet update is serialized per wallet ID using a lock. The lock covers:
- balance validation
- wallet update
- ledger append
- idempotency record write

This prevents one wallet from being overdrawn, even under concurrent deduct requests.

### Idempotent deduct
`POST /wallets/{walletId}/deduct` requires an `idempotencyKey`.

The first call stores the result for `walletId + idempotencyKey`.
Later calls with the same key return the same logical outcome:
- same success result if the first call succeeded
- same rejection if the first call failed due to insufficient funds

### Ledger-first auditability
Every successful money movement creates a `WalletTransaction` entry. The wallet stores the current balance for fast reads, and the ledger keeps the history.

## Authentication model
The service uses a simple auth model:
- `customer-token:<customerId>` for customer-facing operations
- `order-service-token` for the order service

Authorization matrix:
- customer token: create wallet, top-up, balance, transactions
- order-service token: deduct, balance, transactions

For customer-facing calls, the service reads `customerId` from the bearer token and checks wallet ownership on reads and top-ups. This is not a full IAM design, but it avoids trusting request parameters for customer identity.

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
  persistence/hibernate/      Hibernate entities and ORM mapping artifacts
  service/                    core business logic
  store/                      repository abstractions and adapters
src/main/resources/env/dev/   development runtime configuration
```

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

The tests cover:
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
A simple order-service simulator is available at [`examples/order-service-full-stub.sh`](examples/order-service-full-stub.sh).

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
An end-to-end smoke script is available at [`examples/full-flow-stub.sh`](examples/full-flow-stub.sh).

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
Future database tables:
- `wallets`
- `wallet_transactions`
- `deduction_idempotency`

Hibernate-ready entities already exist under `src/main/java/org/example/wallet/persistence/hibernate/entity`.
Repository-side Hibernate adapter placeholders live under `src/main/java/org/example/wallet/store/hibernate`.

## Testing methodology
I focused testing on the failure modes that matter most for a wallet service:
- repeated deduct requests with the same idempotency key
- reuse of the same idempotency key with a different amount
- concurrent deduct requests against the same wallet
- insufficient balance handling
- auth and authorization errors

I did not spend time on large amounts of controller boilerplate testing because the main risk here is correctness under retries and concurrency.

## Trade-offs
### What is intentionally simplified
- in-memory storage is not durable across restarts
- auth uses static tokens instead of full identity integration
- transaction history is not paginated
- Kafka is represented as a no-op abstraction
- Hibernate runtime mode is modeled, but not fully activated

### Why those choices are reasonable here
- the task prioritizes correctness and clarity over infrastructure setup
- the current design keeps the service easy to run locally
- the repository and mutation abstractions preserve a clean migration path

## What I would do next with more time
1. Activate the Hibernate runtime path with H2 or PostgreSQL and real transactional boundaries.
2. Prefer atomic conditional update for the main balance mutation path; use optimistic locking or `SELECT ... FOR UPDATE` only where needed.
3. Introduce a transactional outbox for wallet events before publishing to Kafka.
4. Add pagination and filtering for transaction history.
5. Replace static tokens with service identity and tenant-aware authorization.
6. Add structured metrics, tracing, and request correlation IDs.
