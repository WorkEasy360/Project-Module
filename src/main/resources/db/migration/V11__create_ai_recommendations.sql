-- AIRecommendation: an AI-generated suggestion about a project, recorded for human review.
--
-- Specified in docs/project/26-AI-RECOMMENDATIONS-SPEC.md (approved), per the boundary
-- established in docs/project/25-AI-ARCHITECTURE-SPEC.md. Suggest-only: accepting a
-- recommendation never automatically applies it (no mutation is triggered by this table).
-- No archive columns: a recommendation is a point-in-time suggestion, not an ongoing work item;
-- REJECTED already serves the role archiving would.

CREATE TABLE ai_recommendations (
    id             uuid         NOT NULL,
    project_id     uuid         NOT NULL,
    type           varchar(30)  NOT NULL,
    resource_id    uuid,
    question       text,
    title          varchar(500) NOT NULL,
    rationale      text         NOT NULL,
    payload        text,
    status         varchar(20)  NOT NULL,
    requested_by   uuid         NOT NULL,
    responded_by   uuid,
    responded_at   timestamptz,
    created_at     timestamptz  NOT NULL,
    updated_at     timestamptz  NOT NULL,
    version        bigint       NOT NULL DEFAULT 0,

    CONSTRAINT pk_ai_recommendations PRIMARY KEY (id),
    CONSTRAINT fk_ai_recommendations_project
        FOREIGN KEY (project_id) REFERENCES projects (id) ON DELETE CASCADE,
    CONSTRAINT ck_ai_recommendations_type
        CHECK (type IN ('TASK_BREAKDOWN', 'TASK_IMPROVEMENT', 'PROJECT_SUMMARY', 'RISK_ANALYSIS', 'WHAT_IF')),
    CONSTRAINT ck_ai_recommendations_status
        CHECK (status IN ('PENDING', 'ACCEPTED', 'REJECTED', 'EXPIRED')),
    CONSTRAINT ck_ai_recommendations_title_not_blank
        CHECK (length(btrim(title)) > 0),
    CONSTRAINT ck_ai_recommendations_rationale_not_blank
        CHECK (length(btrim(rationale)) > 0),
    CONSTRAINT ck_ai_recommendations_response_consistent
        CHECK ((responded_by IS NULL) = (responded_at IS NULL))
);

COMMENT ON COLUMN ai_recommendations.resource_id IS
    'The specific Task this concerns, for TASK_BREAKDOWN/TASK_IMPROVEMENT; NULL for project-wide types. No foreign key: it is polymorphic across entity types depending on future recommendation types.';
COMMENT ON COLUMN ai_recommendations.requested_by IS
    'Opaque reference to the external User Management module. Intentionally has no foreign key.';
COMMENT ON COLUMN ai_recommendations.responded_by IS
    'Opaque reference to the external User Management module. Intentionally has no foreign key.';

CREATE INDEX idx_ai_recommendations_project_status ON ai_recommendations (project_id, status);
