package com.projectmodule.security.identity;

import com.projectmodule.common.identity.ExternalUserId;
import com.projectmodule.common.identity.UuidV7;
import java.time.Clock;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * The PostgreSQL-backed {@link FederatedIdentityStore}.
 *
 * <p>Two first sign-ins of the same person at once both try to insert the same (issuer, subject); the
 * unique constraint lets exactly one win and the other reads the winner's row. Each attempt is its own
 * transaction so a constraint violation never poisons the next read.
 */
@Service
public class JpaFederatedIdentityStore implements FederatedIdentityStore {

    private static final int ATTEMPTS = 3;

    private final FederatedIdentityRepository repository;
    private final TransactionTemplate transactions;
    private final Clock clock;

    public JpaFederatedIdentityStore(FederatedIdentityRepository repository,
                                     PlatformTransactionManager transactionManager,
                                     Clock clock) {
        this.repository = repository;
        this.transactions = new TransactionTemplate(transactionManager);
        this.clock = clock;
    }

    @Override
    public ExternalUserId resolve(String issuer, String subject, String email, String displayName) {
        DataIntegrityViolationException last = null;
        for (int attempt = 0; attempt < ATTEMPTS; attempt++) {
            try {
                UUID userId = transactions.execute(status -> resolveOnce(issuer, subject, email, displayName));
                return ExternalUserId.of(userId);
            } catch (DataIntegrityViolationException conflict) {
                last = conflict;
            }
        }
        throw last;
    }

    private UUID resolveOnce(String issuer, String subject, String email, String displayName) {
        Optional<FederatedIdentity> existing = repository.findByIssuerAndSubject(issuer, subject);
        if (existing.isPresent()) {
            FederatedIdentity identity = existing.get();
            identity.signedIn(email, displayName, clock.instant());
            repository.save(identity);
            return identity.getUserId();
        }
        UUID userId = newUserId(subject);
        repository.saveAndFlush(new FederatedIdentity(
                UuidV7.generate(), issuer, subject, userId, email, displayName, clock.instant()));
        return userId;
    }

    private UUID newUserId(String subject) {
        UUID fromSubject = parseUuid(subject);
        if (fromSubject != null && !repository.existsByUserId(fromSubject)) {
            return fromSubject;
        }
        return UuidV7.generate();
    }

    /** The subject as a UUID when (and only when) it is written as a canonical one. */
    static UUID parseUuid(String subject) {
        if (subject == null || subject.length() != 36) {
            return null;
        }
        try {
            return UUID.fromString(subject);
        } catch (IllegalArgumentException notUuid) {
            return null;
        }
    }
}
