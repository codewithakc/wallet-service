# Wallet Service Low-Level Design

## 1. Package Layout
Planned Java package structure:

```text
org.example.wallet
├── WalletServiceApplication
├── WalletServiceConfiguration
├── api
│   ├── WalletResource
│   ├── request
│   └── response
├── auth
│   ├── AuthFilter
│   ├── CallerContext
│   ├── CallerRole
│   └── TokenAuthenticator
├── domain
│   ├── MoneyMovementType
│   ├── Wallet
│   ├── WalletTransaction
│   ├── DeductionResult
│   └── IdempotencyRecord
├── error
│   ├── ApiExceptionMapper
│   ├── DomainException
│   └── specific exceptions
├── metrics
│   ├── MetricsPort
│   └── NoOpMetricsPort
├── events
│   ├── EventPublisher
│   ├── NoOpEventPublisher
│   └── event payloads
├── service
│   └── WalletApplicationService
├── store
│   ├── WalletRepository
│   ├── TransactionRepository
│   ├── IdempotencyRepository
│   ├── WalletMutationExecutor
│   └── inmemory
│       ├── InMemoryWalletRepository
│       ├── InMemoryTransactionRepository
│       ├── InMemoryIdempotencyRepository
│       └── InMemoryWalletMutationExecutor
└── persistence
    └── hibernate
        ├── entity
        ├── dao
        └── HibernateRepositorySkeletons
```

## 2. Configuration Model
`WalletServiceConfiguration` will contain:
- `server` and `logging` inherited from Dropwizard
- `auth`
  - `customerTokenPrefix`
  - `orderServiceToken`
- `runtimeMode`
  - `INMEMORY`
  - optional placeholder value `HIBERNATE`
- `deductionAmount`
  - default `100`

Optional future section:
- `database`
- `kafka`
- `metrics`

## 3. API Contracts
### 3.1 Create wallet
`POST /wallets`

Request:
```json
{
  "initialBalance": 500
}
```

Response:
```json
{
  "walletId": "wal-123",
  "customerId": "cust-101",
  "balance": 500,
  "createdAt": "2026-05-13T12:00:00Z"
}
```

### 3.2 Top-up
`POST /wallets/{walletId}/topup`

Request:
```json
{
  "amount": 300,
  "referenceId": "topup-001"
}
```

Response:
```json
{
  "walletId": "wal-123",
  "balance": 800,
  "transactionId": "txn-001",
  "status": "SUCCESS"
}
```

### 3.3 Get wallet
`GET /wallets/{walletId}`

Response:
```json
{
  "walletId": "wal-123",
  "customerId": "cust-101",
  "balance": 800,
  "createdAt": "2026-05-13T12:00:00Z"
}
```

### 3.4 Deduct
`POST /wallets/{walletId}/deduct`

Headers:
- `Authorization: Bearer <token>`

Request:
```json
{
  "idempotencyKey": "order-9001",
  "referenceId": "order-9001"
}
```

Success response:
```json
{
  "walletId": "wal-123",
  "balance": 700,
  "transactionId": "txn-002",
  "status": "SUCCESS",
  "deductedAmount": 100,
  "servedFromIdempotencyCache": false
}
```

Insufficient funds response:
```json
{
  "errorCode": "INSUFFICIENT_BALANCE",
  "message": "Wallet balance is lower than the deduction amount."
}
```

### 3.5 Balance
`GET /wallets/{walletId}/balance`

Response:
```json
{
  "walletId": "wal-123",
  "balance": 700
}
```

### 3.6 Transactions
`GET /wallets/{walletId}/transactions`

Response:
```json
[
  {
    "transactionId": "txn-001",
    "type": "TOPUP",
    "amount": 300,
    "referenceId": "topup-001",
    "idempotencyKey": null,
    "createdAt": "2026-05-13T12:10:00Z"
  }
]
```

## 4. Authentication and Authorization
Authentication model:
- static bearer tokens from configuration
- `customerTokenPrefix:<customerId>` maps to role `CUSTOMER` and provides customer identity
- `orderServiceToken` maps to role `ORDER_SERVICE`

Authorization matrix:

| Endpoint | CUSTOMER | ORDER_SERVICE |
|---|---|---|
| `POST /wallets` | yes | no |
| `GET /wallets/{id}` | yes | yes |
| `POST /wallets/{id}/topup` | yes | no |
| `POST /wallets/{id}/deduct` | no | yes |
| `GET /wallets/{id}/balance` | yes | yes |
| `GET /wallets/{id}/transactions` | yes | yes |

Implementation notes:
- `AuthFilter` parses the bearer token and injects a `CallerContext` into request scope.
- Customer identity is derived from the token rather than request parameters.
- `POST /wallets` derives `customerId` from the authenticated token rather than the request body.
- Resource methods validate required roles and enforce wallet ownership through a small helper instead of spreading auth logic through services.

## 5. Domain Objects
### 5.1 Wallet
Fields:
- `walletId`
- `customerId`
- `balance`
- `version`
- `createdAt`

Rules:
- balance must be `>= 0`
- only service methods mutate balance

### 5.2 WalletTransaction
Fields:
- `transactionId`
- `walletId`
- `type`
- `amount`
- `referenceId`
- `idempotencyKey`
- `createdAt`

Rules:
- append-only
- positive amount only
- `type` is `TOPUP` or `DEDUCT`

### 5.3 IdempotencyRecord
Fields:
- `walletId`
- `idempotencyKey`
- `outcome`
- `transactionId`
- `balanceAfterOperation`
- `createdAt`

Rules:
- unique per `walletId + idempotencyKey`
- stores the final outcome of a deduct request

