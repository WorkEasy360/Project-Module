package com.projectmodule.account.infrastructure;

import com.projectmodule.account.domain.AuthSession;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AuthSessionRepository extends JpaRepository<AuthSession, UUID> {

    Optional<AuthSession> findByTokenHash(String tokenHash);

    /** Ends every session of an account. */
    @Modifying
    @Query("update AuthSession s set s.revokedAt = :now where s.accountId = :accountId and s.revokedAt is null")
    void revokeAll(@Param("accountId") UUID accountId, @Param("now") Instant now);

    /** Removes an account's expired sessions, so the table does not grow with every sign-in. */
    @Modifying
    @Query("delete from AuthSession s where s.accountId = :accountId and s.expiresAt < :now")
    void deleteExpired(@Param("accountId") UUID accountId, @Param("now") Instant now);
}
