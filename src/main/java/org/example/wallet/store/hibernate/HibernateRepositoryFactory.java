package org.example.wallet.store.hibernate;

/**
 * Placeholder factory that marks where Hibernate-backed repository wiring would live once a
 * transactional database is introduced. The application currently defaults to in-memory mode.
 */
public final class HibernateRepositoryFactory {
    private HibernateRepositoryFactory() {
    }

    public static void initialize() {
        throw new HibernateModeNotYetSupported(
                "Hibernate repository wiring is intentionally left as an extension point for this exercise.");
    }
}
