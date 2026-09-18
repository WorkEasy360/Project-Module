-- ProjectComment: a short, unthreaded remark left on a project.
--
-- Specified in docs/project/10-COMMENT-SPEC.md (approved), since 01-SPEC.md/03-DATABASE.md
-- document ProjectComment only as a bare entity name. Per the approved specification: comments
-- attach only to a project (no target-entity polymorphism), no status column, no
-- parent_comment_id (no threading), no events/outbox integration. author_id is opaque (no FK),
-- the same convention tasks.assignee_id / decisions.decided_by already use.

CREATE TABLE project_comments (
    id               uuid         NOT NULL,
    project_id       uuid         NOT NULL,
    author_id        uuid         NOT NULL,
    body             text         NOT NULL,
    archived_at      timestamptz,
    archived_by      uuid,
    created_at       timestamptz  NOT NULL,
    updated_at       timestamptz  NOT NULL,
    version          bigint       NOT NULL DEFAULT 0,

    CONSTRAINT pk_project_comments PRIMARY KEY (id),
    CONSTRAINT fk_project_comments_project
        FOREIGN KEY (project_id) REFERENCES projects (id) ON DELETE CASCADE,
    CONSTRAINT ck_project_comments_body_not_blank
        CHECK (length(btrim(body)) > 0),
    CONSTRAINT ck_project_comments_archive_consistent
        CHECK ((archived_at IS NULL) = (archived_by IS NULL))
);

COMMENT ON TABLE project_comments IS
    'Short, unthreaded remarks left on a project. Deletion is a soft archive, consistent with projects.';
COMMENT ON COLUMN project_comments.author_id IS
    'Opaque reference to the external User Management module. Intentionally has no foreign key.';

CREATE INDEX idx_project_comments_project ON project_comments (project_id);
CREATE INDEX idx_project_comments_active ON project_comments (project_id) WHERE archived_at IS NULL;
