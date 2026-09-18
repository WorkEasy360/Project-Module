-- Risk: a potential problem that could affect a project.
--
-- Not detailed beyond its existence (01-SPEC.md, 03-DATABASE.md) and its three events
-- (05-EVENTS.md: risk.created, risk.updated, risk.resolved). This schema - project-scoped, with
-- name/description/priority/status - is the minimal shape consistent with every other work
-- entity's conventions, not a documented requirement. priority reuses the same values as
-- projects.priority (V1).

CREATE TABLE risks (
    id               uuid         NOT NULL,
    project_id       uuid         NOT NULL,
    name             varchar(200) NOT NULL,
    description      text,
    priority         varchar(20)  NOT NULL,
    status           varchar(20)  NOT NULL,
    archived_at      timestamptz,
    archived_by      uuid,
    created_at       timestamptz  NOT NULL,
    updated_at       timestamptz  NOT NULL,
    version          bigint       NOT NULL DEFAULT 0,

    CONSTRAINT pk_risks PRIMARY KEY (id),
    CONSTRAINT fk_risks_project
        FOREIGN KEY (project_id) REFERENCES projects (id) ON DELETE CASCADE,
    CONSTRAINT ck_risks_name_not_blank
        CHECK (length(btrim(name)) > 0),
    CONSTRAINT ck_risks_priority
        CHECK (priority IN ('LOW', 'MEDIUM', 'HIGH', 'CRITICAL')),
    CONSTRAINT ck_risks_status
        CHECK (status IN ('OPEN', 'RESOLVED')),
    CONSTRAINT ck_risks_archive_consistent
        CHECK ((archived_at IS NULL) = (archived_by IS NULL))
);

COMMENT ON TABLE risks IS
    'Potential problems that could affect a project. Deletion is a soft archive, consistent with projects.';

CREATE INDEX idx_risks_project ON risks (project_id);
CREATE INDEX idx_risks_active ON risks (project_id) WHERE archived_at IS NULL;
