# Search (P2) — Approved Specification

## Status of this document

**Approved.** All five open decisions below were presented and confirmed by the project owner,
each choosing the recommended option. Following the same process `09`–`12` established:
`08-ROADMAP.md:30` lists `Search` under P2 — Views as a single bullet, with **zero** other
mention anywhere in any of the 12 prior docs, and zero existing search infrastructure anywhere in
the codebase (confirmed by a full-codebase search for `LIKE`/`ILIKE`/`Specification<`/
`CriteriaBuilder` before writing this). It is now this module's specification for Search, to be
followed exactly the same way `01-SPEC.md` through `12-TEMPLATE-SPEC.md` are — implementation may
proceed from it once explicitly requested.

**This document is structurally different from `09`–`12`.** Those each specified one new
persisted entity. Search is **not a new entity** — it is a **read-only query capability over
data that already exists**, per your explicit framing. Consequently:

- There is **no new Flyway migration** in V1 (no new table) — only new derived-query methods on
  four existing repositories. No new PostgreSQL extension or index type either (Decision #5).
- The central question was not "what fields does it have" but **"what does it search, and at
  what scope"** — resolved as Decision #1/#2 below.

Per your instruction, findings were marked:

- **A. Existing documented decisions** — a direct, mechanical application of a convention already
  established elsewhere in this codebase. Not a new design choice.
- **B. Approved decisions** — was genuinely undefined; now confirmed by the project owner. The
  surrounding text is left as originally proposed (it already described the approved,
  recommended option); see the final section for the confirmed outcome of each.
- **C. Recommended decisions** — where retained below, this was the proposal's recommendation
  that became the approved decision (B).

## 1. Search scope

**B — APPROVED: project-scoped (Decision #1).** Nothing in any doc documented what Search
searches, or at what scope. Two real options existed, each with genuine precedent already in
this codebase:

**Option (a) — Project-scoped.** `GET /api/v1/projects/{projectId}/search?q=...`, gated by
`ProjectPermission.VIEW_PROJECT` on that one project — exactly the authorization every
work-management sub-entity's own list endpoint already requires (`TaskApplicationService.listTasks`,
`RiskApplicationService.listRisks`, etc. each call `requirePermission(..., projectId, VIEW_PROJECT)`
once before returning that project's rows). Search, under this option, is a cross-*entity-type*,
single-*project* capability: one call searches Tasks, Risks, Issues and Decisions *within* one
project the caller can already see.

**Option (b) — Organization-wide.** `GET /api/v1/search?q=...`, spanning every project in the
caller's organization, with **no per-project permission check** — mirroring
`DashboardApplicationService.getDashboard`'s own precedent exactly: it is deliberately *not*
gated through `ProjectAuthorizationService`, because (its own Javadoc) *"that interface decides
permission on one project, and this summary spans every project in the organization, so there is
no single `projectId` for it to check against."* A single search bar across the whole
organization is arguably the more natural reading of "Search" as a top-level P2 capability.

**Approved: (a), project-scoped**, despite Dashboard's real precedent for (b), for one concrete
reason: **Dashboard only ever returns aggregate counts** — never an individual project's name,
description, or any other content. Search, by contrast, returns actual entity titles/descriptions.
Applying Dashboard's "no per-project check" reasoning to content instead of counts would mean a
caller who is only a `VIEWER` on Project A (or not a member of any project yet) could see
Task/Risk/Issue/Decision titles from Project B in the same organization purely because both share
an organization — weakening the exact guarantee `VIEW_PROJECT` exists to enforce everywhere else
in this module. This keeps Search's authorization strength identical to browsing any one
project's existing Task/Risk/Issue/Decision lists today.

**Rejected alternative:** (b), organization-wide, with no per-project permission check.

**Everything below reflects (a).**

## 2. Search fields — which resources, which fields

**B — APPROVED: Task, Risk, Issue, Decision only (Decision #2).** "Do not assume all entities
unless justified" — justification below, entity by entity, based on which existing entities
actually have a free-text field and fit the project-scoped frame (§1):

| Entity | Free-text field(s) | In V1 scope? | Why |
|---|---|---|---|
| `Task` | `name`, `description` | **Yes** | The most directly "searched-for" work item in comparable tools. |
| `Risk` | `name`, `description` | **Yes** | Same shape as Task; genuinely free text. |
| `Issue` | `name`, `description` | **Yes** | Same shape as Task; genuinely free text. |
| `Decision` | `name`, `description` | **Yes** | Same shape as Task; genuinely free text. |
| `Phase` | `name`, `description` | Recommended **out of V1** | Few per project (typically single digits); browsing already suffices; adding it is a small later extension, not a design change. |
| `Milestone` | `name`, `description` | Recommended **out of V1** | Same reasoning as Phase. |
| `TaskList` | `name`, `description` | Recommended **out of V1** | Same reasoning as Phase. |
| `Subtask` | `name`, `description` | Recommended **out of V1** | Nested under a Task; searching it separately from its parent Task adds real complexity (does a Subtask match surface its parent Task in results?) for a nested, typically-few-per-Task record. |
| `Checklist` | `text` only (no `name`) | Recommended **out of V1** | Same nesting concern as Subtask, plus a different field shape (single `text`, not `name`+`description`) that would need its own result-mapping case. |
| `ProjectComment` | `body` only (no `name`) | Recommended **out of V1** | A discussion feed, not a "thing with a title" the way Task/Risk/Issue/Decision are; also already paginated separately (`10-COMMENT-SPEC.md`). |
| `ProjectCustomField` | `name` (yes); `value` (typed — `TEXT`/`NUMBER`/`DATE`/`BOOLEAN`) | **Out of V1** | `value`'s meaning depends on `valueType` (`11-CUSTOMFIELD-SPEC.md` §4); free-text-matching a `NUMBER`/`DATE`/`BOOLEAN` value as if it were text is not meaningful, and partial inclusion (only `TEXT`-typed fields) is a special case not worth V1's complexity. |
| `Dependency` | none | **Excluded entirely** | `Dependency`'s own fields are just two task ids and a `broken` flag (`V4__create_dependencies.sql`) — no `name`/`description`, nothing to match against. |
| `Project`, `ProjectTemplate` | `name`, `description` | **Excluded entirely** | Both organization-scoped (§1's Option (a) is project-scoped); a project-scoped search endpoint cannot sensibly search the projects/templates collection itself. |

**Approved V1 field set: `Task`, `Risk`, `Issue`, `Decision` only**, each matched on `name`
**and** `description` (§3). This is the minimal set that gives Search real utility (the four
entities most analogous to "things you'd search for" in comparable tools) without deciding six
more questions (nested-record surfacing, typed-value matching, discussion-feed semantics) that
each other entity in the table above would otherwise raise. All other entities listed above
remain out of scope for V1.

## 3. Matching behavior

**A/C — low-controversy consequences of §1/§2, not separately listed as open decisions:**

- **Case-insensitive.** `ILIKE`-equivalent matching (Spring Data derived query
  `...ContainingIgnoreCase`), not a case-sensitive comparison — matches the ordinary expectation
  of "search," and there is no existing case-sensitive-text-matching precedent anywhere in this
  codebase to prefer instead.
- **Partial (substring) matching, not exact.** `name`/`description` containing the query term
  anywhere, not equal to it — again, the ordinary meaning of "search" versus a filter/lookup.
- **Multiple fields searched with OR logic.** A term matches an entity if it appears in `name`
  **or** `description` — not requiring both.
- **Multiple entity types searched, results merged.** A single call searches Task, Risk, Issue
  and Decision together (§2), not one type at a time.
- **Blank/empty query is rejected**, not treated as "match everything" or "match nothing" —
  `BusinessRuleViolationException` (the same exception type and HTTP 422 every other
  business-rule rejection in this module already uses, e.g. `12-TEMPLATE-SPEC.md`'s
  null-`defaultPriority`-on-apply rule), raised in the application layer (not via a Bean
  Validation annotation on the `@RequestParam` — this codebase's validation pattern is
  consistently domain/application-layer, via `@RequestBody @Valid` for bodies and manual checks
  for everything else; introducing `@Validated`-on-query-parameter would be a new mechanical
  pattern with no existing precedent to extend).
- **No minimum query length** in V1 (e.g. no "must be at least 3 characters" rule) — not
  documented anywhere, and inventing an arbitrary threshold is exactly the kind of undocumented
  business rule this proposal should not silently add. Flagged as low-stakes; can be added later
  without changing the API contract if short queries prove to be a real problem.
- **No relevance ranking / scoring.** Results are not ordered by "how well" they match (that
  would require a real text-search engine — see §8) — ordered by `updatedAt` descending instead
  (§4), the most recently touched matching record first.

## 4. API contract

**A/C — established shape, low-controversy consequence of §1/§2:**

```
GET /api/v1/projects/{projectId}/search?q={term}&page=&size=&sort=
```

- **`q`** (required, non-blank): the search term. Rejected per §3 if blank.
- **`page`/`size`**: standard Spring Data `Pageable` query parameters, the same convention
  `GET /projects` already exposes (`@PageableDefault`).
- **`sort`**: accepted for consistency with every other paginated endpoint, but a merged
  cross-entity-type result can only meaningfully sort by a field every result shares — in
  practice `updatedAt`/`createdAt` (§3). Sorting by an entity-type-specific field (e.g. `name`)
  is not proposed for V1.
- **Response**: `PageResponse<SearchResultResponse>` — the **existing** `PageResponse<T>`
  wrapper (`common/api/PageResponse.java`, already used by `GET /projects`), not a new pagination
  shape. `SearchResultResponse` is a **new, minimal DTO** (nothing else in this codebase returns
  a heterogeneous result set, so there is no existing type to reuse for the result item itself):

  ```java
  record SearchResultResponse(
      String resourceType,   // "TASK" | "RISK" | "ISSUE" | "DECISION"
      UUID id,
      UUID projectId,
      String name,
      String description,    // may be null, same as every entity's optional description
      boolean archived,      // always false in V1 — see §7
      Instant createdAt,
      Instant updatedAt)
  ```

  **APPROVED (Decision #4):** `resourceType` is a plain `String` — `"TASK"`, `"RISK"`,
  `"ISSUE"`, or `"DECISION"` — not a shared enum. There is no existing cross-entity type
  discriminator anywhere in this codebase to extend, and inventing one (e.g. a
  `SearchableResourceType` enum touching four otherwise-unrelated entities' packages) would be
  new shared infrastructure for a single read-only endpoint.

- **Validation/error behavior**: blank `q` → `BusinessRuleViolationException` → HTTP 422 (§3).
  Unknown/inaccessible `projectId` → `ResourceNotFoundException` → HTTP 404, identical to every
  other entity's cross-organization-boundary behavior. No `VIEW_PROJECT` → `AuthorizationException`
  → HTTP 403, identical to every other project-scoped list.

**B — APPROVED: application-layer merge (Decision #3).** There is no existing precedent in this
codebase for a single paginated response merging rows from more than one table (every existing
paginated endpoint — `GET /projects`, and this specification's own `GET /templates`, per
`12-TEMPLATE-SPEC.md` Decision #5 — paginates exactly one repository's `Page<T>`). Two ways to
build it were considered:

**(a) Application-layer merge — approved.** `SearchApplicationService` calls a new
`search(projectId, term)` derived-query method on each of the four existing repositories
(`TaskRepository`, `RiskRepository`, `IssueRepository`, `DecisionRepository`) — each already
filtered by `project_id` and `archived_at IS NULL` using their existing indexes (§8) — collects
the results, sorts the combined list by `updatedAt` descending, and paginates that in-memory
list. This is honest about its limits (§8) but adds no new query infrastructure, and per-project
row counts for these four entities are inherently bounded (dozens to low hundreds in realistic
use, the same assumption every other project-scoped entity's unbounded-list convention already
makes).

**(b) A single SQL `UNION ALL` query across all four tables — rejected for V1.** Would let the
database do the sort-then-paginate directly, avoiding the in-memory merge's scaling limit — but
there is no existing precedent anywhere in this codebase for a cross-table JPQL/native `UNION`
query (the one `@Query` usage that exists, `ProjectRepository.countActiveByStatus`, is a
single-table `GROUP BY`), and it would need hand-written native SQL mapped into a projection, a
new pattern this module does not currently have.

(a) is approved for V1; (b) remains the natural upgrade path if search result volumes ever make
the in-memory merge a real problem (§8), a future decision, not part of this specification.

## 5. Authorization

**A — established, reusing existing infrastructure exactly, no new mechanism.** Under §1's
approved project scope: `ProjectApplicationService.findActiveProjectInCallerOrganization`
resolves the organization boundary and existence check (the identical pattern every
work-management application service already uses), then
`ProjectAuthorizationService.requirePermission(userId, projectId, ProjectPermission.VIEW_PROJECT)`
gates the call — the same permission `listTasks`/`listRisks`/`listIssues`/`listDecisions` each
already require individually. No new `ProjectPermission` value, no new authorization mechanism.

## 6. Organization/project boundary

**A — established, identical to every entity.** `projectId` comes from the URL path;
`findActiveProjectInCallerOrganization` ensures it belongs to the caller's own organization
before anything is searched (a `projectId` from a different organization is reported not found,
not forbidden — consistent with the existing rule that a project's existence is not revealed
across the tenant boundary). Because Search never accepts an organization id directly (only a
`projectId`, resolved server-side to its owning organization) and only queries the four
repositories' `findByProjectId...` methods for that one already-boundary-checked project, there
is no path for it to return data from a project outside that boundary.

## 7. Archived records

**A/C — excluded, no toggle.** Every existing `...AndArchivedAtIsNull` derived query
already excludes archived rows from the standard read surface across all four entities; Search's
new `search(...)` repository methods add the same `AND archived_at IS NULL` predicate, so an
archived Task/Risk/Issue/Decision never appears in results. `SearchResultResponse.archived` is
therefore always `false` in V1 — kept in the response shape for forward-compatibility (so a
future "include archived" toggle would not need a breaking response-shape change), but not
settable in V1. No `includeArchived` query parameter is proposed for V1: nothing in any doc asks
for one, and every other entity's default list behavior already excludes archived rows with no
opt-in flag either.

## 8. Performance / indexing

**B — APPROVED: reuse existing btree indexes; no new PostgreSQL extension, no new
infrastructure (Decision #5).** Each of the four searched entities already has `idx_*_project` (on
`project_id`) and `idx_*_active` (`project_id` `WHERE archived_at IS NULL`) from their own
migrations (`V3`, `V5`, `V6`, `V7`). The new `search(...)` query's `WHERE project_id = ? AND
archived_at IS NULL` clause uses these existing indexes to narrow to one project's active rows
first; the `name ILIKE '%term%' OR description ILIKE '%term%'` portion cannot itself use a plain
btree index (a leading-wildcard `LIKE` never can) and performs a sequential scan **over the
already-narrowed, per-project row set** — acceptable given realistic per-project volumes (§4),
the same sizing assumption every other project-scoped entity's unbounded list already makes.

**Explicitly not proposed for V1, per your instruction to avoid unnecessary infrastructure:**
Elasticsearch or any external search engine (nothing in `06-INTEGRATION.md` names one, and it
would be a wholly new external dependency this module has never had); PostgreSQL full-text
search (`tsvector`/`tsquery`) or `pg_trgm` trigram GIN indexes — both are legitimate, lighter-weight
PostgreSQL-native options *and* neither is unreasonable, but both are also genuinely new
infrastructure (a new column type or a new extension `CREATE EXTENSION pg_trgm`) with no current
precedent in this codebase, and V1's realistic data volumes don't yet justify them. Recorded as
the natural future enhancement if search performance becomes a measured problem, not a V1
requirement.

## 9. Consistency

**A — established, no second data model.** Search introduces no new table, no new domain entity,
and no new business rule of its own. It reads through **new query methods added to the four
existing repositories** (`TaskRepository`, `RiskRepository`, `IssueRepository`,
`DecisionRepository`), never bypassing or duplicating each entity's own domain invariants —
because Search is read-only, there is no invariant to bypass (creation/validation/status-transition
rules all remain solely in each entity's own domain class, untouched by this proposal). This
mirrors `DashboardApplicationService`'s own precedent exactly: a cross-cutting, read-only
capability that queries existing repositories directly rather than duplicating their data.

## 10. Testing

**A/C — established four-layer shape, adapted for a cross-entity read-only capability:**

- `SearchApplicationServiceTest` (Mockito): mocks all four repositories' new `search(...)`
  methods; asserts results are merged and sorted by `updatedAt` descending; blank `q` rejected;
  `VIEW_PROJECT` denial; cross-organization denial (via `ProjectApplicationService` mock);
  archived rows never appear (covered by asserting the repository methods are always called with
  the `ArchivedAtIsNull` predicate, not by testing exclusion logic in the service itself, since
  exclusion is the repository query's job).
- `SearchControllerTest` (`@WebMvcTest`): the endpoint, pagination parameters, blank-`q` → 400/422
  mapping, response shape (`resourceType` per result).
- **Repository-level tests** (added to each of `TaskPersistenceTest`, `RiskPersistenceTest`,
  `IssuePersistenceTest`, `DecisionPersistenceTest` — no new persistence test class, since no new
  table exists): the new `search(...)` derived-query method against **real PostgreSQL**, per the
  existing discipline — case-insensitivity, partial match, `name`-or-`description` matching,
  archived-exclusion, cross-project exclusion (a term matching a row in a *different* project
  must not appear).
- No new domain test class (`Search` is not an entity — there is no `SearchTest.java`).

## Open decisions — resolved via approval

All five were presented and **confirmed, each choosing the recommended option**:

1. **Search scope (§1). APPROVED: project-scoped**
   (`GET /api/v1/projects/{projectId}/search?q=...`), `VIEW_PROJECT`-gated, matching every
   existing work-management list endpoint's authorization strength. Rejected alternative:
   organization-wide (`GET /search`), no per-project check, mirroring
   `DashboardApplicationService`'s precedent but exposing individual entity content (not just
   counts) without a per-project permission gate.
2. **Which entities are searched (§2). APPROVED: `Task`, `Risk`, `Issue`, `Decision` only**,
   matched on `name` + `description`. Rejected alternative: including some or all of
   Phase/Milestone/TaskList/Subtask/Checklist/`ProjectComment`/`ProjectCustomField`.
3. **Cross-entity result merging (§4). APPROVED: application-layer merge** of four independent
   repository queries, sorted by `updatedAt` descending and paginated in memory. Rejected
   alternative: a single native SQL `UNION ALL` query.
4. **`resourceType` representation (§4). APPROVED: a plain `String`** (`"TASK"`, `"RISK"`,
   `"ISSUE"`, `"DECISION"`), not a new shared enum.
5. **Indexing strategy (§8). APPROVED: reuse existing btree indexes only** — no `pg_trgm`, no
   full-text search, no Elasticsearch, no new search infrastructure in V1. Rejected alternative:
   add `pg_trgm` trigram GIN indexes now rather than as a future enhancement.

Every other section in this document already reflected these outcomes (each was written as the
approved option) — §5, §6, §9, §10 are fixed codebase-wide conventions applied to a read-only
cross-entity capability, and §3, §7 are direct, low-controversy consequences of how §1/§2 were
resolved. None require further change as a result of this approval.

## Implementation-readiness

**Implementation-ready.** All five open decisions are resolved (above). No contradictory
wording remains: project-scoped authorization and the organization boundary (§5, §6), archived
records excluded with no toggle (§7), blank `q` rejected via the existing
`BusinessRuleViolationException` → HTTP 422 behavior (§3, §4), `PageResponse<SearchResultResponse>`
(§4), application-layer merging (§4), and the no-new-migration/no-new-infrastructure direction
(§8, "Status of this document") are all stated consistently throughout. No Java code, SQL
migration, or tests have been written yet; this file is the only change made in this pass.
