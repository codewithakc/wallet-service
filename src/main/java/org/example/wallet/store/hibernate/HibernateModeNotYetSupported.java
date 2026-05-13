package org.example.wallet.store.hibernate;

/**
 * Signals that the Hibernate-backed repository adapter is intentionally present only as an
 * extension point in this submission.
 */
public class HibernateModeNotYetSupported extends RuntimeException {
    public HibernateModeNotYetSupported(String message) {
        super(message);
    }
}
