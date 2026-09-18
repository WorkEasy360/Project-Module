-- ProjectAutomation: a deterministic, rule-based automation (trigger -> action).
-- AutomationRun: the append-only execution history of a ProjectAutomation.
--
-- Specified in docs/project/28-AUTOMATION-SPEC.md (approved). V1 actions are scoped to the two
-- existing, non-mutating integration ports (NotificationPort/ChatPort) — no condition
-- sub-language, no broader mutating-action allowlist (see the spec's §1/§3).

CREATE TABLE project_automations (
    id                        uuid         NOT NULL,
    project_id                uuid         NOT NULL,
    name                      varchar(200) NOT NULL,
    description               text,
    trigger_event             varchar(100) NOT NULL,
    action_type               varchar(20)  NOT NULL,
    action_recipient_id       uuid,
    action_channel_reference  text,
    action_message            text         NOT NULL,
    enabled                   boolean      NOT NULL DEFAULT true,
    archived_at               timestamptz,
    archived_by               uuid,
    created_at                timestamptz  NOT NULL,
    updated_at                timestamptz  NOT NULL,
    version                   bigint       NOT NULL DEFAULT 0,

    CONSTRAINT pk_project_automations PRIMARY KEY (id),
    CONSTRAINT fk_project_automations_project
        FOREIGN KEY (project_id) REFERENCES projects (id) ON DELETE CASCADE,
    CONSTRAINT ck_project_automations_action_type
        CHECK (action_type IN ('NOTIFY', 'CHAT_MESSAGE')),
    CONSTRAINT ck_project_automations_name_not_blank
        CHECK (length(btrim(name)) > 0),
    CONSTRAINT ck_project_automations_trigger_event_not_blank
        CHECK (length(btrim(trigger_event)) > 0),
    CONSTRAINT ck_project_automations_message_not_blank
        CHECK (length(btrim(action_message)) > 0),
    CONSTRAINT ck_project_automations_notify_recipient
        CHECK (action_type <> 'NOTIFY' OR action_recipient_id IS NOT NULL),
    CONSTRAINT ck_project_automations_chat_channel
        CHECK (action_type <> 'CHAT_MESSAGE' OR action_channel_reference IS NOT NULL),
    CONSTRAINT ck_project_automations_archive_consistent
        CHECK ((archived_at IS NULL) = (archived_by IS NULL))
);

COMMENT ON COLUMN project_automations.action_recipient_id IS
    'Opaque reference to the external User Management module. Intentionally has no foreign key.';

CREATE INDEX idx_project_automations_project ON project_automations (project_id);
CREATE INDEX idx_project_automations_dispatch
    ON project_automations (project_id, trigger_event)
    WHERE enabled AND archived_at IS NULL;

CREATE TABLE automation_runs (
    id             uuid        NOT NULL,
    automation_id  uuid        NOT NULL,
    project_id     uuid        NOT NULL,
    trigger_event  varchar(100) NOT NULL,
    status         varchar(20) NOT NULL,
    error_message  text,
    executed_at    timestamptz NOT NULL,

    CONSTRAINT pk_automation_runs PRIMARY KEY (id),
    CONSTRAINT fk_automation_runs_automation
        FOREIGN KEY (automation_id) REFERENCES project_automations (id) ON DELETE CASCADE,
    CONSTRAINT ck_automation_runs_status
        CHECK (status IN ('SUCCEEDED', 'FAILED'))
);

COMMENT ON TABLE automation_runs IS
    'Append-only execution history. No update/delete path: an execution record that can be edited after the fact is not an audit record.';

CREATE INDEX idx_automation_runs_automation ON automation_runs (automation_id);
