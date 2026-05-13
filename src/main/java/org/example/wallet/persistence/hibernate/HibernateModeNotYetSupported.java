package org.example.wallet.persistence.hibernate;

/**
 * Signals that the Hibernate-backed runtime path is intentionally present only as an extension
 * point in this submission.
 */
public class HibernateModeNotYetSupported extends RuntimeException {
    public HibernateModeNotYetSupported(String message) {
        super(message);
    }
}
