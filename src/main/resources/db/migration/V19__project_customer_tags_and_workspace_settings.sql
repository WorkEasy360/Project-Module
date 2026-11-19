-- Projects record who the work is for and carry short labels, as on the projects list; and each
-- workspace has a display name. Additive only: older application versions ignore these.

ALTER TABLE projects
    ADD COLUMN customer_contact varchar(200);

COMMENT ON COLUMN projects.customer_contact IS
    'Who the project is for: a customer, client or contact, as free text. Optional.';

CREATE TABLE project_tags (
    project_id uuid        NOT NULL,
    tag        varchar(40) NOT NULL,

    CONSTRAINT pk_project_tags PRIMARY KEY (project_id, tag),
    CONSTRAINT fk_project_tags_project FOREIGN KEY (project_id) REFERENCES projects (id) ON DELETE CASCADE,
    CONSTRAINT ck_project_tags_not_blank CHECK (length(btrim(tag)) > 0)
);

-- id is the workspace's organization id: one row per workspace.
CREATE TABLE workspace_settings (
    id         uuid         NOT NULL,
    name       varchar(200) NOT NULL,
    created_at timestamptz  NOT NULL,
    updated_at timestamptz  NOT NULL,
    version    bigint       NOT NULL DEFAULT 0,

    CONSTRAINT pk_workspace_settings PRIMARY KEY (id),
    CONSTRAINT ck_workspace_settings_name_not_blank CHECK (length(btrim(name)) > 0)
);

COMMENT ON TABLE workspace_settings IS 'Per-workspace display settings, such as the company name shown in the header.';
