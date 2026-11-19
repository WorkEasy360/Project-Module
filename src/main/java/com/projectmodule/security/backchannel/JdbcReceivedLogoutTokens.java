package com.projectmodule.security.backchannel;

import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * The PostgreSQL-backed {@link ReceivedLogoutTokens} ({@code V23__create_received_logout_tokens.sql}). Shared by
 * every instance and kept across restarts, so a captured token cannot be replayed against sessions opened after
 * a deploy.
 *
 * <p>One transaction per token: rows that can no longer matter are deleted, the {@code jti} is inserted with
 * {@code ON CONFLICT DO NOTHING} (no row inserted: a replay), and the sessions are ended. If ending them throws,
 * the transaction rolls back and the {@code jti} is not recorded. Two deliveries of the same {@code jti} at once
 * are serialised by the primary key: the second waits for the first to commit, then finds the row. Plain JDBC
 * rather than an entity: this is an append-only ledger whose one operation is an atomic insert-if-absent.
 */
@Service
public class JdbcReceivedLogoutTokens implements ReceivedLogoutTokens {

    private static final String FORGET_EXPIRED = "DELETE FROM received_logout_tokens WHERE keep_until < ?";
    private static final String RECORD = """
            INSERT INTO received_logout_tokens (issuer, jti, received_at, keep_until)
            VALUES (?, ?, ?, ?)
            ON CONFLICT (issuer, jti) DO NOTHING""";

    private final JdbcTemplate jdbc;
    private final TransactionTemplate transactions;
    private final Clock clock;

    public JdbcReceivedLogoutTokens(JdbcTemplate jdbc, PlatformTransactionManager transactionManager, Clock clock) {
        this.jdbc = jdbc;
        this.transactions = new TransactionTemplate(transactionManager);
        this.clock = clock;
    }

    @Override
    public boolean acceptOnce(String issuer, String jti, Instant keepUntil, Runnable endSessions) {
        Boolean accepted = transactions.execute(status -> {
            OffsetDateTime now = OffsetDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
            jdbc.update(FORGET_EXPIRED, now);
            if (jdbc.update(RECORD, issuer, jti, now, OffsetDateTime.ofInstant(keepUntil, ZoneOffset.UTC)) == 0) {
                return false;
            }
            endSessions.run();
            return true;
        });
        return Boolean.TRUE.equals(accepted);
    }
}
