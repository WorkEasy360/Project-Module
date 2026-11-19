-- Profile photos. The image lives in its own table so loading people (names, roles) never loads
-- image bytes; user_accounts only records when the photo last changed, so the app knows whether
-- there is one and when to fetch it again. Additive only.

ALTER TABLE user_accounts
    ADD COLUMN avatar_updated_at timestamptz;

CREATE TABLE user_avatars (
    account_id   uuid        NOT NULL,
    content_type varchar(40) NOT NULL,
    content      bytea       NOT NULL,
    updated_at   timestamptz NOT NULL,

    CONSTRAINT pk_user_avatars PRIMARY KEY (account_id),
    -- ON UPDATE CASCADE so linking an account to an earlier user id keeps its photo.
    CONSTRAINT fk_user_avatars_account FOREIGN KEY (account_id) REFERENCES user_accounts (id)
        ON DELETE CASCADE ON UPDATE CASCADE
);
