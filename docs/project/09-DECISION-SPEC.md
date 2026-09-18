# Decision — Approved Specification

## Status of this document

**Approved.** All four open questions in §16 were presented as an explicit approval checklist and
confirmed by the project owner, each choosing the recommended option. Unlike `01-SPEC.md` through
`08-ROADMAP.md`, which are followed exactly because they are the original specification, this
document exists because those eight contain only two bare mentions of `Decision`
(`01-SPEC.md:30` and `03-DATABASE.md:16` — an entity name and nothing else: no fields, no
statuses, no API, no events). It is now this module's specification for Decision, to be followed
exactly the same way `01-SPEC.md` through `08-ROADMAP.md` are — implementation may proceed from
it once explicitly requested.

Every requirement below remains tagged, for provenance:

- **[ESTABLISHED]** — a direct, mechanical application of a convention already in force
  elsewhere in the codebase for every comparable entity (Phase, Task, Risk, Issue). Not a new
  design choice; changing it would make Decision inconsistent with everything else in the
  module.
- **[APPROVED]** — new for Decision specifically, since nothing in `01-SPEC.md` through
  `08-ROADMAP.md` addresses it, and confirmed via the §16 approval checklist. (Marked
  **[PROPOSED]** below where the surrounding text is unchanged from the original proposal; see
  §16 for the confirmed outcome of each.)

Where a `[PROPOSED]` item has a real alternative, the alternative is stated alongside it.

## 1. Purpose

**[PROPOSED]** A Decision records a choice that was made about a project — what was decided and
why — so it can be referred back to later. This is the ordinary meaning of "decision log" /
"architecture decision record" in project-management tooling, and the closest reading consistent
with `Decision` sitting alongside `Risk`, `Issue`, `ProjectComment` in the entity list
(`01-SPEC.md:17-40`) as a project-governance record rather than a work item (it is not listed
under Phase/Milestone/TaskList/Task in `03-DATABASE.md`, so it is not modelled as a kind of task).

No document defines this purpose explicitly. This framing is the basis for every field below; if
it's wrong, the rest of this proposal likely needs to change with it.

## 2. Fields and data types

| Field | Type | Required | Tag | Rationale |
|---|---|---|---|---|
| `id` | UUID (v7) | yes | **[ESTABLISHED]** | Same as every entity (`UuidV7.generate()`, app-assigned). |
| `projectId` | UUID | yes, immutable | **[ESTABLISHED]** | Same ownership convention as Phase/Task/Risk/Issue — see §6. |
| `name` | String, ≤200 chars | yes | **[ESTABLISHED convention, PROPOSED value]** | Every entity (Project, Phase, Milestone, Task, Risk, Issue) has a required `name` of the same shape. Calling it `name` rather than `title` is for consistency, not because either is documented. |
| `description` | text | no | **[ESTABLISHED]** | Same optional free-text field every entity has. Doubles as the decision's rationale/context — see open question in §16. |
| `decidedBy` | UUID (opaque `ExternalUserId`), nullable | no | **[PROPOSED]** | Who made the decision, which may differ from the API caller recording it (a PM logging a decision the VP made). Modelled exactly like `Task.assigneeId`: an optional opaque reference to the User module, no foreign key (`06-INTEGRATION.md` — User is external). Alternative: omit this field entirely and rely solely on `RequestContext.userId()` (who *recorded* it, always captured in the audit trail) — see §16. |
| `archivedAt` / `archivedBy` | timestamptz / UUID, nullable together | — | **[ESTABLISHED]** | Soft-archive pair every entity has. |
| `createdAt` / `updatedAt` / `version` | from `BaseEntity` | — | **[ESTABLISHED]** | Standard. `createdAt` doubles as "when this was recorded"; no separate `decidedAt` is proposed, to avoid inventing a field nothing in the docs asks for — see §16 if a distinct decision date is wanted. |

No `status` field is proposed — see §4.

## 3. Required vs optional fields

**[ESTABLISHED convention]** `name` and `projectId` required (immutable after creation, same as
every entity); `description` and `decidedBy` optional; `version` required on every `PATCH`
request for the optimistic-lock check (same as every `Update*Request`).

## 4. Lifecycle / status

