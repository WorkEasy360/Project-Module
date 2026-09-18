# Gantt (P2) — Specification

## Status of this document

**Resolved and implementation-ready.** This document previously classified Gantt as
implementation-blocked (§ below, "Prior blocking analysis") because a real Gantt chart
conventionally needs both duration bars and dependency arrows on the *same* entity, and Task
(the entity `Dependency` connects) has no start date. The product owner has since **approved an
explicit, reduced scope** that works entirely within the existing schema — this section records
that approval; §1–§7 below are the resulting implementation-ready specification.

**[APPROVED]** Decisions, verbatim from the approval:
1. Do not add `startDate` to `Task`. No new database column, table, or migration.
2. No new Maven dependency.
3. Phase is rendered as a real bar (`startDate`→`endDate`) — the one entity that already has a
   duration.
4. Task is rendered as a **point event at `dueDate`**, not a fabricated bar — the literal
   reading of the one date Task actually has.
5. Milestone is rendered as a point event at `dueDate` (unchanged from Calendar/Timeline's
   existing treatment).
6. Existing Task `Dependency` edges are exposed in the response, for the frontend to draw
   arrows between task points, even though those points aren't bars.
7. Read-only: no mutation endpoint, no AI, no new infrastructure.
8. Same `VIEW_PROJECT` authorization and organization boundary as every other project-scoped
   read.

This intentionally departs from a "textbook" Gantt chart (bars connected by arrows on one
entity) — it is documented as a deliberate, approved trade-off, not a silent reinterpretation.

## 1. Prior blocking analysis (superseded by the approval above, kept for context)

A Gantt chart conventionally renders **bars** (duration) connected by **dependency arrows** on
the same entity. Phase has a real duration (`startDate`/`endDate`) but Dependency links Tasks,
not Phases; Task has only `dueDate`, no duration. Adding `Task.startDate` was the only way to
get a "real" Gantt, and was explicitly rejected by the approval above. The approved design
below renders Phase as bars, Task/Milestone as points, and exposes Task dependency edges
separately — the closest Gantt-shaped view obtainable from the existing schema.

## 2. Response shape

**[APPROVED — conservative]**
```java
record GanttResponse(
        List<GanttBarResponse> phases,
        List<GanttPointResponse> tasks,
        List<GanttPointResponse> milestones,
        List<DependencyResponse> dependencies)   // reuses the existing DependencyResponse

record GanttBarResponse(UUID id, String name, LocalDate startDate, LocalDate endDate)
record GanttPointResponse(UUID id, String name, LocalDate date)
```

## 3. Contents and ordering

**[ESTABLISHED]** Reuses the exact repository queries Calendar already added — no new query is
needed for Gantt:

- `phases`: active phases with both `startDate` and `endDate` set
  (`PhaseRepository.findByProjectIdAndStartDateIsNotNullAndEndDateIsNotNullAndArchivedAtIsNullOrderByStartDateAsc`,
  already exists), ordered by `startDate` ascending. A phase missing either date is excluded —
  it cannot be drawn as a bar (§1's "do not invent dates").
- `tasks`: active tasks with a non-null `dueDate`
  (`TaskRepository.findByProjectIdAndDueDateIsNotNullAndArchivedAtIsNullOrderByDueDateAsc`,
  already exists), ordered by `dueDate` ascending.
- `milestones`: active milestones with a non-null `dueDate`
  (`MilestoneRepository.findByProjectIdAndDueDateIsNotNullAndArchivedAtIsNullOrderByDueDateAsc`,
  already exists), ordered by `dueDate` ascending.
- `dependencies`: every active `Dependency` touching this project's active tasks
  (`DependencyRepository.findByDependentTaskIdInAndArchivedAtIsNull`, already exists — the same
  task-id-set indirection `DependencyAnalysisApplicationService` already established, since
  `Dependency` has no `projectId` column), mapped via the existing `DependencyMapper`. Both
  broken and unbroken active dependencies are included — the existing `broken` field on
  `DependencyResponse` already lets a caller distinguish them; Gantt does not filter that
  decision away.

## 4. API

**[APPROVED — conservative]** New endpoint: `GET /api/v1/projects/{projectId}/gantt`.

## 5. Authorization

**[ESTABLISHED]** Same `VIEW_PROJECT` check as every other project-scoped read.

## 6. Testing

**[ESTABLISHED]** Application-service tests (each of the four lists populated/ordered/filtered
correctly, dependencies scoped to the project's own tasks, authorization enforced), controller
test (response shape). No new repository query methods are added, so no new persistence test is
required beyond what Calendar's/Dependency Analysis's existing persistence tests already cover
for the reused queries.

## 7. Out of scope

`Task.startDate` or any other schema change (§ "Prior blocking analysis"), dependency *lag*/
duration, critical-path computation, drag-to-reschedule (existing `PATCH` endpoints already
cover editing dates), any mutation endpoint, AI-assisted scheduling.

## 8. Classification

**IMPLEMENTED**, per the approval in this document's Status section.
