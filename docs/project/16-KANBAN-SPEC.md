# Kanban (P2) — Specification

## Status of this document

**Resolved and implementation-ready.** `08-ROADMAP.md:25` lists `Kanban` under P2 — Views as a
single bullet with no elaboration anywhere else in `docs/project/`.

Tags: **[ESTABLISHED]** = direct application of an existing convention.
**[RESOLVED — conservative]** = genuinely undefined; resolved with the smallest, most
conservative option, using only fields that already exist.

## Purpose

**[RESOLVED — conservative]** A Kanban board groups a project's active tasks into columns by
`TaskStatus`. It is a **read-only reshaping of existing Task data** — nothing about a task's
data model changes. "Moving a card" between columns is already fully supported by the existing
`PATCH /api/v1/tasks/{id}` endpoint (`status` field of `UpdateTaskRequest`), including that
endpoint's existing transition rules (e.g. no reverse transition out of `BLOCKED`/`OVERDUE` —
`TaskStatus.java` javadoc). This document does not add, relax, or otherwise touch those rules;
it only adds a new way to *read* tasks grouped by their current status.

## 1. Scope

**[RESOLVED — conservative]** Task only. No other entity in this codebase has a status field
suited to a small, fixed set of workflow columns the way `TaskStatus` does (Risk/Milestone have
statuses too, but Kanban as a roadmap bullet is conventionally a task board; widening scope
without a documented need would be invention).

## 2. Columns

**[ESTABLISHED]** Exactly the four `TaskStatus` values, in their declared enum order:
`TODO`, `BLOCKED`, `OVERDUE`, `COMPLETED`. Every column is always present in the response, even
when empty — the same "every enum key present, zero-filled" convention
`DashboardResponse.byStatus`/`byPriority` already establish.

## 3. Column contents

**[ESTABLISHED]** Active (non-archived) tasks for the given project, grouped by `status`,
each column ordered by `createdAt` ascending — identical ordering to every other existing Task
list endpoint (`findByProjectIdAndArchivedAtIsNullOrderByCreatedAtAsc`). Archived tasks are
excluded entirely (no "show archived" toggle for the board — Filters' `archived` parameter
already exists on the plain list endpoint for that need; duplicating it here is unnecessary).

## 4. API

**[RESOLVED — conservative]** New endpoint: `GET /api/v1/projects/{projectId}/tasks/board`.

Response:
```java
record KanbanBoardResponse(List<KanbanColumnResponse> columns)
record KanbanColumnResponse(TaskStatus status, List<TaskResponse> tasks)
```
Reuses the existing `TaskResponse`/`TaskMapper` — no new per-task DTO.

## 5. Implementation shape

**[ESTABLISHED]** One repository call
(`findByProjectIdAndArchivedAtIsNullOrderByCreatedAtAsc`, already exists, unmodified) fetches
every active task for the project once; the application service groups the result in memory
into the four fixed columns (avoids four separate queries, and keeps ordering trivially
consistent with the existing single-query list endpoints).

## 6. Authorization

**[ESTABLISHED]** Same `VIEW_PROJECT` check as every other Task read method.

## 7. Testing

**[ESTABLISHED]** Application-service test (tasks grouped into correct columns, empty columns
present, archived tasks excluded, authorization enforced), controller test (response shape,
column order), persistence-level coverage is unnecessary beyond what
`findByProjectIdAndArchivedAtIsNullOrderByCreatedAtAsc` already has (no new query is added).

## 8. Out of scope

Any new mutation endpoint for "moving" a card (the existing `PATCH /tasks/{id}` already covers
it), manual column-internal ordering/position (no `position`/`order` field exists on `Task` or
any other entity in this codebase — introducing one would be a schema/business decision outside
this document's authority), WIP limits, swimlanes, board configuration/persistence.
