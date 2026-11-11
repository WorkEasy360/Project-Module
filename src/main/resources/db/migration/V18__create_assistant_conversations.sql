-- Conversations with the in-app assistant. Each belongs to one person in one workspace, and holds
-- the exchange with the model so it can continue, plus any change waiting for the person's approval.

CREATE TABLE assistant_conversations (
    id              uuid        NOT NULL,
    user_id         uuid        NOT NULL,
    organization_id uuid        NOT NULL,
    messages        text        NOT NULL,
    pending_action  text,
    created_at      timestamptz NOT NULL,
    updated_at      timestamptz NOT NULL,
    version         bigint      NOT NULL DEFAULT 0,

    CONSTRAINT pk_assistant_conversations PRIMARY KEY (id)
);

COMMENT ON COLUMN assistant_conversations.messages IS
    'The exchange with the model as the Messages API takes it, so the conversation continues exactly.';
COMMENT ON COLUMN assistant_conversations.pending_action IS
    'Changes the assistant proposed and is waiting for the person to approve or decline.';

CREATE INDEX idx_assistant_conversations_user ON assistant_conversations (user_id, updated_at DESC);
