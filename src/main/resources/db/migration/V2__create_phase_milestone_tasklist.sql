-- P1 Work Management, first slice: Phase, Milestone, Task List.
--
-- All three belong to a project (project_id, required). Phase groups milestones and task
-- lists, but phases are optional: phase_id on milestones and task_lists is nullable, so both
-- can attach directly to a project with no phase at all.

--------------------------------------------------------------------------------
-- phases
--------------------------------------------------------------------------------
CREATE TABLE phases (
    id               uuid         NOT NULL,
    project_id       uuid         NOT NULL,
    name             varchar(200) NOT NULL,
    description      text,
    start_date       date,
    end_date         date,
    archived_at      timestamptz,
    archived_by      uuid,
    created_at       timestamptz  NOT NULL,
    updated_at       timestamptz  NOT NULL,
    version          bigint       NOT NULL DEFAULT 0,

    CONSTRAINT pk_phases PRIMARY KEY (id),
    CONSTRAINT fk_phases_project
        FOREIGN KEY (project_id) REFERENCES projects (id) ON DELETE CASCADE,
    CONSTRAINT ck_phases_name_not_blank
        CHECK (length(btrim(name)) > 0),
    CONSTRAINT ck_phases_dates_ordered
        CHECK (start_date IS NULL OR end_date IS NULL OR end_date >= start_date),
    CONSTRAINT ck_phases_archive_consistent
        CHECK ((archived_at IS NULL) = (archived_by IS NULL))
);

COMMENT ON TABLE phases IS
    'Optional groupings of a project''s work into a stretch of time. Deletion is a soft archive, consistent with projects.';

CREATE INDEX idx_phases_project ON phases (project_id);
CREATE INDEX idx_phases_active ON phases (project_id) WHERE archived_at IS NULL;

--------------------------------------------------------------------------------
-- milestones
--------------------------------------------------------------------------------
CREATE TABLE milestones (
    id               uuid         NOT NULL,
    project_id       uuid         NOT NULL,
    phase_id         uuid,
    name             varchar(200) NOT NULL,
    description      text,
    due_date         date,
    status           varchar(20)  NOT NULL,
    archived_at      timestamptz,
    archived_by      uuid,
    created_at       timestamptz  NOT NULL,
    updated_at       timestamptz  NOT NULL,
    version          bigint       NOT NULL DEFAULT 0,

    CONSTRAINT pk_milestones PRIMARY KEY (id),
    CONSTRAINT fk_milestones_project
        FOREIGN KEY (project_id) REFERENCES projects (id) ON DELETE CASCADE,
    -- SET NULL, not CASCADE/RESTRICT: a milestone can exist with no phase at all (the phase
    -- grouping is optional), so removing a phase must not destroy or block removal of the
    -- milestones that referenced it.
    CONSTRAINT fk_milestones_phase
        FOREIGN KEY (phase_id) REFERENCES phases (id) ON DELETE SET NULL,
    CONSTRAINT ck_milestones_name_not_blank
        CHECK (length(btrim(name)) > 0),
    CONSTRAINT ck_milestones_status
        CHECK (status IN ('PENDING', 'AT_RISK', 'COMPLETED')),
    CONSTRAINT ck_milestones_archive_consistent
        CHECK ((archived_at IS NULL) = (archived_by IS NULL))
);

COMMENT ON TABLE milestones IS
    'Target dates a project is working towards. Deletion is a soft archive, consistent with projects.';

CREATE INDEX idx_milestones_project ON milestones (project_id);
CREATE INDEX idx_milestones_phase ON milestones (phase_id);
CREATE INDEX idx_milestones_active ON milestones (project_id) WHERE archived_at IS NULL;

--------------------------------------------------------------------------------
-- task_lists
--------------------------------------------------------------------------------
CREATE TABLE task_lists (
    id               uuid         NOT NULL,
    project_id       uuid         NOT NULL,
    phase_id         uuid,
    name             varchar(200) NOT NULL,
    description      text,
    archived_at      timestamptz,
    archived_by      uuid,
    created_at       timestamptz  NOT NULL,
    updated_at       timestamptz  NOT NULL,
    version          bigint       NOT NULL DEFAULT 0,

    CONSTRAINT pk_task_lists PRIMARY KEY (id),
    CONSTRAINT fk_task_lists_project
        FOREIGN KEY (project_id) REFERENCES projects (id) ON DELETE CASCADE,
    CONSTRAINT fk_task_lists_phase
        FOREIGN KEY (phase_id) REFERENCES phases (id) ON DELETE SET NULL,
    CONSTRAINT ck_task_lists_name_not_blank
        CHECK (length(btrim(name)) > 0),
    CONSTRAINT ck_task_lists_archive_consistent
        CHECK ((archived_at IS NULL) = (archived_by IS NULL))
);

COMMENT ON TABLE task_lists IS
    'Named groupings of tasks within a project. Deletion is a soft archive, consistent with projects. Tasks themselves are a later slice.';

CREATE INDEX idx_task_lists_project ON task_lists (project_id);
CREATE INDEX idx_task_lists_phase ON task_lists (phase_id);
CREATE INDEX idx_task_lists_active ON task_lists (project_id) WHERE archived_at IS NULL;
