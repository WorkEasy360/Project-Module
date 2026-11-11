-- Teammates: a workspace (organization) has admins and members, and admins invite people into it.

-- Everyone who has registered so far created their own workspace, so they administer it.
ALTER TABLE user_accounts
    ADD COLUMN workspace_role varchar(20) NOT NULL DEFAULT 'ADMIN',
    ADD CONSTRAINT ck_user_accounts_workspace_role CHECK (workspace_role IN ('ADMIN', 'MEMBER'));

CREATE TABLE workspace_invitations (
    id              uuid         NOT NULL,
    organization_id uuid         NOT NULL,
    email           varchar(254) NOT NULL,
    token_hash      varchar(64)  NOT NULL,
    invited_by      uuid         NOT NULL,
    expires_at      timestamptz  NOT NULL,
    accepted_at     timestamptz,
    revoked_at      timestamptz,
    created_at      timestamptz  NOT NULL,
    updated_at      timestamptz  NOT NULL,
    version         bigint       NOT NULL DEFAULT 0,

    CONSTRAINT pk_workspace_invitations PRIMARY KEY (id),
    CONSTRAINT uq_workspace_invitations_token_hash UNIQUE (token_hash),
    CONSTRAINT ck_workspace_invitations_email_normalized CHECK (email = lower(btrim(email)))
);

COMMENT ON COLUMN workspace_invitations.token_hash IS
    'SHA-256 of the invitation token. The link itself is shown once, to the person who created it.';

CREATE INDEX idx_workspace_invitations_organization ON workspace_invitations (organization_id, created_at DESC);
