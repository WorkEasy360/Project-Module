-- Brings the stored data in line with the WorkEasy360 Operations V3 structure (12 Nov 2026).
--
-- Features outside V3 are removed completely, records included, by the product owner's decision:
-- phases, milestones, task lists, task dependencies, risks, issues, decisions, project-level
-- comments, project custom fields, project templates, time tracking (time entries and timers),
-- AI recommendations, subtasks, task followers and task recurrence. Automation keeps only
-- in-app notification rules on project and task events.
--
-- Values are narrowed to V3's sets:
--   task status     To Do, In Progress, Blocked, Done (TODO, IN_PROGRESS, BLOCKED, COMPLETED).
--                   Overdue is derived (past due and not done), so the stored OVERDUE status goes:
--                   each such task returns to the last open status its own history recorded,
--                   or To Do when there is none.
--   project status  Active, On Hold, Completed (+ the separate archived flag).
--                   PLANNING becomes ACTIVE. CANCELLED becomes ON_HOLD and the project is
--                   archived (by its owner, if not already archived), so it is kept, read only,
--                   under Archived Projects rather than shown as active work.
--   priority        Low, Normal, High (LOW, MEDIUM, HIGH). CRITICAL becomes HIGH.

--------------------------------------------------------------------------------
-- 1. Tables of removed features, in foreign-key-safe order
--------------------------------------------------------------------------------
DROP TABLE IF EXISTS dependencies;

ALTER TABLE checklists DROP COLUMN IF EXISTS subtask_id;
DROP TABLE IF EXISTS subtasks;

DROP TABLE IF EXISTS milestones;
DROP TABLE IF EXISTS task_lists;
DROP TABLE IF EXISTS phases;

DROP TABLE IF EXISTS risks;
DROP TABLE IF EXISTS issues;
DROP TABLE IF EXISTS decisions;
DROP TABLE IF EXISTS project_custom_fields;
DROP TABLE IF EXISTS project_templates;
DROP TABLE IF EXISTS ai_recommendations;

DROP TABLE IF EXISTS time_entries;
DROP TABLE IF EXISTS active_timers;
DROP TABLE IF EXISTS task_followers;

--------------------------------------------------------------------------------
-- 2. Comments belong to a task
--------------------------------------------------------------------------------
DELETE FROM project_comments WHERE task_id IS NULL;
ALTER TABLE project_comments ALTER COLUMN task_id SET NOT NULL;

--------------------------------------------------------------------------------
-- 3. Tasks: no stored OVERDUE status, no recurrence, three priorities
--------------------------------------------------------------------------------
UPDATE tasks t
SET status = COALESCE(
        (SELECT a.payload ->> 'status'
         FROM project_activity a
         WHERE a.project_id = t.project_id
           AND a.payload ->> 'taskId' = t.id::text
           AND a.payload ->> 'status' IN ('TODO', 'IN_PROGRESS', 'BLOCKED')
         ORDER BY a.occurred_at DESC
         LIMIT 1),
        'TODO'),
    version = t.version + 1
WHERE t.status = 'OVERDUE';

ALTER TABLE tasks DROP CONSTRAINT IF EXISTS ck_tasks_status;
ALTER TABLE tasks ADD CONSTRAINT ck_tasks_status
    CHECK (status IN ('TODO', 'IN_PROGRESS', 'BLOCKED', 'COMPLETED'));

ALTER TABLE tasks DROP COLUMN IF EXISTS recurrence;

UPDATE tasks SET priority = 'HIGH', version = version + 1 WHERE priority = 'CRITICAL';
ALTER TABLE tasks DROP CONSTRAINT IF EXISTS ck_tasks_priority;
ALTER TABLE tasks ADD CONSTRAINT ck_tasks_priority CHECK (priority IN ('LOW', 'MEDIUM', 'HIGH'));

--------------------------------------------------------------------------------
-- 4. Projects: Active, On Hold, Completed; three priorities
--------------------------------------------------------------------------------
UPDATE projects SET status = 'ACTIVE', version = version + 1 WHERE status = 'PLANNING';