**[PROPOSED — recommended: none]** No status field, and no status transition. A Decision simply
exists once recorded, is editable (name/description/decidedBy) until archived, and archiving is
its only lifecycle event — exactly the shape already built for `Issue`
(`work/domain/Issue.java`), not `Risk` (which has `OPEN`/`RESOLVED` because `risk.resolved` is a
documented event to drive it).

**Rationale for recommending "none" over a `Risk`-style status:** nothing in any of the 8 docs
suggests a decision has states (proposed → decided → superseded, or similar). Inventing one would
mean inventing both the values and the transition rule with zero grounding, which is exactly what
this proposal step exists to avoid doing silently.

**Alternative, if desired:** `PROPOSED` → `DECIDED` (mirroring `Risk`'s `OPEN` → `RESOLVED`
shape), with a `decide()` transition. This would need its own justification, since unlike Risk's
`resolve()`, there's no documented event to hang it on (see §11) — it would be entirely invented,
not merely extended. **This is the single biggest open question in this proposal; see §16.**

## 5. Validation rules

**[ESTABLISHED]** `name`: not blank, ≤200 characters (same `@NotBlank`/`@Size(max = 200)` pattern
and the same `ValidationException` thrown by the domain constructor as every other entity).
`projectId`/`decidedBy` (if supplied): valid UUID, enforced by Spring's path-variable/JSON
binding, same as everywhere else. No other validation is documented or proposed.

## 6. Project/organization ownership

**[ESTABLISHED]** Project-scoped via a plain `projectId` column (no JPA association, matching
`ProjectMember`/`Phase`/`Task`/`Risk`/`Issue`). Organization boundary resolved the same way every
project-scoped entity resolves it: `ProjectApplicationService.findActiveProjectInCallerOrganization`
before every operation, so a Decision can only be created under, listed from, or reached through a
project the caller's organization actually owns.

## 7. Authorization requirements

**[ESTABLISHED]** Reuses `ProjectAuthorizationService` exactly as `Risk`/`Issue`/`Phase`/`Task`
do — no new permission, no new authorization mechanism:

- Create, update, archive → `ProjectPermission.EDIT_PROJECT`
- List → `ProjectPermission.VIEW_PROJECT`

## 8. API endpoints and request/response shape

**[ESTABLISHED convention, PROPOSED because undocumented]** `04-API.md` defines no Decision
endpoints, the same gap Risk and Issue had. Extending the identical "collection nested under
project, item flat" pattern those two already use:

```
POST   /api/v1/projects/{projectId}/decisions
GET    /api/v1/projects/{projectId}/decisions
PATCH  /api/v1/decisions/{id}
DELETE /api/v1/decisions/{id}
```

No single-item `GET /decisions/{id}`, matching Phase/Milestone/Risk/Issue (only `Task` has one,
because `04-API.md` documents it explicitly for Task and nowhere else).

