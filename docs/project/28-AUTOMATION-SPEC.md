# Automation (P5) — Specification

## Status of this document

**Resolved and implementation-ready for a deliberately narrow V1**; one sub-capability is
explicitly deferred with its own open decision (§7). `08-ROADMAP.md` P5 lists: Triggers,
Conditions, Actions, Natural Language Automation, Approvals, Audit. `01-SPEC.md`/`03-DATABASE.md`
name `ProjectAutomation`/`AutomationRun` as owned entities, by name only.
`automation/package-info.java` (already in the codebase before this document) fixes package
placement: `api → application → domain`, with `infrastructure` for persistence — the standard
module layering, applied to this bounded context specifically (unlike `ai`, which explicitly
excludes persistence).

Tags: **[ESTABLISHED]**/**[RESOLVED — conservative]**/**[OPEN DECISION]** as in the P4 documents.

## 1. Why V1 is scoped to trigger + notify/chat, not general mutation

**[RESOLVED — conservative]** "Actions" (P5 roadmap bullet) could mean any existing mutating
operation across the whole domain model — the same unresolved allowlist problem
`27-AI-ACTIONS-SPEC.md` §1 already identified for AI. This document does not re-invent that
allowlist. Instead, V1 scopes Automation actions to the **two already-existing, already-wired
integration ports** with clear, narrow, already-approved contracts: `NotificationPort.notify`
and `ChatPort.postMessage`. Both are non-destructive, non-mutating-to-project-data (they send a
message; they do not change a Task, Risk, or any other domain entity), and both already have a
real (if minimal — `LoggingNotificationAdapter`/`LoggingChatAdapter`) implementation, unlike an
AI provider. This gives P5 a genuinely complete, non-fake V1 without guessing at a broader
mutating-action allowlist.

## 2. Triggers — reusing the existing event catalogue exactly

**[RESOLVED — conservative]** A `ProjectAutomation`'s `triggerEvent` must be one of the event
type strings already raised by `WorkEventRecorder`/`ProjectEventRecorder` (e.g. `task.overdue`,
`task.blocked`, `risk.created`, `dependency.broken` — the full catalogue in `05-EVENTS.md`,
excluding the `## AI`/`## Automation` sections themselves, since an automation reacting to
another automation's own execution is not specified and would need its own cycle-prevention
design). No new event type is introduced; this document only lets an existing event type be
*configured as a trigger*.

Enforced by `com.projectmodule.automation.domain.AutomationTriggerEvent` — the single canonical
list of these 19 values, checked by `ProjectAutomation`'s domain validation at creation
(`ValidationException`, the existing 400 `validation-failed` shape) — and mirrored by a database
`CHECK` constraint (`V13__restrict_automation_trigger_events.sql`, added after a compliance audit
found `V12` only enforced non-blank, not catalogue membership). No other list of trigger values
exists anywhere in this codebase.

## 3. Conditions — deferred to a future slice

**[RESOLVED — conservative]** A condition sub-language (field comparisons, boolean logic) is a
genuine rules-engine design this document has no basis to invent from existing documentation —
nothing in `05-EVENTS.md`/`08-ROADMAP.md` defines one. V1 automations match on `triggerEvent`
and `projectId` only ("match-all" once triggered) — documented here explicitly as a scope
reduction, not silently omitted. A future slice can add a condition expression once one is
specified and approved.

## 4. Approvals — not required for this action set

**[RESOLVED — conservative]** The Core Principle instruction is explicit: *"Dangerous/
destructive/high-impact actions require explicit approval unless a clearly approved automation/
agent policy says otherwise."* Sending a notification or a chat message is neither destructive
nor high-impact (it changes no project data — §1) — this document is that "clearly approved...
policy": V1 automation actions execute without a human-approval gate. A future mutating action
type would require one, deferred alongside §1's broader allowlist.

## 5. Data model

**[RESOLVED — conservative]** Placed under `com.projectmodule.automation` per
`automation/package-info.java`'s own existing layering.

`ProjectAutomation` (`automation.domain`):

| Field | Type | Notes |
|---|---|---|
| `id` | UUID (v7) | |
| `projectId` | UUID, required | |
| `name` | String, required | |
| `description` | String, nullable | |
| `triggerEvent` | String, required | one of `05-EVENTS.md`'s catalogued event types (§2) |
| `actionType` | `AutomationActionType` (`NOTIFY`, `CHAT_MESSAGE`) | |
| `actionRecipientId` | UUID, required for `NOTIFY` | opaque `ExternalUserId`, the person to notify — configured explicitly per automation, not defaulted to e.g. "the project owner," to avoid inventing a resolution rule nothing specifies |
| `actionChannelReference` | String, required for `CHAT_MESSAGE` | matches `ChatPort.postMessage`'s existing `channelReference` parameter |
| `actionMessage` | String, required | a plain static message — no templating/placeholder engine (would be new infrastructure; not specified anywhere) |
| `enabled` | boolean, default `true` | disabled automations never execute (§6) |
| `archivedAt`/`archivedBy` | nullable | same soft-archive convention as every other work entity |
| `createdAt`/`updatedAt`/`version` | from `BaseEntity` | |

`AutomationRun` (`automation.domain`) — append-only, mirrors `ProjectActivity`'s "no mutators"
pattern:

| Field | Type | Notes |
|---|---|---|
| `id` | UUID (v7) | |
| `automationId` | UUID | |
| `projectId` | UUID | |
| `triggerEvent` | String | the event type that fired this run |
| `status` | `AutomationRunStatus` (`SUCCEEDED`, `FAILED`) | |
| `errorMessage` | String, nullable | set only when `FAILED` |
| `executedAt` | Instant | |

