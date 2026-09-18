# Reports (P3) — Specification

## Status of this document

**Resolved and implementation-ready**, scoped to the one reading of "Reports" directly grounded
in an existing precedent (§1) rather than guessing among open-ended reporting concepts (time
tracking, burndown, velocity — none of which this module has any underlying data for).
`08-ROADMAP.md:40` lists `Reports` under P3 — Intelligence as a single bullet.

Tags: **[ESTABLISHED]** = direct application of an existing convention.
**[RESOLVED — conservative]** = genuinely undefined; resolved conservatively.

## 1. Scope, chosen from an existing precedent

**[RESOLVED — conservative]** `DashboardApplicationService`/`DashboardResponse` already
establish exactly this shape of feature — enum-distribution counts, every key always present,
zero-filled — but scoped to *every project in the organization*. This document reuses the
identical pattern, scoped instead to **one project**: a "Project Summary Report." This is a
mechanical projection of already-existing enum distributions, not an invented business concept,
which is why it can be resolved conservatively rather than left as an open proposal the way
`21-PROJECT-HEALTH-SPEC.md` (a genuine weighted formula) had to be.

No time-tracking, velocity, burndown, or estimation data exists anywhere in this module's domain
model (confirmed: no `estimatedHours`/`loggedHours`/`storyPoints`-style field on any entity) —
so no such report is included; inventing one would fabricate data this module doesn't have.

## 2. Contents

**[RESOLVED — conservative]**

```java
record ProjectSummaryReportResponse(
        Map<TaskStatus, Long> taskCountByStatus,
        Map<RiskStatus, Long> riskCountByStatus,
        Map<ProjectPriority, Long> riskCountByPriority,
        Map<ProjectPriority, Long> issueCountByPriority,
        long decisionCount,
        long delayedItemCount,
        long brokenDependencyCount)
```

Every map carries every enum key, including zero counts — the same convention
`DashboardResponse.byStatus`/`byPriority` already establish. All counts are over **active
(non-archived)** rows only, the same default every other read in this module uses.
`delayedItemCount` and `brokenDependencyCount` are **not** duplicated business logic — they
reuse `DelayDetectionApplicationService`/`DependencyAnalysisApplicationService` (this batch's
own `22-DELAY-DETECTION-SPEC.md`/`23-DEPENDENCY-ANALYSIS-SPEC.md`) directly and take the size of
their result lists, rather than re-deriving the same "what counts as delayed/broken" rules a
second time in a different place.

## 3. API

**[RESOLVED — conservative]** New endpoint:
`GET /api/v1/projects/{projectId}/reports/summary`.

## 4. Implementation shape

**[ESTABLISHED]** New project-scoped `GROUP BY` count queries, mirroring
`ProjectRepository.countActiveByStatus`/`countActiveByPriority` exactly (the one documented
precedent in this module for "a derived method name can't express `GROUP BY`, so this is the one
`@Query` exception"): `TaskRepository.countActiveByStatus(projectId)`,
`RiskRepository.countActiveByStatus(projectId)`, `RiskRepository.countActiveByPriority(projectId)`,
`IssueRepository.countActiveByPriority(projectId)`. `decisionCount` is a simple derived-name
count (`DecisionRepository.countByProjectIdAndArchivedAtIsNull`) — no `GROUP BY` needed since
Decision has no status/priority-style field to group by.

## 5. Authorization

**[ESTABLISHED]** Same `VIEW_PROJECT` check as every other project-scoped read — unlike
`DashboardApplicationService` (explicitly organization-wide and *not* gated by
`ProjectAuthorizationService`, per its own javadoc), this report is scoped to one project and
therefore does use the per-project check, consistent with every other project-scoped read in
this module.

## 6. Testing

**[ESTABLISHED]** Application-service tests (each count correct including zero-filled enum
keys, archived rows excluded, delayed/broken counts correctly delegate rather than re-derive,
authorization), controller test (response shape), persistence tests for the new `GROUP BY`/count
query methods against real PostgreSQL.

## 7. Out of scope

Any report requiring data this module doesn't have (time tracking, velocity, burndown,
estimation), multi-project/portfolio-level reports (Dashboard already covers the org-wide
aggregate case), exportable formats (PDF/CSV), scheduled/emailed reports.
