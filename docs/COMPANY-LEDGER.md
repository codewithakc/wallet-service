# Company mirror ledger

## Purpose

The platform maintains a **company (platform) wallet** in the same wallet service. Customer money movements are mirrored on that wallet so platform revenue and liability stay auditable:

| Customer event | Company mirror |
|----------------|----------------|
| Successful order deduct (`DEDUCT`) | Credit (`TOPUP`) same amount |
| Customer top-up (`TOPUP`) | Debit (`DEDUCT`) same amount |

Rejected customer deducts are **not** mirrored.

## Consistency model

1. Customer wallet mutation completes (balance, ledger, idempotency).
2. An outbox row is appended in the **same** `WalletMutationExecutor` scope as the customer change.
3. `OutboxProcessor` polls pending rows and invokes `CompanyMirrorLedgerHandler`.
4. `CompanyMirrorService` applies the mirror under the company `walletId` lock with idempotency key `mirror:{customerTransactionId}`.

Customer API responses are not blocked on the company mirror (eventual consistency; typically sub-second with the in-process poller).

## Idempotency

| Layer | Key |
|-------|-----|
| Customer deduct | `walletId + idempotencyKey` (Order Service) |
| Company mirror | `mirror:{customerTransactionId}` |

Idempotent customer deduct replays do not append a new outbox event. Outbox redelivery does not double-apply company entries.

## Platform wallet rules

- `WalletKind.PLATFORM` wallets may go **negative** on mirror debits (customer top-ups increase platform liability).
- Customer wallets remain non-negative.

## Configuration

```yaml
companyWallet:
  customerId: platform-company
  initialBalance: 0
outbox:
  pollIntervalMs: 500
  batchSize: 50
```

`CompanyWalletBootstrap` creates the platform wallet at startup if missing.

## Components

| Class | Role |
|-------|------|
| `OutboxEventPublisher` | Implements `EventPublisher`; writes outbox rows |
| `OutboxProcessor` | Polls and dispatches events |
| `CompanyMirrorLedgerHandler` | Routes events to mirror service |
| `CompanyMirrorService` | Applies TOPUP/DEDUCT on company wallet |
| `InMemoryOutboxRepository` | Outbox store (in-memory) |
| `InMemoryMirrorIdempotencyRepository` | Mirror dedup store |

## Future: Kafka

Replace the in-process dispatcher with:

1. Same outbox table written in the DB transaction as customer state.
2. Relay process publishes to Kafka.
3. Consumer invokes the same `CompanyMirrorLedgerHandler` logic.

`OutboxEventEntity` is a Hibernate placeholder for that migration.
