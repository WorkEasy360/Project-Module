# Filters (P2) — Specification

## Status of this document

**Resolved and implementation-ready**, following a different process from `09`–`13`: those
each presented open options and waited for a separate approval round. Per this pass's explicit
instruction — *"document ambiguities and resolve them conservatively... do not code while
ambiguities remain"* — every ambiguity below is resolved directly, choosing the smallest,
most conservative design at each fork, rather than left open for a later round. Where a
resolution was a genuine judgment call (not mechanically forced by existing convention), it is
still marked so you can override it before implementation begins.

`08-ROADMAP.md:29` lists `Filters` under P2 — Views as a single bullet. A fresh full-document
search (including `13-SEARCH-SPEC.md`) found **zero** other mention of "filter" as a
specification anywhere — the two incidental hits in `13-SEARCH-SPEC.md` are prose describing
*Search's* own matching semantics ("...versus a filter/lookup"), not a Filters specification.
Filters was not sufficiently specified anywhere before this document.

Tags used below:

- **[ESTABLISHED]** — a direct, mechanical application of a convention already in force
  elsewhere in this codebase. Not a new design choice.
- **[RESOLVED — conservative]** — genuinely undefined; resolved here by choosing the smallest,
  least-invasive option. A real alternative exists and is named, but not adopted for V1.

## Purpose

