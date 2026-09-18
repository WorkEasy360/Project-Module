-- ProjectTemplate: a reusable, organization-scoped blueprint for creating a Project.
--
-- Specified in docs/project/12-TEMPLATE-SPEC.md (approved), since 01-SPEC.md/03-DATABASE.md
-- document ProjectTemplate only as a bare entity name. Per the approved specification: V1 is a
-- metadata-only project-default preset (name, description, default_priority) - no structural
-- snapshot of phases/milestones/tasks (explicitly out of scope). Organization-scoped, the same
-- convention projects.organization_id already uses (opaque, no FK). No events/outbox
-- integration. Active template names must be unique per organization.

CREATE TABLE project_templates (
    id                 uuid         NOT NULL,
    organization_id    uuid         NOT NULL,
    name               varchar(200) NOT NULL,
    description        text,
    default_priority   varchar(20),
    archived_at        timestamptz,
    archived_by        uuid,
    created_at         timestamptz  NOT NULL,
    updated_at         timestamptz  NOT NULL,
    version            bigint       NOT NULL DEFAULT 0,

    CONSTRAINT pk_project_templates PRIMARY KEY (id),
    CONSTRAINT ck_project_templates_name_not_blank
        CHECK (length(btrim(name)) > 0),
    CONSTRAINT ck_project_templates_default_priority
        CHECK (default_priority IS NULL OR default_priority IN ('LOW', 'MEDIUM', 'HIGH', 'CRITICAL')),
    CONSTRAINT ck_project_templates_archive_consistent
        CHECK ((archived_at IS NULL) = (archived_by IS NULL))
    -- No FK on organization_id: opaque reference, same as projects.organization_id.
);

COMMENT ON TABLE project_templates IS
    'Reusable, organization-scoped project-default presets. Deletion is a soft archive, consistent with projects.';
COMMENT ON COLUMN project_templates.organization_id IS
    'Opaque reference to the external Organization Management module. Intentionally has no foreign key.';

CREATE INDEX idx_project_templates_organization ON project_templates (organization_id);
CREATE INDEX idx_project_templates_active ON project_templates (organization_id) WHERE archived_at IS NULL;

-- Active-row uniqueness cannot be expressed as a table-level UNIQUE constraint in PostgreSQL (a
-- table CONSTRAINT ... UNIQUE accepts no WHERE predicate); it requires a partial unique index.
CREATE UNIQUE INDEX uq_project_templates_org_name_active
    ON project_templates (organization_id, name)
    WHERE archived_at IS NULL;
