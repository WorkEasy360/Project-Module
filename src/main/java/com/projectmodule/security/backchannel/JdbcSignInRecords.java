package com.projectmodule.security.backchannel;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * The PostgreSQL-backed {@link SignInRecords} ({@code V24__create_signed_in_sessions.sql}): one row per signed-in
 * session in {@code signed_in_sessions}, and the recently ended {@code sid}s in {@code ended_sign_ins}. Shared
 * by every instance and kept across restarts.
 *
 * <p><strong>A logout overtaking its sign-in.</strong> A sign-in for a {@code sid} (here, {@link #open}) and a
 * logout token for the same {@code sid} ({@link #end}) can run at the same moment on two instances. Each takes the
 * same transaction-scoped advisory lock, keyed on (issuer, {@code sid}), before it reads or writes anything about
 * that sign-in, so one finishes before the other starts: either the session's row exists before the logout
 * deletes rows (and is deleted), or the logout's {@code ended_sign_ins} row exists before the sign-in looks (and
 * the sign-in is refused). Ended {@code sid}s are kept for {@link SignInSessions#ENDED_SIGN_IN_MEMORY}, longer
 * than an ID token for them can be accepted.
 *
 * <p><strong>Keeping the table small.</strong> A session that ends by itself deletes its row
 * ({@link #closed}, from {@link SignInRecordCleanup}). A row can still be left behind when an instance stops
 * without ending its sessions; it is deleted once it has not been seen for longer than the session idle timeout
 * plus {@link #ABANDONED_MARGIN}: {@link #isOpen} refreshes {@code seen_at} at most every {@link #SEEN_REFRESH}
 * while the session is used, so a session still in use can never be that stale. Both clean-ups run inside a
 * sign-in's own transaction; nothing runs in the background. Deleting a row can only ever sign someone out.
 *
 * <p>Plain JDBC rather than entities, like {@link JdbcReceivedLogoutTokens}: every operation is a single
 * statement on a key.
 */
@Service
public class JdbcSignInRecords implements SignInRecords {

    /** A session in use refreshes its row's {@code seen_at} at most this often (one write per interval). */
    static final Duration SEEN_REFRESH = Duration.ofMinutes(5);
    /** How much longer than the idle timeout an unseen row is kept before it is deleted as abandoned. */
    static final Duration ABANDONED_MARGIN = Duration.ofHours(1);
    /**
     * The first key of every advisory lock taken here. The two-key form of {@code pg_advisory_xact_lock} has a
     * key space of its own, so these locks never meet the one-key locks Flyway takes.
     */
    static final int LOCK_NAMESPACE = 0x45505349;

    private static final String FORGET_ABANDONED = "DELETE FROM signed_in_sessions WHERE seen_at < ?";
    private static final String FORGET_EXPIRED_ENDINGS = "DELETE FROM ended_sign_ins WHERE keep_until <= ?";
    private static final String LOCK_SIGN_IN = "SELECT pg_advisory_xact_lock(?, hashtext(?))";
    private static final String IS_ENDED = """
            SELECT count(*) FROM ended_sign_ins WHERE issuer = ? AND sid = ? AND keep_until > ?""";
    private static final String OPEN = """
            INSERT INTO signed_in_sessions (id, issuer, subject, sid, created_at, seen_at)
            VALUES (?, ?, ?, ?, ?, ?)""";
    private static final String SEEN_AT = "SELECT seen_at FROM signed_in_sessions WHERE id = ?";
    private static final String TOUCH = "UPDATE signed_in_sessions SET seen_at = ? WHERE id = ? AND seen_at < ?";
    private static final String REMEMBER_ENDED = """
            INSERT INTO ended_sign_ins (issuer, sid, ended_at, keep_until) VALUES (?, ?, ?, ?)
            ON CONFLICT (issuer, sid) DO UPDATE
            SET ended_at = EXCLUDED.ended_at, keep_until = GREATEST(ended_sign_ins.keep_until, EXCLUDED.keep_until)""";
    private static final String END_SIGN_IN = "DELETE FROM signed_in_sessions WHERE issuer = ? AND sid = ?";
    private static final String END_SIGN_IN_OF_SUBJECT =
            "DELETE FROM signed_in_sessions WHERE issuer = ? AND sid = ? AND subject = ?";
    private static final String END_SUBJECT = "DELETE FROM signed_in_sessions WHERE issuer = ? AND subject = ?";
    private static final String CLOSE = "DELETE FROM signed_in_sessions WHERE id = ?";

    private final JdbcTemplate jdbc;
    private final TransactionTemplate transactions;
    private final Clock clock;
    private final Duration abandonedAfter;

    public JdbcSignInRecords(JdbcTemplate jdbc, PlatformTransactionManager transactionManager, Clock clock,
                             @Value("${server.servlet.session.timeout:8h}") Duration sessionIdleTimeout) {
        this.jdbc = jdbc;
        this.transactions = new TransactionTemplate(transactionManager);
        this.clock = clock;
        this.abandonedAfter = sessionIdleTimeout.plus(SEEN_REFRESH).plus(ABANDONED_MARGIN);
    }

    @Override
    public String open(String issuer, String subject, String sid) {
        return transactions.execute(status -> {
            // The lock first, as end() takes it: the clean-up below may lock rows a logout for this sid deletes.
            if (sid != null) {
                lock(issuer, sid);
            }
            Instant now = clock.instant();
            jdbc.update(FORGET_ABANDONED, utc(now.minus(abandonedAfter)));
            jdbc.update(FORGET_EXPIRED_ENDINGS, utc(now));
            if (sid != null) {
                Integer ended = jdbc.queryForObject(IS_ENDED, Integer.class, issuer, sid, utc(now));
                if (ended != null && ended > 0) {
                    return null;
                }
            }
            UUID id = UUID.randomUUID();
            jdbc.update(OPEN, id, issuer, subject, sid, utc(now), utc(now));
            return id.toString();
        });
    }

    @Override
    public boolean isOpen(String recordId) {
        UUID id = parse(recordId);
        if (id == null) {
            return false;
        }
        List<OffsetDateTime> seen = jdbc.queryForList(SEEN_AT, OffsetDateTime.class, id);
        if (seen.isEmpty()) {
            return false;
        }
        Instant now = clock.instant();
        Instant refreshBefore = now.minus(SEEN_REFRESH);
        if (seen.get(0).toInstant().isBefore(refreshBefore)) {
            jdbc.update(TOUCH, utc(now), id, utc(refreshBefore));
        }
        return true;
    }

    @Override
    public int end(String issuer, String subject, String sid) {
        Integer ended = transactions.execute(status -> {
            if (sid == null) {
                return jdbc.update(END_SUBJECT, issuer, subject);
            }
            Instant now = clock.instant();
            lock(issuer, sid);
            jdbc.update(REMEMBER_ENDED, issuer, sid, utc(now), utc(now.plus(SignInSessions.ENDED_SIGN_IN_MEMORY)));
            return subject == null
                    ? jdbc.update(END_SIGN_IN, issuer, sid)
                    : jdbc.update(END_SIGN_IN_OF_SUBJECT, issuer, sid, subject);
        });
        return ended == null ? 0 : ended;
    }

    @Override
    public void closed(String recordId) {
        UUID id = parse(recordId);
        if (id != null) {
            jdbc.update(CLOSE, id);
        }
    }

    /** Held until the surrounding transaction ends. */
    private void lock(String issuer, String sid) {
        jdbc.query(LOCK_SIGN_IN, resultSet -> { }, LOCK_NAMESPACE, issuer + '\n' + sid);
    }

    private static UUID parse(String recordId) {
        if (recordId == null) {
            return null;
        }
        try {
            return UUID.fromString(recordId);
        } catch (IllegalArgumentException notARecordId) {
            return null;
        }
    }

    private static OffsetDateTime utc(Instant instant) {
        return OffsetDateTime.ofInstant(instant, ZoneOffset.UTC);
    }
}