UPDATE projects
SET status = 'ON_HOLD',
    archived_at = COALESCE(archived_at, now()),
    archived_by = COALESCE(archived_by, owner_id),
    version = version + 1
WHERE status = 'CANCELLED';

ALTER TABLE projects DROP CONSTRAINT IF EXISTS ck_projects_status;
ALTER TABLE projects ADD CONSTRAINT ck_projects_status
    CHECK (status IN ('ACTIVE', 'ON_HOLD', 'COMPLETED'));

UPDATE projects SET priority = 'HIGH', version = version + 1 WHERE priority = 'CRITICAL';
ALTER TABLE projects DROP CONSTRAINT IF EXISTS ck_projects_priority;
ALTER TABLE projects ADD CONSTRAINT ck_projects_priority CHECK (priority IN ('LOW', 'MEDIUM', 'HIGH'));

--------------------------------------------------------------------------------
-- 5. Automation: in-app notifications on project and task events only
--------------------------------------------------------------------------------
-- Runs go with their rules (ON DELETE CASCADE); runs of other rules on removed events go too.
DELETE FROM project_automations
WHERE action_type <> 'NOTIFY'
   OR trigger_event NOT IN ('project.created', 'project.updated', 'project.archived',
                            'task.created', 'task.updated', 'task.assigned', 'task.completed', 'task.blocked');
DELETE FROM automation_runs
WHERE trigger_event NOT IN ('project.created', 'project.updated', 'project.archived',
                            'task.created', 'task.updated', 'task.assigned', 'task.completed', 'task.blocked');

ALTER TABLE project_automations DROP CONSTRAINT IF EXISTS ck_project_automations_chat_channel;
ALTER TABLE project_automations DROP COLUMN IF EXISTS action_channel_reference;
ALTER TABLE project_automations DROP CONSTRAINT IF EXISTS ck_project_automations_action_type;
ALTER TABLE project_automations ADD CONSTRAINT ck_project_automations_action_type
    CHECK (action_type IN ('NOTIFY'));

ALTER TABLE project_automations DROP CONSTRAINT IF EXISTS ck_project_automations_trigger_event_catalogue;
ALTER TABLE project_automations ADD CONSTRAINT ck_project_automations_trigger_event_catalogue
    CHECK (trigger_event IN ('project.created', 'project.updated', 'project.archived',
                             'task.created', 'task.updated', 'task.assigned', 'task.completed', 'task.blocked'));

ALTER TABLE automation_runs DROP CONSTRAINT IF EXISTS ck_automation_runs_trigger_event_catalogue;
ALTER TABLE automation_runs ADD CONSTRAINT ck_automation_runs_trigger_event_catalogue
    CHECK (trigger_event IN ('project.created', 'project.updated', 'project.archived',
                             'task.created', 'task.updated', 'task.assigned', 'task.completed', 'task.blocked'));

--------------------------------------------------------------------------------
-- 6. History and pending events of removed features
--------------------------------------------------------------------------------
-- TASK_OVERDUE activity stays: it is part of a kept task's history.
DELETE FROM project_activity
WHERE activity_type IN ('PHASE_CREATED', 'PHASE_UPDATED',
                        'MILESTONE_CREATED', 'MILESTONE_COMPLETED', 'MILESTONE_AT_RISK',
                        'DEPENDENCY_CREATED', 'DEPENDENCY_BROKEN',
                        'RISK_CREATED', 'RISK_UPDATED', 'RISK_RESOLVED',
                        'AI_RECOMMENDATION_CREATED', 'AI_RECOMMENDATION_ACCEPTED', 'AI_RECOMMENDATION_REJECTED');

DELETE FROM outbox
WHERE aggregate_type IN ('Phase', 'Milestone', 'Dependency', 'Risk', 'AIRecommendation')
   OR event_type LIKE 'phase.%'
   OR event_type LIKE 'milestone.%'
   OR event_type LIKE 'dependency.%'
   OR event_type LIKE 'risk.%'
   OR event_type LIKE 'ai.recommendation.%';
