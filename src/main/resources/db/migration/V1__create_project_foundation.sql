-- P0 foundation: projects, membership, audit trail and the event outbox.
--
-- Ownership boundary: organization_id, owner_id, user_id, actor_id and archived_by all
-- reference records owned by other modules. They are stored as opaque uuid values with no
-- foreign key, because the tables they point at do not live in this database and must not.

--------------------------------------------------------------------------------
-- projects
--------------------------------------------------------------------------------
CREATE TABLE projects (
    id               uuid         NOT NULL,
    organization_id  uuid         NOT NULL,
    name             varchar(200) NOT NULL,
    description      text,
    status           varchar(30)  NOT NULL,
    priority         varchar(20)  NOT NULL,
    start_date       date,
    target_end_date  date,
    owner_id         uuid         NOT NULL,
    archived_at      timestamptz,
    archived_by      uuid,
    created_at       timestamptz  NOT NULL,
    updated_at       timestamptz  NOT NULL,
    version          bigint       NOT NULL DEFAULT 0,

    CONSTRAINT pk_projects PRIMARY KEY (id),
    CONSTRAINT ck_projects_name_not_blank
        CHECK (length(btrim(name)) > 0),
    CONSTRAINT ck_projects_status
        CHECK (status IN ('PLANNING', 'ACTIVE', 'ON_HOLD', 'COMPLETED', 'CANCELLED')),
    CONSTRAINT ck_projects_priority
        CHECK (priority IN ('LOW', 'MEDIUM', 'HIGH', 'CRITICAL')),
    CONSTRAINT ck_projects_dates_ordered
        CHECK (start_date IS NULL OR target_end_date IS NULL OR target_end_date >= start_date),
    -- Archival is a single fact: both columns are set together or neither is.
    CONSTRAINT ck_projects_archive_consistent
        CHECK ((archived_at IS NULL) = (archived_by IS NULL))
);

COMMENT ON TABLE projects IS
    'Projects owned by this module. Deletion is a soft archive: archived_at is set and the row is retained.';
COMMENT ON COLUMN projects.organization_id IS
    'Opaque reference to the external Organization Management module. Intentionally has no foreign key.';
COMMENT ON COLUMN projects.owner_id IS
    'Opaque reference to the external User Management module. Intentionally has no foreign key.';

CREATE INDEX idx_projects_organization ON projects (organization_id);
CREATE INDEX idx_projects_owner        ON projects (owner_id);

-- Listing live projects for a tenant is the most common read; the partial index keeps
-- archived rows out of it entirely.
CREATE INDEX idx_projects_active
    ON projects (organization_id, status)
    WHERE archived_at IS NULL;

--------------------------------------------------------------------------------
-- project_members
--------------------------------------------------------------------------------
CREATE TABLE project_members (
    id          uuid        NOT NULL,
    project_id  uuid        NOT NULL,
    user_id     uuid        NOT NULL,
    role        varchar(20) NOT NULL,
    joined_at   timestamptz NOT NULL,
    created_at  timestamptz NOT NULL,
    updated_at  timestamptz NOT NULL,
    version     bigint      NOT NULL DEFAULT 0,

    CONSTRAINT pk_project_members PRIMARY KEY (id),
    CONSTRAINT fk_project_members_project
        FOREIGN KEY (project_id) REFERENCES projects (id) ON DELETE CASCADE,
    CONSTRAINT uq_project_members_project_user
        UNIQUE (project_id, user_id),
    CONSTRAINT ck_project_members_role
        CHECK (role IN ('OWNER', 'MANAGER', 'MEMBER', 'VIEWER'))
);

COMMENT ON TABLE project_members IS
    'Project-level membership and role. Roles are scoped to one project and are unrelated to any company-wide role.';
COMMENT ON COLUMN project_members.user_id IS
    'Opaque reference to the external User Management module. Intentionally has no foreign key.';

-- Answers "which projects does this user belong to", the entry point for most listings.
CREATE INDEX idx_project_members_user ON project_members (user_id);

--------------------------------------------------------------------------------
-- project_activity
--------------------------------------------------------------------------------
CREATE TABLE project_activity (
    id             uuid         NOT NULL,
    project_id     uuid         NOT NULL,
    activity_type  varchar(60)  NOT NULL,
    actor_type     varchar(20)  NOT NULL,
    actor_id       uuid,
    summary        varchar(500) NOT NULL,
    payload        jsonb,
    correlation_id varchar(100),
    occurred_at    timestamptz  NOT NULL,

    CONSTRAINT pk_project_activity PRIMARY KEY (id),
    -- RESTRICT, not CASCADE: the audit trail must not be removable by deleting a project.
    -- Deletion is a soft archive, so this never blocks normal operation.
    CONSTRAINT fk_project_activity_project
        FOREIGN KEY (project_id) REFERENCES projects (id) ON DELETE RESTRICT,
    CONSTRAINT ck_project_activity_actor_type
        CHECK (actor_type IN ('HUMAN', 'AI', 'AUTOMATION', 'SYSTEM')),
    -- A human or AI action must name its actor; only the system may act anonymously.
    CONSTRAINT ck_project_activity_actor_present
        CHECK (actor_type = 'SYSTEM' OR actor_id IS NOT NULL)
);

COMMENT ON TABLE project_activity IS
    'Append-only audit trail. Terminates the AI action flow and records human, AI and automation actors alike.';

-- The activity feed reads one project newest-first.
CREATE INDEX idx_project_activity_project ON project_activity (project_id, occurred_at DESC);
CREATE INDEX idx_project_activity_actor   ON project_activity (actor_id);

--------------------------------------------------------------------------------
-- outbox
--------------------------------------------------------------------------------
CREATE TABLE outbox (
    id             uuid        NOT NULL,
    aggregate_type varchar(60) NOT NULL,
    aggregate_id   uuid        NOT NULL,
    event_type     varchar(80) NOT NULL,
    payload        jsonb       NOT NULL,
    correlation_id varchar(100),
    actor_type     varchar(20),
    actor_id       uuid,
    schema_version integer     NOT NULL DEFAULT 1,
    occurred_at    timestamptz NOT NULL,
    published_at   timestamptz,
    attempt_count  integer     NOT NULL DEFAULT 0,
    last_error     text,

    CONSTRAINT pk_outbox PRIMARY KEY (id),
    CONSTRAINT ck_outbox_attempt_count CHECK (attempt_count >= 0)
);

COMMENT ON TABLE outbox IS
    'Transactional outbox. Event rows are written in the same transaction as the state change they describe, so an event is never published for a change that rolled back. No foreign key: the outbox spans every aggregate type.';

-- The publisher polls only unpublished rows; the partial index stays small no matter how
-- much history accumulates.
CREATE INDEX idx_outbox_unpublished
    ON outbox (occurred_at)
    WHERE published_at IS NULL;
