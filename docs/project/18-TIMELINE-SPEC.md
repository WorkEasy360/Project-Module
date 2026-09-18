# Timeline (P2) — Specification

## Status of this document

**Resolved and implementation-ready.** `08-ROADMAP.md:27` lists `Timeline` under P2 — Views as a
single bullet with no elaboration elsewhere.

Tags: **[ESTABLISHED]** = direct application of an existing convention.
**[RESOLVED — conservative]** = genuinely undefined; resolved conservatively.

## Purpose

**[RESOLVED — conservative]** A read-only, chronological structure of a project's phases (each
with its real `startDate`/`endDate` range and its nested milestones) plus the project's tasks
ordered by due date. It reuses exactly the same underlying data as `17-CALENDAR-SPEC.md`; the
difference is *shape* — Calendar returns a flat, date-windowed list; Timeline returns a
phase-nested structure with no date window (the whole project's timeline at once), matching how
these two view types are conventionally consumed differently by a frontend even though they draw
on identical source data.

## 1. A real structural constraint — stated explicitly, not invented around

**[ESTABLISHED]** `Task` has **no** `phaseId` (or `taskListId`) field — confirmed by reading
`Task.java` in full: its only fields are `projectId`, `name`, `description`, `dueDate`,
`assigneeId`, `status`, `archivedAt`, `archivedBy`. `Milestone` **does** have a nullable
`phaseId`. This means: milestones can be correctly nested under their phase; tasks **cannot** be
attributed to a phase by the current data model. Rather than inventing a `phaseId` on `Task` (a
schema/business decision, out of this document's scope) or silently pretending tasks nest under
phases, this document represents that limitation directly: tasks are returned as a separate,
flat, project-level list, not nested under any phase.

## 2. API

**[RESOLVED — conservative]** New endpoint: `GET /api/v1/projects/{projectId}/timeline`.

Response:
```java
record TimelineResponse(List<TimelinePhaseResponse> phases, List<TaskResponse> tasks)
record TimelinePhaseResponse(
        UUID id, String name, LocalDate startDate, LocalDate endDate,
        List<MilestoneResponse> milestones)
```
`tasks` reuses the existing `TaskResponse`/`TaskMapper`; `milestones` reuses the existing
`MilestoneResponse`/`MilestoneMapper`.

## 3. Contents and ordering

**[ESTABLISHED]**
- `phases`: every active phase for the project, ordered by `startDate` ascending (a phase with a
  `null` `startDate` sorts after every phase that has one, keeping the list deterministic without
  discarding unscheduled phases the way Calendar does — Timeline is meant to show the whole
  project, not a windowed subset).
- Each phase's `milestones`: every active milestone with that `phaseId`, ordered by `dueDate`
  ascending (`null` last, same rule).
- `tasks`: every active task for the project (flat, not nested — see §1), ordered by `dueDate`
  ascending (`null` last).

Archived phases/milestones/tasks are excluded — same default as every other list endpoint.

## 4. Implementation shape

**[ESTABLISHED]** Three existing repository calls, unmodified:
`PhaseRepository.findByProjectIdAndArchivedAtIsNullOrderByCreatedAtAsc`,
`MilestoneRepository.findByProjectIdAndArchivedAtIsNullOrderByCreatedAtAsc`,
`TaskRepository.findByProjectIdAndArchivedAtIsNullOrderByCreatedAtAsc` — re-sorted by date in the
application layer (a comparator with nulls-last, since none of these existing queries order by
date). Milestones are grouped into their phase by `phaseId` in the application layer, mirroring
the exact "merge/group in the application layer" style `SearchApplicationService` already
established.

## 5. Authorization

**[ESTABLISHED]** Same `VIEW_PROJECT` check as every other project-scoped read.

## 6. Testing

**[ESTABLISHED]** Application-service tests (phases ordered by startDate with nulls last,
milestones correctly nested under their phase, milestones with no phase or an archived phase
handled — see §7, tasks flat and ordered by dueDate, archived items excluded, authorization),
controller test (response shape), no new repository queries are added so no new persistence test
is required beyond what already exists for the reused queries.

## 7. A note on milestones with no phase, or whose phase is archived

**[RESOLVED — conservative]** A milestone with a `null` `phaseId` is not nested under any phase
and is simply **omitted from `phases[].milestones`** — there is no "unphased milestones" bucket
in this V1 response (unlike Task, which is entirely unphased and gets its own top-level list,
Milestone is only sometimes unphased, and a project's milestone list is already available via the
existing `GET /projects/{id}/milestones` endpoint for that case). A milestone whose `phaseId`
points to an archived phase is also omitted from the timeline (that phase itself is excluded by
§3's "active phases only" rule, so there is no phase entry to nest it under).

## 8. Out of scope

Any change to `Task`/`TaskList`/`Phase` schema (e.g. adding `phaseId` to `Task`), zooming/date
windowing (Calendar already covers that need), drag-to-reschedule (the existing
`PATCH /phases/{id}`, `PATCH /milestones/{id}`, `PATCH /tasks/{id}` already support editing
dates), critical-path or dependency arrows (Gantt's concern — see `19-GANTT-SPEC.md`).