**[RESOLVED — conservative]** Filters lets a caller narrow an existing project-scoped entity
list to rows matching specific field values (e.g. "only `BLOCKED` tasks," "only `HIGH`-priority
risks"), using the fields that entity already has. It is **not** a general free-text query —
that is Search's job (`13-SEARCH-SPEC.md`, already approved) — and this document deliberately
does not duplicate it (§2, §3).

## 1. Scope

**[RESOLVED — conservative]** V1 covers exactly the same four entities `13-SEARCH-SPEC.md`
already approved for Search — **Task, Risk, Issue, Decision** — for the same reason Search
chose them: they are project-scoped, already have a real list endpoint, and (unlike
Phase/TaskList/Dependency/Comment/CustomField/Template) carry the enum/reference fields that
make filtering meaningful. Reusing the identical entity boundary keeps these two closely related
P2 capabilities consistent with each other rather than introducing a second, different notion of
"which entities are in P2 V1."

**Explicitly out of scope for V1** (each named, not merely omitted):

- **Milestone** — has a real `status` field (`MilestoneStatus`), a genuine future candidate, but
  excluded from V1 to keep this spec's entity set identical to Search's rather than widening it
  on its own.
- **Phase, TaskList, Subtask, Checklist** — no `status`/`priority`/assignee-like field to filter
  on beyond `name`/`description` (already Search's domain, not Filters').
- **Dependency** — only `broken` (boolean) and two task ids; not enough filterable surface to
  justify inclusion.
- **ProjectComment** — no status/priority/assignee field.
- **ProjectCustomField** — explicitly addressed and excluded in §9.
- **ProjectTemplate, Project** — organization-scoped, not project-scoped (the same reasoning
  `13-SEARCH-SPEC.md` §1/§2 already gave for excluding them from Search); a different boundary
  question this document does not reopen.
- **Filtering across multiple projects at once** — every filter here operates *within* one
  already-selected project's existing list endpoint, the same scope Search settled on.

This is **not** "included merely because it's on the roadmap" — each inclusion is justified by
an existing field on an existing entity; each exclusion is justified by the absence of one.

## 2. Filter fields

**[ESTABLISHED — no new entity fields]** Only fields that already exist today:

| Entity | Filterable field | Existing type | Column |
|---|---|---|---|
| `Task` | `status` | `TaskStatus` (`TODO`/`BLOCKED`/`OVERDUE`/`COMPLETED`) | `tasks.status` |
| `Task` | `assigneeId` | `UUID` (opaque `ExternalUserId`) | `tasks.assignee_id` |
| `Task` | `dueDate` | `LocalDate` | `tasks.due_date` |
| `Risk` | `status` | `RiskStatus` (`OPEN`/`RESOLVED`) | `risks.status` |
| `Risk` | `priority` | `ProjectPriority` (`LOW`/`MEDIUM`/`HIGH`/`CRITICAL`) | `risks.priority` |
| `Issue` | `priority` | `ProjectPriority` | `issues.priority` |
| `Decision` | `decidedBy` | `UUID` (opaque `ExternalUserId`) | `decisions.decided_by` |
| *(all four)* | `archived` | derived from `archived_at`/`archived_by` | see §8 |

**Fields considered and excluded, with reasons:**

- **`project`** — redundant: every one of these endpoints is already nested under
  `/projects/{projectId}/...`; the project is the path, not a filter.
- **`phase` / `milestone` / `task list`** — **none of Task, Risk, Issue or Decision has a
  `phaseId`/`milestoneId`/`taskListId` column.** `03-DATABASE.md` and every entity's own
  migration confirm this (`Task` associates only to `project_id`, explicitly not to a Task List —
  see `Task.java`'s own class Javadoc). Filtering by a field that does not exist would mean
  adding one *for filtering's sake*, which this task explicitly forbids.
- **`name` / `description` contains** — this is free-text matching, already Search's exact
  responsibility (`13-SEARCH-SPEC.md`). Deliberately not duplicated here to avoid two endpoints
  doing overlapping things differently.
- **`Issue.status`** — `Issue` has no status field at all (confirmed: no `## Issue` events, no
  status column — `Issue` only ever has `OPEN`-equivalent existence or archived).
- **`Decision.priority` / `Decision.status`** — `Decision` has neither field.

## 3. Operators

**[RESOLVED — conservative: equality only for enum/reference fields, three comparisons for
dates]**

| Field type | Supported operator(s) | Query parameter shape |
|---|---|---|
| Enum (`status`, `priority`) | equals | `?status=BLOCKED` |
| Reference (`assigneeId`, `decidedBy`) | equals | `?assigneeId=<uuid>` |
| Date (`dueDate`) | before, after, on | `?dueDateBefore=2026-06-01&dueDateAfter=2026-01-01` |
| `archived` | boolean toggle (§8) | `?archived=true` |

**Not included in V1, per "keep it simple":**

- **`in` / `not in`** (multiple values for one field, e.g. "`BLOCKED` or `OVERDUE`") — a real,
  cheap-to-add future enhancement (comma-split an existing equality check), but adds a second
  query-parameter shape (`status=BLOCKED,OVERDUE` vs. `status=BLOCKED`) this document is not
  introducing until asked for.
- **`not equals`** — no concrete use case identified for any of the four fields above; add if
  and when one is.
- **`contains`** — belongs to Search (§2), not duplicated here.
- **`between`** for dates — expressible today by combining `dueDateBefore` **and**
  `dueDateAfter` in one request (§4's `AND` combination), so a separate `between` operator would
  be redundant, not a missing capability.

## 4. Combination logic

**[RESOLVED — conservative: AND only, no OR, no nested groups]** Multiple filter parameters in
one request are combined with **AND** — `?status=BLOCKED&assigneeId=<uuid>` means status is
`BLOCKED` **and** assignee is that user, never *or*. There is no group syntax, no explicit
precedence rule needed (a flat `AND` has none to define), and no `OR` between different field
values.

**Not included in V1:** `OR` combination and nested filter groups. Nothing in any doc — and
nothing about how the four target entities are queried today — requires it; every existing
`findByXAndY...` derived-query method in this codebase is already `AND`-only (e.g.
`findByProjectIdAndArchivedAtIsNull`). Introducing `OR`/grouping would need a real query-builder
(a `Specification<T>`/`Criteria` mechanism this codebase has never used — the one `@Query`
precedent, `ProjectRepository.countActiveByStatus`, is a fixed `GROUP BY`, not a dynamic
predicate builder) — out of proportion to what's been asked for.

## 5. API

**[RESOLVED — conservative: extend existing list endpoints with optional query parameters, no
new endpoint]** All four filter parameter sets are added to the **existing** list endpoints,
purely additively — every parameter is optional, and a request with none behaves **exactly** as
today:

```
GET /api/v1/projects/{projectId}/tasks?status=&assigneeId=&dueDateBefore=&dueDateAfter=&archived=
GET /api/v1/projects/{projectId}/risks?status=&priority=&archived=
GET /api/v1/projects/{projectId}/issues?priority=&archived=
GET /api/v1/projects/{projectId}/decisions?decidedBy=&archived=
```

**Response shape is unchanged**: still `List<TaskResponse>` / `List<RiskResponse>` /
`List<IssueResponse>` / `List<DecisionResponse>` — the same type each endpoint already returns.
No `PageResponse` wrapper is introduced for these four endpoints in this document (§6 explains
why that would go beyond what's asked).

**Why not a dedicated endpoint (the way Search needed one):** Search had to introduce a new
endpoint because it merges rows from *four different tables* into one heterogeneous result.
Filters never merges anything — each request still targets exactly one entity type's own
existing list — so extending that entity's own endpoint is strictly simpler than inventing a
parallel `/filter` endpoint that would just re-implement what `listTasks`/`listRisks`/etc.
already do.

## 6. Pagination and sorting

**[RESOLVED — conservative: no change]** `listTasks`/`listRisks`/`listIssues`/`listDecisions`
are unbounded `List<T>` today, not `Pageable`-driven — unlike `GET /projects` or the approved
`GET /templates`/`GET .../comments`. Retrofitting pagination onto them is a **response-shape
change** to an already-shipped API, which this task's own instructions place off-limits this
turn ("Do not modify existing APIs"). Filters therefore makes no change to pagination or sorting:
results remain the same `createdAt ASC`-ordered, unbounded list, just with fewer rows when a
filter narrows them. Adding pagination to these four endpoints — independent of Filters — would
be its own, separately-decided future change, not bundled into this one.

## 7. Authorization

**[ESTABLISHED]** No change. Each endpoint already resolves the organization boundary via
`ProjectApplicationService.findActiveProjectInCallerOrganization` and requires
`ProjectPermission.VIEW_PROJECT` before returning anything. Filter parameters only narrow *which
rows of an already-authorized list* are returned — they never widen access, and no new
permission or authorization mechanism is introduced.

## 8. Archived records

**[RESOLVED — conservative]** Default (parameter omitted) behavior is **unchanged**: archived
rows excluded, exactly as every existing `...AndArchivedAtIsNull` list already does today. A new
optional `archived` boolean parameter is added:

- Omitted, or `archived=false` — active rows only (today's behavior, unchanged).
- `archived=true` — **archived rows only** (not a union of both).

**Not included in V1:** a combined "show active and archived together" mode. That would need a
third query shape (e.g. `archived=all`) with no clear caller need identified yet, and every
existing archive-exclusion convention in this codebase is already a strict either/or, not a
toggle-able union — this keeps `archived` consistent with that.

## 9. Custom fields

**[RESOLVED]** `ProjectCustomField` values are **not filterable in V1** — explicitly, not by
omission. Two independent reasons, either one sufficient alone: (a) `ProjectCustomField` was not
included in this document's V1 entity scope at all (§1); (b) even if it were, `value` is a
`text` column whose *meaning* depends on `valueType` (`TEXT`/`NUMBER`/`DATE`/`BOOLEAN`, per
`11-CUSTOMFIELD-SPEC.md` §4/§6) — a generic filter on it would need type-aware comparison logic
(numeric ordering for `NUMBER`, date parsing for `DATE`) that does not exist today and would be
exactly the "speculative custom-field query infrastructure" this task's instructions warn
against building ahead of a real need.

## 10. Database

**[RESOLVED — conservative: no migration]** Every field in §2 is an existing column from an
already-applied migration (`V3` Task, `V5` Risk, `V6` Issue, `V7` Decision) — no new column, no
new table, **no `V11` migration**.

**Indexing:** no new index is needed. Every filter query still starts from the same
`WHERE project_id = ? AND archived_at IS NULL` predicate every existing list query already uses,
which is already served by each table's `idx_*_project`/`idx_*_active` indexes (established in
their own migrations). The added `AND status = ?` / `AND priority = ?` / `AND assignee_id = ?` /
date-range predicate runs over that already-narrowed, small per-project row set — the identical
performance reasoning `13-SEARCH-SPEC.md` §8 already used for its own `ILIKE` predicate, which
needed no index either. A low-cardinality equality filter over a handful of rows needs nothing
beyond what's already there.

## 11. Performance

**[RESOLVED — conservative]** No new technology. Elasticsearch, caching, and any dedicated
filter-query infrastructure are all unnecessary at V1's realistic per-project row counts (the
same bounded-volume assumption every project-scoped entity's unbounded list already relies on —
established as far back as `04-API.md`'s original Task list, never revisited since). If a
project's row counts ever grow large enough to matter, that is a scaling question independent of
Filters' existence, not a reason to add infrastructure now.

## 12. Events / outbox / audit

**[ESTABLISHED]** Filters is entirely read-only — a `GET` with additional query parameters, no
state change of any kind. No domain events, no `WorkEventRecorder` integration, no outbox
messages, no `ProjectActivity` rows — identical to Search's own resolution
(`13-SEARCH-SPEC.md` §9) and to `DashboardApplicationService`'s precedent for cross-cutting,
read-only capabilities.

## 13. AI / automation

**[ESTABLISHED]** None. Filters is a deterministic set of query parameters interpreted exactly
as written — no natural-language-to-filter translation, no AI-suggested filters, no automation
triggered by a filtered view. Out of scope regardless of documentation, per standing project
instructions.

## 14. Testing

**[RESOLVED — conservative, extends each entity's existing test files, no new test class]**

- **`TaskApplicationServiceTest`** (additions): filtering by `status` alone; by `assigneeId`
  alone; by `dueDateBefore`/`dueDateAfter` alone and combined; combining two filters with `AND`;
  no filters supplied → identical result to today's `listTasks`; `archived=true` returns only
  archived rows.
- **`RiskApplicationServiceTest`** (additions): filtering by `status`; by `priority`; combined;
  no-filter parity; `archived=true`.
- **`IssueApplicationServiceTest`** (additions): filtering by `priority`; no-filter parity;
  `archived=true`.
- **`DecisionApplicationServiceTest`** (additions): filtering by `decidedBy`; no-filter parity;
  `archived=true`.
- **Controller test additions** (`TaskControllerTest`, `RiskControllerTest`,
  `IssueControllerTest`, `DecisionControllerTest`): query parameters are correctly bound and
  passed to the application service; an existing no-filter request's response is byte-for-byte
  unchanged from before this feature (explicit backward-compatibility test); an invalid enum
  value (e.g. `?status=NOT_A_STATUS`) produces the module's standard `ProblemDetail` error shape,
  not Spring's generic default — see **Errors**, below.
- **Persistence-level tests** (additions to `TaskPersistenceTest`, `RiskPersistenceTest`,
  `IssuePersistenceTest`, `DecisionPersistenceTest`, run against **real PostgreSQL**, the
  existing discipline): each new repository filter query, individually and combined, against
  real data — proving the `AND`-combination and the existing indexes actually satisfy it.
- **No new domain test class** — no domain/entity behavior changes; filtering is a pure
  repository/query-layer capability, the same shape Search's own testing section took.

**Errors — one implementation gap this document surfaces, not just tests for:**
`GlobalExceptionHandler` currently has no `@ExceptionHandler` for
`MethodArgumentTypeMismatchException` (confirmed: only `ProjectModuleException`,
`MethodArgumentNotValidException`, `ObjectOptimisticLockingFailureException` and a catch-all
`Exception` handler exist today). An invalid enum value in a new filter query parameter (e.g.
`?status=NOT_A_STATUS`) would therefore fall through to Spring Boot's default handling — a 400,
but **not** in this module's `ProblemDetail` shape (`https://errors.projectmodule/...`) every
other validation failure already uses. Recommending the implementation add one small, additive
`@ExceptionHandler(MethodArgumentTypeMismatchException.class)` to `GlobalExceptionHandler` for
consistency — a shared-infrastructure change, but a strictly additive one (existing callers
never trigger it, since they never send these new parameters), not a modification of any
existing endpoint's behavior.

## Explicit non-goals (V1)

- `OR` combination, nested filter groups (§4).
- `in`/`not in`, `not equals`, `contains` operators (§3).
- Filtering Phase, Milestone, TaskList, Subtask, Checklist, ProjectComment, ProjectCustomField,
  ProjectTemplate, or Project itself (§1).
- Filtering `ProjectCustomField` values (§9).
- Pagination or sorting changes to the four extended endpoints (§6).
- A combined "active + archived together" result (§8).
- Any new index, extension, caching layer, or search technology (§10, §11).
- Any AI/automation involvement (§13).

## Numbered resolutions (for your override before implementation)

Every genuinely ambiguous point, resolved conservatively above; listed together for a quick
final check:

1. **Scope (§1):** Task, Risk, Issue, Decision only — same as Search's approved V1 scope.
2. **Fields (§2):** only fields that already exist on those four entities; `phase`/`milestone`/
   `task list`/`project` filters excluded because those columns don't exist on these entities.
3. **Operators (§3):** equality only for enum/reference fields; before/after/on for dates; no
   `in`, `not equals`, or `contains`.
4. **Combination (§4):** `AND` only; no `OR`, no nested groups.
5. **API shape (§5):** extend the four existing list endpoints with optional query parameters;
   no new endpoint; response type unchanged.
6. **Pagination (§6):** unchanged — still unbounded `List<T>`, since changing that would modify
   an existing endpoint's response shape.
7. **Archived (§8):** `archived=true` shows archived-only, not a combined view; omitted/`false`
   is today's unchanged default.
8. **Custom fields (§9):** not filterable in V1, for two independent reasons.
9. **Database (§10):** no migration; no new index.
10. **Error handling (§14):** recommend adding one new `@ExceptionHandler` to
    `GlobalExceptionHandler` for consistent invalid-filter-value error responses — flagged as a
    small necessary implementation detail, not optional polish.

## Implementation-readiness

**Implementation-ready.** Every required section (purpose, scope, fields, operators,
combination, API, authorization, pagination/sorting, archive behavior, database impact, errors,
testing, non-goals, numbered resolutions) is present and resolved. No Java code, migration, or
tests have been written; this file is the only change made in this pass.
