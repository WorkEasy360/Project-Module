# Calendar (P2) — Specification

## Status of this document

**Resolved and implementation-ready.** `08-ROADMAP.md:26` lists `Calendar` under P2 — Views as a
single bullet. The only other "Calendar" hits anywhere in this codebase are the external
**Calendar Service** integration port (`CalendarPort.java`) and `01-SPEC.md` §4, which explicitly
lists "Company Calendar" under things the Project Module does **not** own — that is a different
concept (a company-wide calendar owned by another module) from this document's project-scoped
Calendar *view*, which only reads this module's own data.

Tags: **[ESTABLISHED]** = direct application of an existing convention.
**[RESOLVED — conservative]** = genuinely undefined; resolved conservatively, using only fields
that already exist.

## Purpose

**[RESOLVED — conservative]** A read-only, date-ordered view of this project's date-bearing
items, optionally windowed to a date range. **No new fields are added to any entity.** Only
entities that already carry a real date are included.

## 1. Which entities, and which date(s)

**[ESTABLISHED — from existing fields only]**

| Entity    | Date field(s) on the entity today | Included as |
|-----------|-------------------------------------|-------------|
| Task      | `dueDate` (nullable, single date)   | a point event on `dueDate` |
| Milestone | `dueDate` (nullable, single date)   | a point event on `dueDate` |
| Phase     | `startDate`, `endDate` (both nullable, independently) | a ranged event |

A Task/Milestone with a `null` `dueDate`, or a Phase with a `null` `startDate` or `null`
`endDate`, is excluded — there is no date to place it on a calendar with, and inventing a
fallback date would be fabricating data. No other entity (Risk, Issue, Decision, Dependency,
ProjectComment, ProjectCustomField) has any date field beyond `createdAt`/`updatedAt`
(audit timestamps, not scheduling dates) and is therefore out of scope.

## 2. API

**[RESOLVED — conservative]** New endpoint: `GET /api/v1/projects/{projectId}/calendar`, with
two optional query parameters `from` and `to` (`LocalDate`, ISO-8601). Both omitted returns every
active date-bearing item for the project (identical "no implicit limit" precedent as every other
unpaginated list endpoint in this module).

Response:
```java
record CalendarResponse(List<CalendarEntryResponse> entries)
record CalendarEntryResponse(
        String entityType,   // "TASK" | "MILESTONE" | "PHASE" — plain String,
                              // the same choice and rationale SearchResultResponse already used
                              // ("no existing cross-entity type discriminator to extend")
        UUID id,
        String name,
        LocalDate date,       // set for TASK/MILESTONE, null for PHASE
        LocalDate startDate,  // set for PHASE, null for TASK/MILESTONE
        LocalDate endDate)    // set for PHASE, null for TASK/MILESTONE
```

## 3. Range filter semantics

**[RESOLVED — conservative]**
- Task/Milestone (point date): included if `date` is non-null and
  `(from == null OR date >= from) AND (to == null OR date <= to)` — inclusive on both ends,
  matching `14-FILTERS-SPEC.md`'s own precedent that an *equality/range* filter (as opposed to
  its strict `dueDateBefore`/`dueDateAfter` operators) is the natural default for "does this item
  fall within this window."
- Phase (ranged date): included if `startDate`/`endDate` are both non-null and the ranges
  overlap: `(from == null OR endDate >= from) AND (to == null OR startDate <= to)`.

Only active (non-archived) items are included — consistent with every other list endpoint's
default; there is no "include archived" toggle for Calendar (Filters already owns that need on
the plain list endpoints).

## 4. Ordering

**[ESTABLISHED]** Ascending by the entry's primary date (`date` for Task/Milestone, `startDate`
for Phase), ties broken by `name` — deterministic, matches the "ORDER BY ... ASC" convention
used everywhere else in this module.

## 5. Implementation shape

**[ESTABLISHED]** Merge-in-application-layer, not a DB `UNION` — the exact approach
`SearchApplicationService` already established and documented as this module's "approved V1
approach" for combining results across entity types. Three independent repository queries
(new derived-name query methods: Task/Milestone `findByProjectIdAndDueDateIsNotNullAnd...`, Phase
`findByProjectIdAndStartDateIsNotNullAndEndDateIsNotNullAnd...`, each `ArchivedAtIsNull`), mapped
to `CalendarEntryResponse`, concatenated, filtered by the range predicate above, then sorted.

## 6. Authorization

**[ESTABLISHED]** Same `VIEW_PROJECT` check as every other project-scoped read.

## 7. Testing

**[ESTABLISHED]** Application-service tests (each entity type mapped correctly, range filtering
inclusive/exclusive boundaries, null-date items excluded, archived excluded, merge/sort order,
authorization), controller tests (query param binding, both omitted, invalid date format → 400),
persistence-level coverage via the new derived-name repository query methods against real
PostgreSQL.

## 8. Out of scope

Recurring events, timezone handling beyond `LocalDate` (no time-of-day field exists on any
entity), calendar subscription/ICS export, any entity without a real date field, pagination.
