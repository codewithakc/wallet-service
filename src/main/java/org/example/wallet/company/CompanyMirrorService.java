package org.example.wallet.company;

import org.example.wallet.domain.MoneyMovementType;
import org.example.wallet.domain.Wallet;
import org.example.wallet.domain.WalletTransaction;
import org.example.wallet.error.WalletNotFoundException;
import org.example.wallet.events.CustomerMovementPayload;
import org.example.wallet.store.MirrorIdempotencyRepository;
import org.example.wallet.store.TransactionRepository;
import org.example.wallet.store.WalletMutationExecutor;
import org.example.wallet.store.WalletRepository;

import java.time.Instant;
import java.util.UUID;

/**
 * Applies mirrored ledger entries to the platform company wallet.
 */
public class CompanyMirrorService {
    private static final String MIRROR_REFERENCE_PREFIX = "mirror-of:";

    private final WalletRepository walletRepository;
    private final TransactionRepository transactionRepository;
    private final WalletMutationExecutor mutationExecutor;
    private final MirrorIdempotencyRepository mirrorIdempotencyRepository;

    public CompanyMirrorService(
            WalletRepository walletRepository,
            TransactionRepository transactionRepository,
            WalletMutationExecutor mutationExecutor,
            MirrorIdempotencyRepository mirrorIdempotencyRepository) {
        this.walletRepository = walletRepository;
        this.transactionRepository = transactionRepository;
        this.mutationExecutor = mutationExecutor;
        this.mirrorIdempotencyRepository = mirrorIdempotencyRepository;
    }

    public void mirrorCustomerDeduct(CustomerMovementPayload payload) {
        applyMirror(payload, MoneyMovementType.TOPUP, true);
    }

    public void mirrorCustomerTopup(CustomerMovementPayload payload) {
        applyMirror(payload, MoneyMovementType.DEDUCT, false);
    }

    private void applyMirror(CustomerMovementPayload payload, MoneyMovementType mirrorType, boolean creditCompany) {
        String companyWalletId = payload.companyWalletId();
        String mirrorKey = mirrorIdempotencyKey(payload.customerTransactionId());

        mutationExecutor.execute(companyWalletId, () -> {
            if (mirrorIdempotencyRepository.exists(companyWalletId, mirrorKey)) {
                return null;
            }

            Wallet companyWallet = requireCompanyWallet(companyWalletId);
            Wallet updatedWallet =
                    creditCompany ? companyWallet.topup(payload.amount()) : companyWallet.deduct(payload.amount());
            walletRepository.save(updatedWallet);

            WalletTransaction transaction =
                    new WalletTransaction(
                            UUID.randomUUID().toString(),
                            companyWalletId,
                            mirrorType,
                            payload.amount(),
                            mirrorReference(payload.customerTransactionId()),
                            mirrorKey,
                            updatedWallet.getBalance(),
                            Instant.now());
            transactionRepository.append(transaction);
            mirrorIdempotencyRepository.save(companyWalletId, mirrorKey);
            return null;
        });
    }

    private Wallet requireCompanyWallet(String companyWalletId) {
        return walletRepository
                .findById(companyWalletId)
                .orElseThrow(() -> new WalletNotFoundException(companyWalletId));
    }

    public static String mirrorIdempotencyKey(String customerTransactionId) {
        return "mirror:" + customerTransactionId;
    }

    private static String mirrorReference(String customerTransactionId) {
        return MIRROR_REFERENCE_PREFIX + customerTransactionId;
    }
}
