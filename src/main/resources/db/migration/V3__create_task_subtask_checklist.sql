-- P1 Work Management, second slice: Task, Subtask, Checklist.
--
-- Tasks belong to a project (project_id, required) only - docs/project/03-DATABASE.md does not
-- associate a Task with a Task List. Subtasks belong to a task; checklist lines belong to a
-- task and optionally to one of its subtasks, mirroring how milestones/task_lists optionally
-- scope to a phase (V2).

--------------------------------------------------------------------------------
-- tasks
--------------------------------------------------------------------------------
CREATE TABLE tasks (
    id               uuid         NOT NULL,
    project_id       uuid         NOT NULL,
    name             varchar(200) NOT NULL,
    description      text,
    due_date         date,
    assignee_id      uuid,
    status           varchar(20)  NOT NULL,
    archived_at      timestamptz,
    archived_by      uuid,
    created_at       timestamptz  NOT NULL,
    updated_at       timestamptz  NOT NULL,
    version          bigint       NOT NULL DEFAULT 0,

    CONSTRAINT pk_tasks PRIMARY KEY (id),
    CONSTRAINT fk_tasks_project
        FOREIGN KEY (project_id) REFERENCES projects (id) ON DELETE CASCADE,
    CONSTRAINT ck_tasks_name_not_blank
        CHECK (length(btrim(name)) > 0),
    CONSTRAINT ck_tasks_status
        CHECK (status IN ('TODO', 'BLOCKED', 'OVERDUE', 'COMPLETED')),
    CONSTRAINT ck_tasks_archive_consistent
        CHECK ((archived_at IS NULL) = (archived_by IS NULL))
);

COMMENT ON TABLE tasks IS
    'Units of work within a project. Deletion is a soft archive, consistent with projects.';
COMMENT ON COLUMN tasks.assignee_id IS
    'Opaque reference to the external User Management module. Intentionally has no foreign key.';

CREATE INDEX idx_tasks_project ON tasks (project_id);
CREATE INDEX idx_tasks_active ON tasks (project_id) WHERE archived_at IS NULL;

--------------------------------------------------------------------------------
-- subtasks
--------------------------------------------------------------------------------
CREATE TABLE subtasks (
    id               uuid         NOT NULL,
    task_id          uuid         NOT NULL,
    name             varchar(200) NOT NULL,
    description      text,
    completed        boolean      NOT NULL DEFAULT false,
    archived_at      timestamptz,
    archived_by      uuid,
    created_at       timestamptz  NOT NULL,
    updated_at       timestamptz  NOT NULL,
    version          bigint       NOT NULL DEFAULT 0,

    CONSTRAINT pk_subtasks PRIMARY KEY (id),
    CONSTRAINT fk_subtasks_task
        FOREIGN KEY (task_id) REFERENCES tasks (id) ON DELETE CASCADE,
    CONSTRAINT ck_subtasks_name_not_blank
        CHECK (length(btrim(name)) > 0),
    CONSTRAINT ck_subtasks_archive_consistent
        CHECK ((archived_at IS NULL) = (archived_by IS NULL))
);

COMMENT ON TABLE subtasks IS
    'Smaller units of work within a task. Deletion is a soft archive, consistent with projects.';

CREATE INDEX idx_subtasks_task ON subtasks (task_id);
CREATE INDEX idx_subtasks_active ON subtasks (task_id) WHERE archived_at IS NULL;

--------------------------------------------------------------------------------
-- checklists
--------------------------------------------------------------------------------
CREATE TABLE checklists (
    id               uuid         NOT NULL,
    task_id          uuid         NOT NULL,
    subtask_id       uuid,
    text             varchar(500) NOT NULL,
    checked          boolean      NOT NULL DEFAULT false,
    archived_at      timestamptz,
    archived_by      uuid,
    created_at       timestamptz  NOT NULL,
    updated_at       timestamptz  NOT NULL,
    version          bigint       NOT NULL DEFAULT 0,

    CONSTRAINT pk_checklists PRIMARY KEY (id),
    CONSTRAINT fk_checklists_task
        FOREIGN KEY (task_id) REFERENCES tasks (id) ON DELETE CASCADE,
    -- SET NULL, not CASCADE/RESTRICT: a checklist line can exist with no subtask at all (the
    -- subtask scoping is optional), so removing a subtask must not destroy or block removal of
    -- the checklist lines that referenced it.
    CONSTRAINT fk_checklists_subtask
        FOREIGN KEY (subtask_id) REFERENCES subtasks (id) ON DELETE SET NULL,
    CONSTRAINT ck_checklists_text_not_blank
        CHECK (length(btrim(text)) > 0),
    CONSTRAINT ck_checklists_archive_consistent
        CHECK ((archived_at IS NULL) = (archived_by IS NULL))
);

COMMENT ON TABLE checklists IS
    'Single checkable lines belonging to a task, optionally scoped to one of its subtasks. Deletion is a soft archive, consistent with projects.';

CREATE INDEX idx_checklists_task ON checklists (task_id);
CREATE INDEX idx_checklists_subtask ON checklists (subtask_id);
CREATE INDEX idx_checklists_active ON checklists (task_id) WHERE archived_at IS NULL;
