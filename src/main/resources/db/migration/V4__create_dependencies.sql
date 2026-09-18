-- P1 Work Management, third slice: Dependencies.
--
-- A dependency links two tasks: the dependent task cannot start/complete until its prerequisite
-- does. Both task ids are required and immutable; the owning project is not stored on this table
-- because it is always resolved through the dependent task (see DependencyApplicationService).

CREATE TABLE dependencies (
    id                    uuid        NOT NULL,
    dependent_task_id     uuid        NOT NULL,
    prerequisite_task_id  uuid        NOT NULL,
    broken                boolean     NOT NULL DEFAULT false,
    archived_at           timestamptz,
    archived_by           uuid,
    created_at            timestamptz NOT NULL,
    updated_at            timestamptz NOT NULL,
    version               bigint      NOT NULL DEFAULT 0,

    CONSTRAINT pk_dependencies PRIMARY KEY (id),
    CONSTRAINT fk_dependencies_dependent_task
        FOREIGN KEY (dependent_task_id) REFERENCES tasks (id) ON DELETE CASCADE,
    CONSTRAINT fk_dependencies_prerequisite_task
        FOREIGN KEY (prerequisite_task_id) REFERENCES tasks (id) ON DELETE CASCADE,
    CONSTRAINT ck_dependencies_not_self
        CHECK (dependent_task_id <> prerequisite_task_id),
    CONSTRAINT uq_dependencies_pair
        UNIQUE (dependent_task_id, prerequisite_task_id),
    CONSTRAINT ck_dependencies_archive_consistent
        CHECK ((archived_at IS NULL) = (archived_by IS NULL))
);

COMMENT ON TABLE dependencies IS
    'Directed dependency between two tasks (dependent depends on prerequisite). Deletion is a soft archive, consistent with projects.';

CREATE INDEX idx_dependencies_dependent ON dependencies (dependent_task_id);
CREATE INDEX idx_dependencies_prerequisite ON dependencies (prerequisite_task_id);
CREATE INDEX idx_dependencies_active ON dependencies (dependent_task_id) WHERE archived_at IS NULL;
