-- Decision: a choice that was made about a project, recorded for later reference.
--
-- Specified in docs/project/09-DECISION-SPEC.md (approved), since 01-SPEC.md/03-DATABASE.md
-- document Decision only as a bare entity name. Per the approved specification: no status
-- column, no priority column, no events/outbox integration. decided_by is optional, opaque
-- (no FK), the same convention tasks.assignee_id already uses.

CREATE TABLE decisions (
    id               uuid         NOT NULL,
    project_id       uuid         NOT NULL,
    name             varchar(200) NOT NULL,
    description      text,
    decided_by       uuid,
    archived_at      timestamptz,
    archived_by      uuid,
    created_at       timestamptz  NOT NULL,
    updated_at       timestamptz  NOT NULL,
    version          bigint       NOT NULL DEFAULT 0,

    CONSTRAINT pk_decisions PRIMARY KEY (id),
    CONSTRAINT fk_decisions_project
        FOREIGN KEY (project_id) REFERENCES projects (id) ON DELETE CASCADE,
    CONSTRAINT ck_decisions_name_not_blank
        CHECK (length(btrim(name)) > 0),
    CONSTRAINT ck_decisions_archive_consistent
        CHECK ((archived_at IS NULL) = (archived_by IS NULL))
);

COMMENT ON TABLE decisions IS
    'Choices made about a project, recorded for later reference. Deletion is a soft archive, consistent with projects.';
COMMENT ON COLUMN decisions.decided_by IS
    'Opaque reference to the external User Management module. Intentionally has no foreign key.';

CREATE INDEX idx_decisions_project ON decisions (project_id);
CREATE INDEX idx_decisions_active ON decisions (project_id) WHERE archived_at IS NULL;