## 6. Service-Level Workflow
### 6.1 createWallet
1. validate input
2. create wallet ID
3. persist wallet
4. emit `WalletCreated` event
5. return response

### 6.2 topup
1. validate amount
2. execute mutation under wallet lock
3. load wallet
4. increase balance
5. persist updated wallet
6. append ledger entry
7. record metrics
8. publish event
9. return updated balance

### 6.3 deduct
1. validate idempotency key
2. execute mutation under wallet lock
3. check `IdempotencyRepository`
4. if record exists, return stored outcome
5. load wallet
6. if balance `< deductionAmount`, store rejected outcome and return domain error
7. decrease balance
8. persist updated wallet
9. append ledger entry
10. persist idempotency result
11. record metrics
12. publish event
13. return success response

### 6.4 Database-backed deduct strategy
Preferred production strategy for the debit path:
1. begin database transaction
2. insert or check the idempotency key with a unique constraint on `(wallet_id, idempotency_key)`
3. execute an atomic conditional balance update such as:
   - `UPDATE wallets SET balance = balance - :amount, version = version + 1 WHERE wallet_id = :walletId AND balance >= :amount`
4. inspect affected row count:
   - `1` means the debit succeeded
   - `0` means insufficient balance or wallet missing
5. append the ledger row
6. persist the final idempotency outcome
7. optionally persist an outbox row for event publication
8. commit

Why this is preferred:
- keeps the non-negative balance rule inside the database write itself
- avoids a race-prone read-then-write flow
- usually performs better than `SELECT ... FOR UPDATE` for hot debit paths

## 7. Repository Interfaces
### 7.1 WalletRepository
Methods:
- `Wallet create(Wallet wallet)`
- `Optional<Wallet> findById(String walletId)`
- `Wallet save(Wallet wallet)`
- `boolean exists(String walletId)`

### 7.2 TransactionRepository
Methods:
- `WalletTransaction append(WalletTransaction transaction)`
- `List<WalletTransaction> findByWalletId(String walletId)`

### 7.3 IdempotencyRepository
Methods:
- `Optional<IdempotencyRecord> find(String walletId, String idempotencyKey)`
- `IdempotencyRecord save(IdempotencyRecord record)`

### 7.4 WalletMutationExecutor
Purpose:
- abstracts atomic mutation boundary
- in-memory implementation uses per-wallet locks
- Hibernate implementation can map to a database transaction and lock policy

Method:
- `<T> T execute(String walletId, Supplier<T> operation)`

## 8. In-Memory Data Structures
### Wallet storage
- `ConcurrentHashMap<String, Wallet>`

### Transaction storage
- `ConcurrentHashMap<String, List<WalletTransaction>>`

### Idempotency storage
- `ConcurrentHashMap<String, IdempotencyRecord>`
where the key is `walletId + "::" + idempotencyKey`

### Lock storage
- `ConcurrentHashMap<String, ReentrantLock>`

Why this works:
- all state transitions for one wallet happen inside the same lock scope
- different wallets can still progress concurrently

## 9. Hibernate Extension Path
The implementation will add Hibernate-ready classes, even if they are not the default runtime path:
- `WalletEntity`
- `WalletTransactionEntity`
- `IdempotencyRecordEntity`
- DAO skeletons or repository adapters

Expected relational constraints:
- `wallets.wallet_id` unique
- `wallet_transactions.transaction_id` unique
- `deduction_idempotency(wallet_id, idempotency_key)` unique
- `wallets.version` for optimistic locking

Concurrency options for the database path:
- preferred: atomic conditional update for debit and top-up style balance changes
- alternative: optimistic locking via `wallets.version` when staying closer to ORM-managed entity updates
- use `SELECT ... FOR UPDATE` only when a workflow requires explicit row serialization across multiple dependent reads and writes

## 10. Error Model
Base exception:
- `DomainException`

Specialized exceptions:
- `WalletNotFoundException`
- `DuplicateWalletException`
- `InvalidRequestException`
- `InsufficientBalanceException`
- `UnauthorizedException`
- `ForbiddenException`

Mapped response:
```json
{
  "errorCode": "WALLET_NOT_FOUND",
  "message": "Wallet wal-123 was not found."
}
```

## 11. Metrics and Events
### MetricsPort
Methods:
- `recordCreateWallet()`
- `recordTopupSuccess()`
- `recordDeductSuccess()`
- `recordDeductRejected()`
- `recordIdempotentReplay()`
- `recordLatency(operation, duration)`

### EventPublisher
Methods:
- `publishWalletCreated(...)`
- `publishWalletToppedUp(...)`
- `publishWalletDeducted(...)`
- `publishWalletDeductionRejected(...)`

Default implementation:
- no-op classes

Future production design:
- metrics delegate to Micrometer or Dropwizard Metrics
- events use Kafka producer plus transactional outbox

## 12. Testing Plan
### Unit tests
- create wallet
- create wallet uses customer identity from token
- top-up success
- deduct success
- deduct insufficient balance
- deduct idempotent replay
- invalid request cases

### Concurrency tests
- many concurrent deduct requests against one wallet should never push balance below zero
- repeated concurrent requests with the same idempotency key should only charge once

### Resource tests
- status codes
- auth behavior
- wallet ownership enforcement
- JSON payload validation

### Optional persistence tests
- verify Hibernate entity mappings compile and basic DAO wiring can be constructed

## 13. Implementation Notes
- Money will be stored as integer rupees in this exercise for readability because the deduction amount is a fixed `100`. If extended to arbitrary amounts, migrate to paise with a dedicated money value object.
- Use immutable response DTOs where possible.
- Keep service logic deterministic and side-effect ordering explicit: state change first, event publishing second.
