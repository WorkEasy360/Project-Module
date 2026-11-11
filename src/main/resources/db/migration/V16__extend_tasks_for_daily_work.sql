-- Everyday task fields: an in-progress status, priority, start date, estimate, recurrence and tags.
-- Existing tasks keep their status and become medium priority, non-recurring.

ALTER TABLE tasks DROP CONSTRAINT ck_tasks_status;
ALTER TABLE tasks ADD CONSTRAINT ck_tasks_status
    CHECK (status IN ('TODO', 'IN_PROGRESS', 'BLOCKED', 'OVERDUE', 'COMPLETED'));

ALTER TABLE tasks
    ADD COLUMN priority         varchar(20) NOT NULL DEFAULT 'MEDIUM',
    ADD COLUMN start_date       date,
    ADD COLUMN estimate_minutes integer,
    ADD COLUMN recurrence       varchar(20) NOT NULL DEFAULT 'NONE',
    ADD CONSTRAINT ck_tasks_priority CHECK (priority IN ('LOW', 'MEDIUM', 'HIGH', 'CRITICAL')),
    ADD CONSTRAINT ck_tasks_recurrence CHECK (recurrence IN ('NONE', 'DAILY', 'WEEKLY', 'MONTHLY')),
    ADD CONSTRAINT ck_tasks_estimate CHECK (estimate_minutes IS NULL OR estimate_minutes >= 0),
    ADD CONSTRAINT ck_tasks_dates_ordered CHECK (start_date IS NULL OR due_date IS NULL OR due_date >= start_date);

CREATE TABLE task_tags (
    task_id uuid        NOT NULL,
    tag     varchar(40) NOT NULL,

    CONSTRAINT pk_task_tags PRIMARY KEY (task_id, tag),
    CONSTRAINT fk_task_tags_task FOREIGN KEY (task_id) REFERENCES tasks (id) ON DELETE CASCADE,
    CONSTRAINT ck_task_tags_not_blank CHECK (length(btrim(tag)) > 0)
);
