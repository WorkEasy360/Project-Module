# Dependency Analysis (P3) — Specification

## Status of this document

**Resolved and implementation-ready**, scoped narrowly using an explicit, already-documented
signal (§1) rather than guessing among the five candidate areas this task's own instructions
named (chains, blocked work, broken dependencies, cycle analysis, impact analysis).
`08-ROADMAP.md:39` lists `Dependency analysis` under P3 — Intelligence as a single bullet.

Tags: **[ESTABLISHED]** = direct application of an existing convention, or of a gap already
documented elsewhere in this codebase. **[RESOLVED — conservative]** = genuinely undefined;
resolved conservatively.

## 1. Scope, chosen from an existing, already-written breadcrumb

**[RESOLVED — conservative]** `Dependency.java` and `DependencyApplicationService.java` both
already carry a javadoc stating that deep transitive **cycle detection** across the whole
dependency graph is explicitly **not** implemented at creation time (only same-pair,
same-project, and direct-reverse-pair duplicates are rejected there), citing
`08-ROADMAP.md`'s "Dependency analysis" as the P3 feature meant to cover it. That is the
strongest, most concrete evidence in this codebase for what "Dependency analysis" was always
meant to include, so this document scopes to exactly the three things directly grounded in
already-existing fields/precedent:

1. **Cycle detection** — the gap the existing javadocs already name.
2. **Blocked-task detection** — `broken` and archive state already exist on `Dependency`; asking
   "which tasks currently have an unmet prerequisite" is a direct, mechanical read of data that
   already exists, no new field needed.
3. **Broken-dependency listing** — `Dependency.broken` already exists as a field
   (`markBroken()`), set manually via the existing `PATCH /dependencies/{id}`; this only exposes
   a project-scoped list of what's already been marked broken.

**Explicitly excluded**: *impact analysis* ("what breaks if I archive/delete this task") — no
existing field, event, or precedent grounds a specific definition of "impact," and guessing one
would be exactly the invention this task's instructions forbid ("Do not assume which ones are
required").

## 2. Definitions (deterministic, from existing fields only)

**[RESOLVED — conservative]**

- **Blocked task**: an active (non-archived) task in this project that is the `dependentTaskId`
  of at least one active (non-archived), non-`broken` `Dependency` whose `prerequisiteTaskId`
  points to a task that is **not** `TaskStatus.COMPLETED` (and is itself active). A prerequisite
  that is archived is treated the same as one that is not completed (its work was never
  finished, from this project's perspective) — archiving a task is not a completion signal in
  this module (they are two independent fields).
- **Broken dependency**: an active `Dependency` with `broken = true`, restricted to dependencies
  whose `dependentTaskId` belongs to this project (Dependency has no `projectId` column of its
  own — it is scoped indirectly through its tasks, exactly the same indirection
  `DependencyApplicationService`'s existing authorization check already uses:
  resolve the task first, then use *its* `projectId`).
- **Cycle**: a set of active, non-broken dependencies for this project's tasks whose
  `dependentTaskId → prerequisiteTaskId` edges form a directed cycle. Detected with a standard
  DFS-based cycle search over that edge set — a pure computation, no new persistence, no new
  event.

## 3. API

**[RESOLVED — conservative]** New endpoint:
`GET /api/v1/projects/{projectId}/dependencies/analysis`.

Response:
```java
record DependencyAnalysisResponse(
        List<List<UUID>> cycles,             // each inner list = one cycle's task ids, in order
        List<UUID> blockedTaskIds,
        List<DependencyResponse> brokenDependencies)  // reuses the existing DependencyResponse
```

## 4. Implementation shape

**[ESTABLISHED]** All active task ids for the project are fetched first (existing
`TaskRepository.findByProjectIdAndArchivedAtIsNullOrderByCreatedAtAsc`), then every active
dependency touching those tasks is fetched with one new derived-name query,
`DependencyRepository.findByDependentTaskIdInAndArchivedAtIsNull(Collection<UUID> taskIds)` —
the same "resolve the task set first, then query dependencies by that set" indirection
`DependencyApplicationService` already documents as necessary since `Dependency` has no
`projectId` column. Blocked-task and broken-dependency detection, and the cycle search, are all
computed in the application layer from that one fetched edge set — merge-in-application-layer,
the same approved style `SearchApplicationService` already established.

## 5. Authorization

**[ESTABLISHED]** Same `VIEW_PROJECT` check as every other project-scoped read.

## 6. Testing

**[ESTABLISHED]** Application-service tests (blocked-task detection with a completed vs.
not-completed vs. archived prerequisite, broken-dependency listing, a real 2-node and 3-node
cycle detected, no false-positive cycle on a simple chain, authorization), controller test
(response shape), persistence test for the one new derived-name query method against real
PostgreSQL.

## 7. Out of scope

Impact analysis (§1), any change to dependency creation-time validation (cycle prevention *at
creation* is a separate, larger decision — e.g. would it need to walk the whole graph on every
create call, a performance/behavior change to an already-shipped endpoint — this document only
adds a read-only report, it does not change `DependencyApplicationService.createDependency`),
dependency *lag*/duration concepts (`19-GANTT-SPEC.md` §4 already flagged this as a separate,
undecided feature).
