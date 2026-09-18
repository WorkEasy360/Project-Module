-- Issue: a problem that has already occurred and affects a project.
--
-- Not detailed beyond its existence (01-SPEC.md, 03-DATABASE.md), and unlike Risk (V5),
-- 05-EVENTS.md documents no Issue events at all - no status column, since the only precedent
-- for a status transition (Risk.resolve()) exists specifically to raise the event that documents
-- it. Fields (name/description/priority) mirror the minimal shape used for risks (V5), for
-- consistency. priority reuses the same values as projects.priority (V1) / risks.priority (V5).

CREATE TABLE issues (
    id               uuid         NOT NULL,
    project_id       uuid         NOT NULL,
    name             varchar(200) NOT NULL,
    description      text,
    priority         varchar(20)  NOT NULL,
    archived_at      timestamptz,
    archived_by      uuid,
    created_at       timestamptz  NOT NULL,
    updated_at       timestamptz  NOT NULL,
    version          bigint       NOT NULL DEFAULT 0,

    CONSTRAINT pk_issues PRIMARY KEY (id),
    CONSTRAINT fk_issues_project
        FOREIGN KEY (project_id) REFERENCES projects (id) ON DELETE CASCADE,
    CONSTRAINT ck_issues_name_not_blank
        CHECK (length(btrim(name)) > 0),
    CONSTRAINT ck_issues_priority
        CHECK (priority IN ('LOW', 'MEDIUM', 'HIGH', 'CRITICAL')),
    CONSTRAINT ck_issues_archive_consistent
        CHECK ((archived_at IS NULL) = (archived_by IS NULL))
);

COMMENT ON TABLE issues IS
    'Problems that have already occurred and affect a project. Deletion is a soft archive, consistent with projects.';

CREATE INDEX idx_issues_project ON issues (project_id);
CREATE INDEX idx_issues_active ON issues (project_id) WHERE archived_at IS NULL;
