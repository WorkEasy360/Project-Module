-- ProjectCustomField: a named, typed, project-scoped custom attribute - one combined field
-- definition and its current value in a single row.
--
-- Specified in docs/project/11-CUSTOMFIELD-SPEC.md (approved), since 01-SPEC.md/03-DATABASE.md
-- document ProjectCustomField only as a bare entity name. Per the approved specification: no
-- reusable field-definition registry shared across projects, custom fields attach only to a
-- project (not to Task/Risk/Issue/Decision), value_type is immutable after creation, no
-- events/outbox integration, and active custom-field names must be unique within a project.

CREATE TABLE project_custom_fields (
    id               uuid         NOT NULL,
    project_id       uuid         NOT NULL,
    name             varchar(200) NOT NULL,
    value_type       varchar(20)  NOT NULL,
    value            text         NOT NULL,
    archived_at      timestamptz,
    archived_by      uuid,
    created_at       timestamptz  NOT NULL,
    updated_at       timestamptz  NOT NULL,
    version          bigint       NOT NULL DEFAULT 0,

    CONSTRAINT pk_project_custom_fields PRIMARY KEY (id),
    CONSTRAINT fk_project_custom_fields_project
        FOREIGN KEY (project_id) REFERENCES projects (id) ON DELETE CASCADE,
    CONSTRAINT ck_project_custom_fields_name_not_blank
        CHECK (length(btrim(name)) > 0),
    CONSTRAINT ck_project_custom_fields_value_not_blank
        CHECK (length(btrim(value)) > 0),
    CONSTRAINT ck_project_custom_fields_value_type
        CHECK (value_type IN ('TEXT', 'NUMBER', 'DATE', 'BOOLEAN')),
    CONSTRAINT ck_project_custom_fields_archive_consistent
        CHECK ((archived_at IS NULL) = (archived_by IS NULL))
);

COMMENT ON TABLE project_custom_fields IS
    'Named, typed, project-scoped custom attributes - one combined definition and current value per row. Deletion is a soft archive, consistent with projects.';

CREATE INDEX idx_project_custom_fields_project ON project_custom_fields (project_id);
CREATE INDEX idx_project_custom_fields_active ON project_custom_fields (project_id) WHERE archived_at IS NULL;

-- Active-row uniqueness cannot be expressed as a table-level UNIQUE constraint in PostgreSQL (a
-- table CONSTRAINT ... UNIQUE accepts no WHERE predicate); it requires a partial unique index.
CREATE UNIQUE INDEX uq_project_custom_fields_project_name_active
    ON project_custom_fields (project_id, name)
    WHERE archived_at IS NULL;
