-- Sign in with Workeasy360: which Workeasy360 account (issuer + OIDC subject) is which internal user.
--
-- A user is an opaque identifier (ExternalUserId) that other tables store without a foreign key; an email
-- account's id (user_accounts, V14) is one, a Workeasy360 identity's user_id is another, and one id never
-- belongs to both. Workeasy360's `sub` is its own user id, which is exactly the identifier the X-User-Id
-- header used to carry, so for a sub that is a UUID the user_id is the sub itself and every row written
-- under that id keeps its owner. A sub that is not a UUID, or a UUID that is already bound to ANY identity
-- (of any issuer) or is an email account's id, gets a freshly generated user_id.
--
-- Numbered V22 (V14 on the workeasy360-sso branch, then V19 until main's own V19-V21 came first; neither number
-- was ever applied to a persistent database): main's V14-V21 came first.
--
-- Identity is (issuer, subject), never the e-mail address; e-mail and display name are profile data
-- refreshed at each sign-in and are not used to match anything.

CREATE TABLE federated_identities (
    id               uuid         NOT NULL,
    issuer           varchar(512) NOT NULL,
    subject          varchar(255) NOT NULL,
    user_id          uuid         NOT NULL,
    email            varchar(320),
    display_name     varchar(200),
    created_at       timestamptz  NOT NULL,
    last_sign_in_at  timestamptz  NOT NULL,

    CONSTRAINT pk_federated_identities PRIMARY KEY (id),
    CONSTRAINT uq_federated_identities_issuer_subject UNIQUE (issuer, subject),
    -- Global, not per issuer: a user id belongs to exactly one external identity, whichever issuer vouches
    -- for it, so two issuers can never be mapped onto the same internal user.
    CONSTRAINT uq_federated_identities_user UNIQUE (user_id),
    CONSTRAINT ck_federated_identities_subject_not_blank CHECK (length(btrim(subject)) > 0)
);

COMMENT ON TABLE federated_identities IS
    'Maps an external OpenID Connect identity (issuer, sub) to the internal user id used by every other table.';
COMMENT ON COLUMN federated_identities.user_id IS
    'The ExternalUserId stored in domain rows. Opaque reference, intentionally no foreign key anywhere.';
COMMENT ON COLUMN federated_identities.email IS
    'Profile data only, as last reported by the issuer. Never used to match or link an identity.';
