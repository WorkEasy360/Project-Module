package com.projectmodule.common.identity;

import jakarta.persistence.EntityManager;
import java.util.UUID;

/**
 * Who may hold an internal user id: exactly one credential holder, either one email account
 * ({@code user_accounts.id}) or one Workeasy360 identity ({@code federated_identities.user_id}), never both.
 *
 * <p>Both kinds of sign-in can adopt an id that already owns work: the email hand-over takes the ids an earlier
 * version kept in the browser, and a first Workeasy360 sign-in takes {@code UUID(sub)}. Without this rule a
 * password and a Workeasy360 account could end up acting as the same person without anyone having linked them.
 * Each side takes the same transaction-scoped advisory lock on the id before it checks the other side and
 * adopts the id, so two first sign-ins on two instances cannot both win.
 */
public final class UserIdOwnership {

    /**
     * The first key of the two-key advisory lock. The two-key form has a key space of its own, so it never meets
     * Flyway's one-key locks; the value differs from the sign-in record's namespace.
     */
    public static final int LOCK_NAMESPACE = 0x45505549;

    private UserIdOwnership() {
    }

    /** Holds the id until the surrounding transaction ends. Must run inside one. */
    public static void lock(EntityManager entityManager, UUID userId) {
        entityManager.createNativeQuery(
                        "SELECT 1 FROM (SELECT pg_advisory_xact_lock(:namespace, hashtext(:userId))) AS locked")
                .setParameter("namespace", LOCK_NAMESPACE)
                .setParameter("userId", userId.toString())
                .getSingleResult();
    }

    /** The id belongs to an email account. */
    public static boolean heldByEmailAccount(EntityManager entityManager, UUID userId) {
        return exists(entityManager, "SELECT EXISTS (SELECT 1 FROM user_accounts WHERE id = :userId)", userId);
    }

    /** The id belongs to a Workeasy360 (or any OpenID Connect) identity. */
    public static boolean heldByFederatedIdentity(EntityManager entityManager, UUID userId) {
        return exists(entityManager, "SELECT EXISTS (SELECT 1 FROM federated_identities WHERE user_id = :userId)",
                userId);
    }

    private static boolean exists(EntityManager entityManager, String sql, UUID userId) {
        Object found = entityManager.createNativeQuery(sql).setParameter("userId", userId).getSingleResult();
        return Boolean.TRUE.equals(found);
    }
}
