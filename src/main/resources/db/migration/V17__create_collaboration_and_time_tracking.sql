-- Comments on tasks, followers, in-app notifications, time tracking, attachments and password resets.

--------------------------------------------------------------------------------
-- comments on a task (NULL task_id: a comment on the project itself)
--------------------------------------------------------------------------------
ALTER TABLE project_comments
    ADD COLUMN task_id uuid,
    ADD CONSTRAINT fk_project_comments_task FOREIGN KEY (task_id) REFERENCES tasks (id) ON DELETE CASCADE;

CREATE INDEX idx_project_comments_task ON project_comments (task_id, created_at) WHERE task_id IS NOT NULL;

--------------------------------------------------------------------------------
-- task_followers
--------------------------------------------------------------------------------
CREATE TABLE task_followers (
    task_id uuid NOT NULL,
    user_id uuid NOT NULL,

    CONSTRAINT pk_task_followers PRIMARY KEY (task_id, user_id),
    CONSTRAINT fk_task_followers_task FOREIGN KEY (task_id) REFERENCES tasks (id) ON DELETE CASCADE
);

--------------------------------------------------------------------------------
-- notifications
--------------------------------------------------------------------------------
CREATE TABLE notifications (
    id           uuid         NOT NULL,
    recipient_id uuid         NOT NULL,
    type         varchar(40)  NOT NULL,
    title        varchar(400) NOT NULL,
    project_id   uuid,
    task_id      uuid,
    read_at      timestamptz,
    created_at   timestamptz  NOT NULL,
    updated_at   timestamptz  NOT NULL,
    version      bigint       NOT NULL DEFAULT 0,

    CONSTRAINT pk_notifications PRIMARY KEY (id)
);

CREATE INDEX idx_notifications_recipient ON notifications (recipient_id, created_at DESC);
CREATE INDEX idx_notifications_unread ON notifications (recipient_id) WHERE read_at IS NULL;

--------------------------------------------------------------------------------
-- time tracking
--------------------------------------------------------------------------------
CREATE TABLE time_entries (
    id         uuid         NOT NULL,
    task_id    uuid         NOT NULL,
    project_id uuid         NOT NULL,
    user_id    uuid         NOT NULL,
    work_date  date         NOT NULL,
    minutes    integer      NOT NULL,
    note       varchar(500),
    billable   boolean      NOT NULL DEFAULT false,
    created_at timestamptz  NOT NULL,
    updated_at timestamptz  NOT NULL,
    version    bigint       NOT NULL DEFAULT 0,

    CONSTRAINT pk_time_entries PRIMARY KEY (id),
    CONSTRAINT fk_time_entries_task FOREIGN KEY (task_id) REFERENCES tasks (id) ON DELETE CASCADE,
    CONSTRAINT ck_time_entries_minutes CHECK (minutes > 0 AND minutes <= 1440)
);

CREATE INDEX idx_time_entries_task ON time_entries (task_id, work_date DESC);
CREATE INDEX idx_time_entries_user ON time_entries (user_id, work_date DESC);

-- At most one running timer per person.
CREATE TABLE active_timers (
    user_id    uuid        NOT NULL,
    task_id    uuid        NOT NULL,
    started_at timestamptz NOT NULL,

    CONSTRAINT pk_active_timers PRIMARY KEY (user_id),
    CONSTRAINT fk_active_timers_task FOREIGN KEY (task_id) REFERENCES tasks (id) ON DELETE CASCADE
);

--------------------------------------------------------------------------------
-- attachments (the bytes are kept apart so listing files never loads them)
--------------------------------------------------------------------------------
CREATE TABLE attachments (
    id           uuid         NOT NULL,
    task_id      uuid         NOT NULL,
    project_id   uuid         NOT NULL,
    file_name    varchar(255) NOT NULL,
    content_type varchar(150) NOT NULL,
    size_bytes   bigint       NOT NULL,
    uploaded_by  uuid         NOT NULL,
    created_at   timestamptz  NOT NULL,
    updated_at   timestamptz  NOT NULL,
    version      bigint       NOT NULL DEFAULT 0,

    CONSTRAINT pk_attachments PRIMARY KEY (id),
    CONSTRAINT fk_attachments_task FOREIGN KEY (task_id) REFERENCES tasks (id) ON DELETE CASCADE,
    CONSTRAINT ck_attachments_size CHECK (size_bytes >= 0)
);

CREATE INDEX idx_attachments_task ON attachments (task_id, created_at);

CREATE TABLE attachment_contents (
    attachment_id uuid  NOT NULL,
    data          bytea NOT NULL,

    CONSTRAINT pk_attachment_contents PRIMARY KEY (attachment_id),
    CONSTRAINT fk_attachment_contents_attachment
        FOREIGN KEY (attachment_id) REFERENCES attachments (id) ON DELETE CASCADE
);

--------------------------------------------------------------------------------
-- password_reset_tokens
--------------------------------------------------------------------------------
CREATE TABLE password_reset_tokens (
    id         uuid        NOT NULL,
    account_id uuid        NOT NULL,
    token_hash varchar(64) NOT NULL,
    expires_at timestamptz NOT NULL,
    used_at    timestamptz,
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    version    bigint      NOT NULL DEFAULT 0,

    CONSTRAINT pk_password_reset_tokens PRIMARY KEY (id),
    CONSTRAINT uq_password_reset_tokens_hash UNIQUE (token_hash),
    CONSTRAINT fk_password_reset_tokens_account
        FOREIGN KEY (account_id) REFERENCES user_accounts (id) ON DELETE CASCADE ON UPDATE CASCADE
);