`CreateDecisionRequest`: `name` (required), `description` (optional), `decidedBy` (optional UUID).
`UpdateDecisionRequest`: `name`, `description`, `decidedBy` all optional (nulls = unchanged), plus
required `version`. `DecisionResponse`: `id`, `projectId`, `name`, `description`, `decidedBy`,
`archived`, `createdAt`, `updatedAt`, `version` — the same response shape every entity has, with
no `status` field (per §4's recommendation).

## 9. Soft-archive behavior

**[ESTABLISHED]** `DELETE /decisions/{id}` calls `archive(actorId)`, setting
`archivedAt`/`archivedBy` together (never separately — same DB `CHECK` as every table), guarded
against double-archive, and excluded from all "active" queries via `...AndArchivedAtIsNull`
repository methods. No hard delete, no event for archiving (consistent with every entity: none of
them raise an `X.archived` event, because none is documented for any entity, not just Decision).

## 10. Optimistic locking

**[ESTABLISHED]** `version` required in `UpdateDecisionRequest`; the application service compares
it to the loaded entity's version and throws `ConflictException` on mismatch before applying any
change — the same manual check as every other `update*` use case, backed by Hibernate's own
`@Version` flush-time check as the secondary defense.

## 11. Domain events

**[PROPOSED — recommended: none]** `05-EVENTS.md` has no `## Decision` section — zero Decision
events are documented, the same as Issue. Recommending the same resolution already applied to
Issue: no domain events, no `WorkEventRecorder` integration, so Decision create/update/archive do
not appear in the outbox. If §4's alternative (status lifecycle) is approved instead, a
`decision.decided` event would need to be invented alongside it, which is a second, compounding
invention beyond what's asked here — flagged in §16.

## 12. Outbox/audit behavior

**[PROPOSED — recommended: none, following §11]** No outbox messages, no `ProjectActivity` audit
rows. Decision records exist in their own table and are reachable through the Decision API, but —
like Issue — are invisible to the project's activity feed and to any outbox consumer. This is a
direct consequence of §11, not a separate decision.

## 13. Database constraints/indexes

**[ESTABLISHED]** A new `V7__create_decisions.sql` migration (V1–V6 already taken by
Project/Phase-Milestone-TaskList/Task-Subtask-Checklist/Dependency/Risk/Issue):

```
decisions
  id               uuid         PK
  project_id       uuid         NOT NULL, FK -> projects(id) ON DELETE CASCADE
  name             varchar(200) NOT NULL
  description      text
  decided_by       uuid
  archived_at      timestamptz
  archived_by      uuid
  created_at       timestamptz  NOT NULL
  updated_at       timestamptz  NOT NULL
  version          bigint       NOT NULL DEFAULT 0

  CHECK (length(btrim(name)) > 0)
  CHECK ((archived_at IS NULL) = (archived_by IS NULL))

  INDEX (project_id)
  INDEX (project_id) WHERE archived_at IS NULL
```

No `status` column (per §4), no `priority` column — nothing in any doc suggests a decision has a
priority the way Risk/Issue do, and none is proposed here.

## 14. Integration considerations

**[ESTABLISHED]** None beyond the existing pattern: `decidedBy` is an opaque reference into the
User module (`06-INTEGRATION.md` — no FK, no cross-module query), identical to
`Task.assigneeId`/`ProjectMember.userId`. No new external service, port, or adapter is implied.

## 15. Tests / acceptance criteria

**[ESTABLISHED]** The same four-layer suite every entity has:

- `DecisionTest` (domain unit): creation, validation rejection, plain-edit mutators, archive
  guard (double-archive, edits-after-archive rejected).
- `DecisionApplicationServiceTest` (Mockito): create + authorization denial, list, update
  (applies edits, stale-version rejection, not-found), archive.
- `DecisionControllerTest` (`@WebMvcTest`): all four endpoints, plus validation-rejection cases.
- `DecisionPersistenceTest` (`@DataJpaTest`, real PostgreSQL, `@EnabledIfEnvironmentVariable`):
  round-trip, FK enforcement, archive round-trip, active-listing exclusion.

If §11/§12 are approved as "no events," `DecisionApplicationServiceTest` needs no
`WorkEventRecorder` mock (same as `IssueApplicationServiceTest`) and there is nothing to add to
`WorkEventRecorderTest`.

## 16. Open questions — resolved via approval checklist

These were the genuine forks — not mechanical convention application — where this proposal made a
judgment call that could reasonably go another way. All four were presented as an explicit
approval checklist and **confirmed, each choosing the recommended option**:

1. **Status/lifecycle (§4). APPROVED: none** (mirrors Issue). No status field, no status column,
   no transition method. Rejected alternative: `PROPOSED` → `DECIDED` with an invented
   `decision.decided` event.
2. **`decidedBy` field (§2). APPROVED: include it**, optional, opaque `ExternalUserId` — modelled
   exactly like `Task.assigneeId`. Rejected alternative: omitting it.
3. **Events/outbox (§11/§12). APPROVED: none** (mirrors Issue). No `WorkEventRecorder`
   integration; Decision create/update/archive will not appear in the outbox or the project
   activity/audit feed. Rejected alternative: inventing `decision.created`/`decision.updated`
   events not present in `05-EVENTS.md`.
4. **Description field (§2, §16 original item 4). APPROVED: `description` only**, no separate
   `rationale`/`outcome`/`alternativesConsidered` fields. Rejected alternative: splitting it into
   multiple structured fields.

Every other section in this document already reflected these outcomes (they were each written as
the recommended option) — sections 1–15 require no further change as a result of this approval.

## 17. Implementation-readiness

**Implementation-ready.** All open questions are resolved (§16); every remaining section is a
mechanical application of existing convention, the same as every prior slice. No Java code, SQL,
or tests have been written; this file is the only change.
