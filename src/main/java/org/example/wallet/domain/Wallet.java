package org.example.wallet.domain;

import org.example.wallet.error.InvalidRequestException;
import org.example.wallet.error.InsufficientBalanceException;

import java.time.Instant;

public final class Wallet {
    private final String walletId;
    private final String customerId;
    private final long balance;
    private final Instant createdAt;

    public Wallet(String walletId, String customerId, long balance, Instant createdAt) {
        if (balance < 0) {
            throw new InvalidRequestException("Wallet balance cannot be negative.");
        }
        this.walletId = walletId;
        this.customerId = customerId;
        this.balance = balance;
        this.createdAt = createdAt;
    }

    public static Wallet create(String walletId, String customerId, long initialBalance, Instant createdAt) {
        return new Wallet(walletId, customerId, initialBalance, createdAt);
    }

    public Wallet topup(long amount) {
        if (amount <= 0) {
            throw new InvalidRequestException("Top-up amount must be positive.");
        }
        return new Wallet(walletId, customerId, balance + amount, createdAt);
    }

    public Wallet deduct(long amount) {
        if (amount <= 0) {
            throw new InvalidRequestException("Deduction amount must be positive.");
        }
        if (balance < amount) {
            throw new InsufficientBalanceException("Wallet balance is lower than the deduction amount.");
        }
        return new Wallet(walletId, customerId, balance - amount, createdAt);
    }

    public String getWalletId() {
        return walletId;
    }

    public String getCustomerId() {
        return customerId;
    }

    public long getBalance() {
        return balance;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
