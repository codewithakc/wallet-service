package org.example.wallet.company;

import org.example.wallet.WalletServiceConfiguration;
import org.example.wallet.domain.Wallet;
import org.example.wallet.domain.WalletKind;
import org.example.wallet.store.WalletRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.UUID;

/**
 * Ensures the configured platform company wallet exists at application startup.
 */
public final class CompanyWalletBootstrap {
    private static final Logger LOGGER = LoggerFactory.getLogger(CompanyWalletBootstrap.class);

    private CompanyWalletBootstrap() {
    }

    public static String ensureCompanyWallet(WalletRepository walletRepository, WalletServiceConfiguration config) {
        WalletServiceConfiguration.CompanyWalletConfiguration companyConfig = config.getCompanyWallet();
        return walletRepository
                .findByCustomerId(companyConfig.getCustomerId())
                .map(wallet -> {
                    if (wallet.getWalletKind() != WalletKind.PLATFORM) {
                        throw new IllegalStateException(
                                "Configured company customerId is bound to a non-platform wallet.");
                    }
                    LOGGER.info("Using existing platform company wallet {}.", wallet.getWalletId());
                    return wallet.getWalletId();
                })
                .orElseGet(() -> {
                    String walletId = UUID.randomUUID().toString();
                    Wallet wallet =
                            Wallet.platformCompany(
                                    walletId,
                                    companyConfig.getCustomerId(),
                                    companyConfig.getInitialBalance(),
                                    Instant.now());
                    walletRepository.create(wallet);
                    LOGGER.info("Created platform company wallet {} for customerId {}.", walletId, companyConfig.getCustomerId());
                    return walletId;
                });
    }
}
