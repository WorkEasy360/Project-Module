-- Email and password sign-in.
--
-- A user account's id IS the user id every other table already stores (owner_id, user_id,
-- assignee_id, ...), and its organization_id is the tenant those tables are scoped by. No existing
-- table changes: a signed-in account is simply the identity those opaque references point at.

--------------------------------------------------------------------------------
-- user_accounts
--------------------------------------------------------------------------------
CREATE TABLE user_accounts (
    id                    uuid         NOT NULL,
    organization_id       uuid         NOT NULL,
    email                 varchar(254) NOT NULL,
    display_name          varchar(120) NOT NULL,
    password_hash         varchar(100) NOT NULL,
    failed_login_attempts integer      NOT NULL DEFAULT 0,
    locked_until          timestamptz,
    last_login_at         timestamptz,
    created_at            timestamptz  NOT NULL,
    updated_at            timestamptz  NOT NULL,
    version               bigint       NOT NULL DEFAULT 0,

    CONSTRAINT pk_user_accounts PRIMARY KEY (id),
    CONSTRAINT uq_user_accounts_email UNIQUE (email),
    -- Addresses are stored lower-case, so the unique constraint is case-insensitive in effect.
    CONSTRAINT ck_user_accounts_email_normalized CHECK (email = lower(btrim(email))),
    CONSTRAINT ck_user_accounts_display_name_not_blank CHECK (length(btrim(display_name)) > 0),
    CONSTRAINT ck_user_accounts_failed_attempts CHECK (failed_login_attempts >= 0)
);

COMMENT ON COLUMN user_accounts.password_hash IS
    'BCrypt hash. The password itself is never stored or logged.';

CREATE INDEX idx_user_accounts_organization ON user_accounts (organization_id);

--------------------------------------------------------------------------------
-- auth_sessions
--------------------------------------------------------------------------------
CREATE TABLE auth_sessions (
    id          uuid        NOT NULL,
    account_id  uuid        NOT NULL,
    token_hash  varchar(64) NOT NULL,
    expires_at  timestamptz NOT NULL,
    revoked_at  timestamptz,
    created_at  timestamptz NOT NULL,
    updated_at  timestamptz NOT NULL,
    version     bigint      NOT NULL DEFAULT 0,

    CONSTRAINT pk_auth_sessions PRIMARY KEY (id),
    -- ON UPDATE CASCADE so an operator can link an account to a pre-existing user id
    -- (see docs/project/33-AUTHENTICATION.md) without first deleting its sessions.
    CONSTRAINT fk_auth_sessions_account
        FOREIGN KEY (account_id) REFERENCES user_accounts (id) ON DELETE CASCADE ON UPDATE CASCADE,
    CONSTRAINT uq_auth_sessions_token_hash UNIQUE (token_hash)
);

COMMENT ON COLUMN auth_sessions.token_hash IS
    'SHA-256 of the session token. The token itself is only ever held by the client, so reading this table does not yield a usable session.';

CREATE INDEX idx_auth_sessions_account ON auth_sessions (account_id);
