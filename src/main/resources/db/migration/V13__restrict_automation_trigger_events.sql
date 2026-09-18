-- Compliance fix: V12 only enforced trigger_event as non-blank text, not membership in the
-- canonical event catalogue docs/project/28-AUTOMATION-SPEC.md §2 requires
-- (docs/project/05-EVENTS.md's Project/Phase/Milestone/Task/Dependency/Risk sections,
-- excluding its own AI/Automation sections). The application layer now validates the identical
-- list via com.projectmodule.automation.domain.AutomationTriggerEvent; this adds the matching
-- database-level constraint, the same defense-in-depth every other enum-backed column in this
-- schema already has (e.g. ck_ai_recommendations_type, ck_ai_recommendations_status).

ALTER TABLE project_automations
    ADD CONSTRAINT ck_project_automations_trigger_event_catalogue
    CHECK (trigger_event IN (
        'project.created', 'project.updated', 'project.archived',
        'phase.created', 'phase.updated',
        'milestone.created', 'milestone.completed', 'milestone.at_risk',
        'task.created', 'task.updated', 'task.assigned', 'task.completed', 'task.overdue', 'task.blocked',
        'dependency.created', 'dependency.broken',
        'risk.created', 'risk.updated', 'risk.resolved'
    ));

ALTER TABLE automation_runs
    ADD CONSTRAINT ck_automation_runs_trigger_event_catalogue
    CHECK (trigger_event IN (
        'project.created', 'project.updated', 'project.archived',
        'phase.created', 'phase.updated',
        'milestone.created', 'milestone.completed', 'milestone.at_risk',
        'task.created', 'task.updated', 'task.assigned', 'task.completed', 'task.overdue', 'task.blocked',
        'dependency.created', 'dependency.broken',
        'risk.created', 'risk.updated', 'risk.resolved'
    ));
