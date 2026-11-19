-- Back-channel logout on every instance (OpenID Connect Back-Channel Logout 1.0; Workeasy360 ADR-069).
-- Numbered V24 (V16 on the workeasy360-sso branch, then V21; neither was ever applied to a persistent database).
--
-- Easy Project's Workeasy360 sessions are the servlet container's, in the memory of the instance that created them. A
-- logout token reaches one instance. So that it ends the person's sessions on every instance (and so that a
-- restart can never bring an ended session back), each signed-in session has a row here, shared by all
-- instances, and every request made with a signed-in session checks that its row still exists. A logout token
-- deletes the rows it names, in the transaction that records its jti (received_logout_tokens, V23). A missing
-- row means "ended": deleting a row can only ever sign someone out.
--
-- No session id is stored: it is a bearer credential. The session carries the row's id instead.

CREATE TABLE signed_in_sessions (
    id          uuid         NOT NULL,
    issuer      varchar(512) NOT NULL,
    subject     varchar(255) NOT NULL,
    sid         varchar(255),
    created_at  timestamptz  NOT NULL,
    seen_at     timestamptz  NOT NULL,

    CONSTRAINT pk_signed_in_sessions PRIMARY KEY (id)
);

-- A logout token naming only sub ends every session of that person; one naming sid, that sign-in's sessions.
CREATE INDEX ix_signed_in_sessions_issuer_subject ON signed_in_sessions (issuer, subject);
CREATE INDEX ix_signed_in_sessions_issuer_sid ON signed_in_sessions (issuer, sid) WHERE sid IS NOT NULL;
-- Rows of sessions that ended without saying so (an instance that stopped) are deleted once unseen for longer
-- than the session idle timeout plus an hour, by the next sign-in; nothing runs in the background.
CREATE INDEX ix_signed_in_sessions_seen_at ON signed_in_sessions (seen_at);

COMMENT ON TABLE signed_in_sessions IS
    'One row per signed-in Easy Project session, on any instance. A session whose row is gone is ended at its next request.';
COMMENT ON COLUMN signed_in_sessions.sid IS
    'The Workeasy360 sign-in (ID token sid) that opened the session; null when the ID token carried none.';
COMMENT ON COLUMN signed_in_sessions.seen_at IS
    'Refreshed at most every five minutes while the session is used.';

-- Sign-ins (sid) that a logout token has ended, kept for ten minutes (longer than an ID token can be accepted),
-- so that a sign-in for one that is still on its way back to the callback, on any instance, is refused.
CREATE TABLE ended_sign_ins (
    issuer      varchar(512) NOT NULL,
    sid         varchar(255) NOT NULL,
    ended_at    timestamptz  NOT NULL,
    keep_until  timestamptz  NOT NULL,

    CONSTRAINT pk_ended_sign_ins PRIMARY KEY (issuer, sid)
);

CREATE INDEX ix_ended_sign_ins_keep_until ON ended_sign_ins (keep_until);

COMMENT ON TABLE ended_sign_ins IS
    'Workeasy360 sign-ins (sid) ended by a logout token; a sign-in for one of these is refused until keep_until.';