## 6. Execution — synchronous, in the triggering request, never breaking it

**[RESOLVED — conservative]** This module has no scheduler and no message-bus consumer anywhere
(confirmed: the outbox is write-only — nothing reads `OutboxMessageRepository.
findByPublishedAtIsNullOrderByOccurredAtAsc` yet). Adding one would be new infrastructure this
document has no justification to introduce (rule 22). Automation matching therefore happens
**synchronously**, hooked into the single existing funnel point both event recorders already
use: `WorkEventRecorder`'s private `persist(...)` method (and `ProjectEventRecorder`'s
equivalent), immediately after the `ProjectActivity`/`OutboxMessage` are saved, in the same
transaction as the triggering mutation.

Because the triggering mutation's own transaction must not fail due to an unrelated
notification/chat delivery problem, action execution failures are **caught, not propagated**:
`AutomationDispatcher.dispatch(context, projectId, eventType)` looks up enabled, matching,
non-archived `ProjectAutomation` rows, executes each action, and on failure records an
`AutomationRun` with `status = FAILED` and the error message — the triggering request still
succeeds. No retry (none is specified — the same "do not silently retry" reasoning
`25-AI-ARCHITECTURE-SPEC.md` §6 already applies to AI failures).

**Archived projects**: the triggering mutation's own application service already calls
`ProjectApplicationService.findActiveProjectInCallerOrganization` before performing the change
that raises the event — an archived project's mutations are already rejected before
`AutomationDispatcher` is ever reached, so "archived projects must not accidentally receive
automation actions" is satisfied by an existing guard, not new logic.

`AutomationDispatcher` (`automation.application`) is injected into `WorkEventRecorder`/
`ProjectEventRecorder` as a collaborator — the minimal-diff hook point, since every domain event
already funnels through those two classes; no other existing application service is modified.
It reads the triggering project via `ProjectRepository` directly rather than
`ProjectApplicationService` — the latter itself depends on `ProjectEventRecorder`, so routing
through it here would be a circular bean dependency, not just a redundant check (the
organization boundary was already verified once, upstream, by the mutation that raised the
event).

## 7. [OPEN DECISION] Natural Language Automation

Configuring an automation rule from a free-text description requires the same LLM provider
`25-AI-ARCHITECTURE-SPEC.md` §4 already leaves open. Not modeled here; deferred until that
decision is made. A human still configures `ProjectAutomation` rows directly via the API (§8) in
V1 — manual automation configuration always works without AI, per the Core Principle.

## 8. API

**[RESOLVED — conservative]**
```
POST   /api/v1/projects/{projectId}/automations
GET    /api/v1/projects/{projectId}/automations
PATCH  /api/v1/automations/{id}
DELETE /api/v1/automations/{id}
GET    /api/v1/automations/{id}/runs
```
Matches the existing "collection nested under a project, item flat" pattern. `PATCH` supports
`enabled`/plain-field edits, the same "null field = not supplied" convention every other
`UpdateXRequest` already uses. `DELETE` soft-archives, matching Task/Risk/Issue/Decision.
`GET .../runs` returns `AutomationRun` history for one automation, newest first.

## 9. Authorization

**[ESTABLISHED]** Creating/updating/archiving a `ProjectAutomation` requires `EDIT_PROJECT`
(configuring automation changes project behavior, the same reasoning every other project-scoped
write already uses); listing automations/runs requires `VIEW_PROJECT`. No new permission.
`AutomationDispatcher`'s own execution is **not** gated by `ProjectAuthorizationService`,
matching `DashboardApplicationService`'s own precedent for a system-internal operation that
doesn't have a single human caller to check a permission against — the human-facing
configuration endpoints (§8) are where authorization applies; execution itself just carries out
what a human, already authorized at configuration time, set up.

## 10. Database

**[RESOLVED — conservative]** New Flyway migration `V12__create_project_automations.sql`: two
tables, `project_automations` and `automation_runs`. `project_automations`: FK to `projects`,
`CHECK` constraints on `action_type` and the conditional-required columns (`ck_...
_notify_recipient`: `action_type != 'NOTIFY' OR action_recipient_id IS NOT NULL`; similarly for
`CHAT_MESSAGE`/`action_channel_reference`), archive-consistency check, index on
`(project_id, trigger_event) WHERE enabled AND archived_at IS NULL` (the dispatcher's lookup
query). `automation_runs`: FK to `project_automations`, index on `automation_id`, no archive
columns (append-only, §5). `V13__restrict_automation_trigger_events.sql` (added after a
compliance audit) adds a `CHECK` constraint restricting both tables' `trigger_event` column to
the exact `AutomationTriggerEvent` catalogue (§2) — database-level defense-in-depth matching the
domain-level validation.

## 11. Testing

**[ESTABLISHED]** Domain (`ProjectAutomation` creation/validation/enable-disable/archive,
`AutomationRun` creation), application (`AutomationDispatcher` matches enabled rules by
project+event, skips disabled/archived rules, executes `NOTIFY`/`CHAT_MESSAGE` via the existing
ports, catches action failure and records `FAILED` without propagating, authorization/org
boundary on the configuration endpoints), controller (CRUD, validation, response shape,
authorization errors), persistence (round-trip, constraints, the dispatcher's lookup index,
archive behavior, optimistic locking) against real PostgreSQL, and one integration-style test
proving an existing mutation (e.g. marking a task overdue) triggers a matching automation
end-to-end without breaking the triggering request when the action fails.

## 12. Out of scope

Conditions (§3), Natural Language Automation (§7), any mutating action type beyond
notify/chat (§1), approval gating (§4), retries, a scheduler/async dispatch, automations
triggered by AI or automation events themselves.
