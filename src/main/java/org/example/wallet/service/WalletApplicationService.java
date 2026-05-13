package org.example.wallet.service;

import org.example.wallet.domain.DeductionResult;
import org.example.wallet.domain.DeductionStatus;
import org.example.wallet.domain.IdempotencyRecord;
import org.example.wallet.domain.MoneyMovementType;
import org.example.wallet.domain.TopupResult;
import org.example.wallet.domain.Wallet;
import org.example.wallet.domain.WalletTransaction;
import org.example.wallet.error.IdempotencyConflictException;
import org.example.wallet.error.InvalidRequestException;
import org.example.wallet.error.WalletNotFoundException;
import org.example.wallet.events.EventPublisher;
import org.example.wallet.metrics.MetricsPort;
import org.example.wallet.store.IdempotencyRepository;
import org.example.wallet.store.TransactionRepository;
import org.example.wallet.store.WalletMutationExecutor;
import org.example.wallet.store.WalletRepository;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Orchestrates wallet commands while preserving the core business invariants.
 *
 * <p>This service owns the rules around non-negative balances, append-only ledger writes, and
 * idempotent deductions under concurrent access.
 */
public class WalletApplicationService {
    private final WalletRepository walletRepository;
    private final TransactionRepository transactionRepository;
    private final IdempotencyRepository idempotencyRepository;
    private final WalletMutationExecutor mutationExecutor;
    private final MetricsPort metricsPort;
    private final EventPublisher eventPublisher;

    public WalletApplicationService(
            WalletRepository walletRepository,
            TransactionRepository transactionRepository,
            IdempotencyRepository idempotencyRepository,
            WalletMutationExecutor mutationExecutor,
            MetricsPort metricsPort,
            EventPublisher eventPublisher) {
        this.walletRepository = walletRepository;
        this.transactionRepository = transactionRepository;
        this.idempotencyRepository = idempotencyRepository;
        this.mutationExecutor = mutationExecutor;
        this.metricsPort = metricsPort;
        this.eventPublisher = eventPublisher;
    }

    /**
     * Creates a new wallet for a customer and optionally records an initial top-up ledger entry.
     */
    public Wallet createWallet(String customerId, long initialBalance) {
        validateCustomerId(customerId);
        if (initialBalance < 0) {
            throw new InvalidRequestException("Initial balance cannot be negative.");
        }

        String walletId = UUID.randomUUID().toString();
        return measure("createWallet", () -> mutationExecutor.execute(walletId, () -> {
            Instant now = Instant.now();
            Wallet wallet = Wallet.create(walletId, customerId.trim(), initialBalance, now);
            walletRepository.create(wallet);

            if (initialBalance > 0) {
                WalletTransaction initialTransaction = new WalletTransaction(
                        UUID.randomUUID().toString(),
                        walletId,
                        MoneyMovementType.TOPUP,
                        initialBalance,
                        "wallet-initial-balance",
                        null,
                        initialBalance,
                        now);
                transactionRepository.append(initialTransaction);
            }

            metricsPort.recordCreateWallet();
            eventPublisher.publishWalletCreated(wallet);
            return wallet;
        }));
    }

    /**
     * Applies a positive top-up to the wallet and records a ledger entry.
     */
    public TopupResult topup(String walletId, long amount, String referenceId) {
        return measure("topup", () -> mutationExecutor.execute(walletId, () -> {
            Wallet wallet = requireWallet(walletId);
            Wallet updatedWallet = wallet.topup(amount);
            walletRepository.save(updatedWallet);

            WalletTransaction transaction = new WalletTransaction(
                    UUID.randomUUID().toString(),
                    walletId,
                    MoneyMovementType.TOPUP,
                    amount,
                    normalizeReference(referenceId),
                    null,
                    updatedWallet.getBalance(),
                    Instant.now());
            transactionRepository.append(transaction);

            metricsPort.recordTopupSuccess();
            eventPublisher.publishWalletToppedUp(updatedWallet, transaction);
            return new TopupResult(
                    walletId,
                    updatedWallet.getBalance(),
                    transaction.transactionId(),
                    transaction.referenceId());
        }));
    }

