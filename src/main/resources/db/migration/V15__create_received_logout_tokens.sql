-- Back-channel logout (OpenID Connect Back-Channel Logout 1.0; Workeasy360 ADR-069): the jti of every logout
-- token Easy Project has acted on, so that no logout token is acted on twice.
--
-- A row only matters while the token it names could still pass verification: until its exp (and at most ten
-- minutes after its iat), plus clock leeway. That instant is keep_until. Rows past it are deleted by the next
-- accepted logout token, in that token's own transaction; nothing runs in the background.
--
-- No session data lives here: Easy Project's sessions are the servlet container's, in memory.

CREATE TABLE received_logout_tokens (
    issuer       varchar(512) NOT NULL,
    jti          varchar(255) NOT NULL,
    received_at  timestamptz  NOT NULL,
    keep_until   timestamptz  NOT NULL,

    CONSTRAINT pk_received_logout_tokens PRIMARY KEY (issuer, jti)
);

CREATE INDEX ix_received_logout_tokens_keep_until ON received_logout_tokens (keep_until);

COMMENT ON TABLE received_logout_tokens IS
    'Logout tokens (jti) accepted by the back-channel logout endpoint; a jti found here is refused as a replay.';
COMMENT ON COLUMN received_logout_tokens.keep_until IS
    'After this instant the token can no longer pass verification, so the row may be deleted.';
