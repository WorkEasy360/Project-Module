# Delay Detection (P3) — Specification

## Status of this document

**Resolved and implementation-ready.** `08-ROADMAP.md:38` lists `Delay detection` under
P3 — Intelligence as a single bullet. This task's own instructions for this feature explicitly
say: *"If deterministic delay detection is sufficiently supported, implement it using existing
data"* — it is: "delayed" is defined below using only fields that already exist, with no
invented threshold, weight, or prediction.

Tags: **[ESTABLISHED]** = direct application of an existing convention.
**[RESOLVED — conservative]** = genuinely undefined; resolved conservatively.

## 1. Relationship to existing manual status fields — read-only, no mutation

**[ESTABLISHED]** `TaskStatus.java`'s own javadoc already states: *"`OVERDUE` and `BLOCKED` are
set explicitly, by a person... there is no scheduler or automated delay detection in this slice
(that is P3 Intelligence)."* This document **is** that P3 slice, and it deliberately stays
read-only: it never sets `Task.status` to `OVERDUE`, never mutates any entity, and does not
change the existing manual-only transition rules in `TaskApplicationService`/
`MilestoneApplicationService`. It only **reports** which items are, by a purely date-based
definition, currently overdue — independent of whatever status a person has (or hasn't) set.

## 2. Definition of "delayed" (per entity, deterministic, from existing fields only)

**[RESOLVED — conservative]**

| Entity    | Delayed when (all conditions, using today's date at query time) |
|-----------|--------------------------------------------------------------|
| Task      | `dueDate` is non-null, `dueDate < today`, `status != COMPLETED`, `archivedAt IS NULL` |
| Milestone | `dueDate` is non-null, `dueDate < today`, `status != COMPLETED`, `archivedAt IS NULL` |
| Phase     | `endDate` is non-null, `endDate < today`, `archivedAt IS NULL` |

Phase has no status/completion field at all (confirmed by direct inspection: `projectId`,
`name`, `description`, `startDate`, `endDate`, `archivedAt`, `archivedBy` — nothing else), so
its delay definition cannot exclude "already completed" the way Task/Milestone's can. This is
stated explicitly rather than papered over: a Phase past its `endDate` is reported as delayed
for as long as it stays active, with no way to mark it "done" in the current data model.

A `BLOCKED`/`OVERDUE`-status task with no `dueDate` at all is **not** reported here — with
nothing to compare against today's date, "delayed" cannot be determined without guessing.

## 3. API

**[RESOLVED — conservative]** New endpoint: `GET /api/v1/projects/{projectId}/delayed`.

Response:
```java
record DelayedItemsResponse(List<DelayedItemResponse> items)
record DelayedItemResponse(
        String entityType,  // "TASK" | "MILESTONE" | "PHASE" — same plain-String precedent
                             // SearchResultResponse/CalendarEntryResponse already use
        UUID id,
        String name,
        LocalDate dueDate,  // Task/Milestone's dueDate, or Phase's endDate
        long daysOverdue)   // today - dueDate, always > 0 by construction
```

## 4. Ordering

**[ESTABLISHED]** Ascending by `dueDate` — the oldest (most overdue) items first, the same
"oldest/most-severe first" convention that reads naturally for an intelligence report, and
deterministic; ties broken by `name`.

## 5. Implementation shape

**[ESTABLISHED]** New derived-name repository query methods (no `@Query` needed, the same
preference every existing repository in this module already shows for simple conditions over
`@Query`): `TaskRepository.findByProjectIdAndDueDateBeforeAndStatusNotAndArchivedAtIsNull(...)`,
the Milestone equivalent, and
`PhaseRepository.findByProjectIdAndEndDateBeforeAndArchivedAtIsNull(...)`. Merged in the
application layer — the same "merge in application layer, not a DB `UNION`" approach
`SearchApplicationService` already established as this module's approved style for combining
results across entity types.

## 6. Authorization

**[ESTABLISHED]** Same `VIEW_PROJECT` check as every other project-scoped read.

## 7. Testing

**[ESTABLISHED]** Application-service tests (each entity's delay condition, boundary at exactly
`today` — not delayed — completed items excluded, archived excluded, merge/sort order,
`daysOverdue` computed correctly, authorization), controller test (response shape), persistence
tests against real PostgreSQL for the three new derived-name query methods.

## 8. Out of scope

Any prediction or trend analysis (this is a deterministic point-in-time report, not a forecast —
"P3 is intelligence, not AI" per this task's own instructions), any notification/alerting on
delay, any change to `Task`/`Milestone` status transition rules, Risk/Issue/Decision (none of
these have a due-date-style field to be "delayed" against).