    /**
     * Applies the requested deduction amount once per idempotency key.
     *
     * <p>If the same wallet and idempotency key are seen again, the previously stored outcome is
     * returned without performing another deduction.
     */
    public DeductionResult deduct(String walletId, String idempotencyKey, long amount, String referenceId) {
        validateIdempotencyKey(idempotencyKey);
        validateDeductionAmount(amount);

        return measure("deduct", () -> mutationExecutor.execute(walletId, () -> {
            IdempotencyRecord existingRecord = idempotencyRepository.find(walletId, idempotencyKey).orElse(null);
            if (existingRecord != null) {
                if (existingRecord.requestedAmount() != amount) {
                    throw new IdempotencyConflictException(
                            "The same idempotency key cannot be reused with a different amount.");
                }
                metricsPort.recordIdempotentReplay();
                return DeductionResult.fromRecord(existingRecord, true);
            }

            Wallet wallet = requireWallet(walletId);
            if (wallet.getBalance() < amount) {
                IdempotencyRecord rejectedRecord = new IdempotencyRecord(
                        walletId,
                        idempotencyKey,
                        amount,
                        DeductionStatus.REJECTED,
                        null,
                        wallet.getBalance(),
                        "INSUFFICIENT_BALANCE",
                        "Wallet balance is lower than the deduction amount.",
                        Instant.now());
                idempotencyRepository.save(rejectedRecord);

                DeductionResult rejectedResult = DeductionResult.fromRecord(rejectedRecord, false);
                metricsPort.recordDeductRejected();
                eventPublisher.publishWalletDeductionRejected(rejectedResult);
                return rejectedResult;
            }

            Wallet updatedWallet = wallet.deduct(amount);
            walletRepository.save(updatedWallet);

            WalletTransaction transaction = new WalletTransaction(
                    UUID.randomUUID().toString(),
                    walletId,
                    MoneyMovementType.DEDUCT,
                    amount,
                    normalizeReference(referenceId, idempotencyKey),
                    idempotencyKey,
                    updatedWallet.getBalance(),
                    Instant.now());
            transactionRepository.append(transaction);

            IdempotencyRecord successRecord = new IdempotencyRecord(
                    walletId,
                    idempotencyKey,
                    amount,
                    DeductionStatus.SUCCESS,
                    transaction.transactionId(),
                    updatedWallet.getBalance(),
                    null,
                    null,
                    Instant.now());
            idempotencyRepository.save(successRecord);

            DeductionResult successResult = DeductionResult.fromRecord(successRecord, false);
            metricsPort.recordDeductSuccess();
            eventPublisher.publishWalletDeducted(successResult);
            return successResult;
        }));
    }

    /**
     * Returns the current wallet snapshot.
     */
    public Wallet getWallet(String walletId) {
        return measure("getWallet", () -> requireWallet(walletId));
    }

    /**
     * Returns the latest wallet balance.
     */
    public long getBalance(String walletId) {
        return measure("getBalance", () -> requireWallet(walletId).getBalance());
    }

    /**
     * Returns the ordered ledger history for a wallet.
     */
    public List<WalletTransaction> getTransactions(String walletId) {
        return measure("getTransactions", () -> {
            requireWallet(walletId);
            return transactionRepository.findByWalletId(walletId);
        });
    }

    private Wallet requireWallet(String walletId) {
        if (walletId == null || walletId.isBlank()) {
            throw new InvalidRequestException("Wallet ID must be provided.");
        }
        return walletRepository.findById(walletId)
                .orElseThrow(() -> new WalletNotFoundException(walletId));
    }

    private void validateCustomerId(String customerId) {
        if (customerId == null || customerId.isBlank()) {
            throw new InvalidRequestException("Customer ID must be provided.");
        }
    }

    private void validateIdempotencyKey(String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new InvalidRequestException("Idempotency key must be provided.");
        }
    }

    private void validateDeductionAmount(long amount) {
        if (amount <= 0) {
            throw new InvalidRequestException("Deduction amount must be positive.");
        }
    }

    private String normalizeReference(String referenceId) {
        return (referenceId == null || referenceId.isBlank()) ? null : referenceId.trim();
    }

    private String normalizeReference(String referenceId, String fallbackReference) {
        String normalized = normalizeReference(referenceId);
        return normalized != null ? normalized : fallbackReference;
    }

    private <T> T measure(String operation, Supplier<T> supplier) {
        Instant start = Instant.now();
        try {
            return supplier.get();
        } finally {
            metricsPort.recordLatency(operation, Duration.between(start, Instant.now()));
        }
    }
}
