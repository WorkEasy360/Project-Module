# AI Recommendations (P4) — Specification

## Status of this document

**Resolved and implementation-ready**, built on `25-AI-ARCHITECTURE-SPEC.md`'s boundary. Covers
the `AIRecommendation` P4 roadmap bullet, and gives an honest, non-mutating (recommend-only)
subset of AI Task Breakdown, AI Task Improvement, Project Copilot, AI Risk Analysis and AI
What-if — the five roadmap capabilities that have a real read-only meaning. "AI Project
Creation" has none (§1 of `25-AI-ARCHITECTURE-SPEC.md`) and is not modeled here.

Tags: **[ESTABLISHED]**/**[RESOLVED — conservative]** as in `25-AI-ARCHITECTURE-SPEC.md`.

## 1. What "recommendation-only" means here

**[RESOLVED — conservative]** Every recommendation type in this document *suggests*; none
*applies*. Accepting a recommendation records a human decision (audit value: "this suggestion
was reviewed and approved") but does **not** automatically create/edit any Task, Risk, or other
resource — doing so would require invoking a mutating tool, and no tool allowlist is approved
yet (`27-AI-ACTIONS-SPEC.md`). A human who accepts a "task breakdown" recommendation still
creates the resulting subtasks by hand, through the existing `POST /tasks` endpoint, using the
recommendation's content as a reference. This is a deliberate, conservative scope reduction from
a fully automated "AI applies its own suggestion" flow, consistent with rule 1's Core Principle
("Manual = Control... Human = Final authority") applying even to the *application* of an
accepted suggestion, not only its creation.

## 2. Data model

**[RESOLVED — conservative]** `AIRecommendation` — a new entity, placed and reviewed per
`25-AI-ARCHITECTURE-SPEC.md` §2 (`work.domain`/`work.infrastructure`/`work.application`), the
same conventions every other work-management entity in this module already follows
(`BaseEntity`, `Persistable<UUID>`, no public setters, soft-archive not applicable — see §6).

| Field | Type | Notes |
|---|---|---|
| `id` | UUID (v7) | from `BaseEntity` |
| `projectId` | UUID | required, the owning project |
| `type` | `RecommendationType` | see §3 |
| `resourceId` | UUID, nullable | the specific Task this concerns, for `TASK_BREAKDOWN`/`TASK_IMPROVEMENT`; `null` for project-wide types |
| `question` | String, nullable | the free-text scenario for `WHAT_IF`; `null` for every other type |
| `title` | String | short label of what was suggested — from the provider's response |
| `rationale` | String | why it was suggested — from the provider's response |
| `payload` | String (JSON text), nullable | structured suggestion content, same "payload as JSON text" convention `OutboxMessage`/`ProjectActivity` already use — no new serialization dependency |
| `status` | `RecommendationStatus` | `PENDING` (initial) → `ACCEPTED` \| `REJECTED`. `EXPIRED` exists as a named state (the user-facing field the task instructions require: "whether it is pending/accepted/rejected/expired") but nothing in this batch sets it — no TTL or scheduler is specified anywhere (confirmed: this module has no scheduler at all, per `TaskStatus.java`'s own javadoc), so automatic expiry is not implemented; attempting to move a recommendation to `EXPIRED` in this V1 has no code path and is not exposed via the API |
| `requestedBy` | UUID | the `ExternalUserId` of the human who requested it (`context.requireUserId()`) — every recommendation in this batch is human-requested, never autonomous (§5 of `25-AI-ARCHITECTURE-SPEC.md`) |
| `respondedBy` | UUID, nullable | who accepted/rejected it |
| `respondedAt` | Instant, nullable | |
| `createdAt`/`updatedAt`/`version` | from `BaseEntity` | optimistic locking, same as every other entity |

**No soft-archive.** Unlike Task/Risk/Issue, a recommendation is a point-in-time suggestion, not
an ongoing work item — there is nothing to "restore." `REJECTED` already serves the role
archiving would.

## 3. RecommendationType (exhaustive for V1)

**[RESOLVED — conservative]** Five values, one per roadmap capability that has an honest
read-only meaning (§1):

| Type | Roadmap capability | `resourceId` | `question` | Context gathered via (existing, reused, read-only) |
|---|---|---|---|---|
| `TASK_BREAKDOWN` | AI Task Breakdown | required (a `Task` id) | — | `TaskApplicationService.getTask` |
| `TASK_IMPROVEMENT` | AI Task Improvement | required (a `Task` id) | — | `TaskApplicationService.getTask` |
| `PROJECT_SUMMARY` | Project Copilot | — | optional (a free-text question) | `ProjectApplicationService`, `ReportsApplicationService.getSummary`, `ProjectHealthApplicationService.getHealth` |
| `RISK_ANALYSIS` | AI Risk Analysis | — | — | `RiskApplicationService.listRisks` |
| `WHAT_IF` | AI What-if | — | required | same context as `PROJECT_SUMMARY`, plus the `question` |

`resourceId`/`question` requiredness is enforced by the application service (§4), not by a
database constraint — mirroring how e.g. `UpdateTaskRequest`'s per-field rules are enforced in
`TaskApplicationService`, not the schema.

## 4. Application service

**[ESTABLISHED]** `AIRecommendationApplicationService` (`work.application`), same authorization
pattern as every other project-scoped service:

```java
public AIRecommendation requestRecommendation(RequestContext context, UUID projectId,
        RecommendationType type, UUID resourceId, String question) {
    projectApplicationService.findActiveProjectInCallerOrganization(context, projectId);
    projectAuthorizationService.requirePermission(context.requireUserId(), projectId, ProjectPermission.EDIT_PROJECT);
    // validate resourceId/question requiredness per §3, gather context via existing read services,
    // call aiProviderPort.generate(...); on success, persist; on failure, propagate — nothing persisted (§6, 25-AI-ARCHITECTURE-SPEC.md)
}

public List<AIRecommendation> listRecommendations(RequestContext context, UUID projectId) { /* VIEW_PROJECT */ }
public AIRecommendation acceptRecommendation(RequestContext context, UUID recommendationId) { /* EDIT_PROJECT, PENDING -> ACCEPTED only */ }
public AIRecommendation rejectRecommendation(RequestContext context, UUID recommendationId) { /* EDIT_PROJECT, PENDING -> REJECTED only */ }
```

`requestRecommendation`/`acceptRecommendation`/`rejectRecommendation` use `EDIT_PROJECT` (they
create or change recommendation state, the same reasoning every other project-scoped *write*
already uses — e.g. Decision/Risk/Issue creation); `listRecommendations` uses `VIEW_PROJECT`,
matching every other project-scoped *read*. Accepting/rejecting an already-`ACCEPTED`/`REJECTED`
recommendation is rejected with the existing `BusinessRuleViolationException` — the same "no
event exists for that transition" pattern `TaskApplicationService`/`RiskApplicationService`
already use for invalid status transitions.

## 5. API

**[RESOLVED — conservative]**
```
POST   /api/v1/projects/{projectId}/ai/recommendations
GET    /api/v1/projects/{projectId}/ai/recommendations
GET    /api/v1/ai/recommendations/{id}
POST   /api/v1/ai/recommendations/{id}/accept
POST   /api/v1/ai/recommendations/{id}/reject
```
Matches the existing "collection nested under a project, item flat" pattern
Phase/Milestone/Risk/Issue/Decision already use; the single-item `GET` gives the `POST`
response's `Location` header a resolvable target, the same convention `TaskController`'s
`GET /tasks/{id}` already establishes for `POST /projects/{id}/tasks`. Request body for `POST .../recommendations`:
`record RequestRecommendationRequest(RecommendationType type, UUID resourceId, String question)`
— `resourceId`/`question` nullable at the DTO level, required-per-type enforced by the
application service (§3/§4), not Bean Validation (the requirement is conditional on `type`, the
same reasoning existing conditional business rules use application-service validation rather
than annotation-based validation).

Response: `AIRecommendationResponse(UUID id, UUID projectId, RecommendationType type, UUID
resourceId, String question, String title, String rationale, String payload,
RecommendationStatus status, UUID requestedBy, UUID respondedBy, Instant respondedAt, Instant
createdAt, Instant updatedAt, long version)`.

## 6. Events and audit

**[ESTABLISHED]** `05-EVENTS.md` catalogues exactly one AI recommendation event:
`ai.recommendation.created`. No `ai.recommendation.accepted`/`rejected` event is catalogued —
this document does not invent one. A new `AIRecommendationEventRecorder` (mirroring
`WorkEventRecorder`'s exact shape: one `ProjectActivity` + one `OutboxMessage`, in the caller's
existing transaction) raises `ai.recommendation.created` only, on successful creation. Accept/
reject still write a `ProjectActivity` entry (new `ActivityType` values `AI_RECOMMENDATION_ACCEPTED`/
`AI_RECOMMENDATION_REJECTED`, alongside a new `AI_RECOMMENDATION_CREATED` — `ActivityType` is an
internal audit enum, not part of the event catalogue, so adding entries here does not conflict
with `05-EVENTS.md`'s fixed list) but raise no outbox event, matching exactly what's catalogued
and nothing more.

## 7. Failure behavior

**[ESTABLISHED]** Per `25-AI-ARCHITECTURE-SPEC.md` §6: if `AIProviderPort.generate(...)` throws
(always, with `UnavailableAIProviderAdapter`), `requestRecommendation` persists nothing and lets
`IntegrationUnavailableException` propagate as the standard `ProblemDetail` (HTTP 503 — already
mapped by the existing `GlobalExceptionHandler`/`ErrorType`).

## 8. Database

**[RESOLVED — conservative]** New Flyway migration `V11__create_ai_recommendations.sql`:
table `ai_recommendations`, columns per §2, `FOREIGN KEY (project_id) REFERENCES projects(id)`,
`CHECK` constraints mirroring existing enum-backed columns (e.g. `ck_ai_recommendations_type`,
`ck_ai_recommendations_status`), index on `(project_id, status)` for the list endpoint (the same
reasoning existing per-project list queries already index on `project_id`), optimistic-lock
`version` column, no archive columns (§2).

## 9. Testing

**[ESTABLISHED]** Domain (`AIRecommendation` creation, `accept()`/`reject()` valid and invalid
transitions), application (authorization, org boundary, per-type `resourceId`/`question`
validation, successful creation persists nothing-until-provider-succeeds via a Mockito
`AIProviderPort` double, provider failure persists nothing and propagates, accept/reject
transition rules), controller (request validation, response shape, 503 on provider failure,
403/404 on authorization/org-boundary failures), persistence (round-trip, constraints, the
`(project_id, status)` index query, optimistic locking) against real PostgreSQL.

## 10. Out of scope

Auto-applying an accepted recommendation (§1), any recommendation type beyond §3's five,
`EXPIRED` automation, conversation/multi-turn context, any real provider call (blocked on
`25-AI-ARCHITECTURE-SPEC.md` §4).
