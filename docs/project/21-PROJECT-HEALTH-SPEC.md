# Project Health (P3) — Specification

## Status of this document

**Resolved and implementation-ready.** The prior version of this document (§ "Prior open
decisions" below) left §2–§5 open because a *weighted numeric score* is a genuine value
judgment this document had no authority to invent. The product owner has since approved a
different, narrower shape that sidesteps the formula question entirely: **individual
deterministic indicators, not a composite score.** This section records that approval; §1–§6
below are the resulting implementation-ready specification.

**[APPROVED]** Decisions, verbatim from the approval:
1. Indicators: overdue items, unresolved risks, unresolved issues, blocked tasks, broken
   dependencies.
2. Do **not** invent a weighted 0–100 health score or any other composite formula.
3. Return the individual counts/indicators clearly — a caller (or a later UI) can weight them
   itself if it chooses to; this module does not decide that.
4. No persistence, no migration, no new Maven dependency — computed on every read from existing
   tables, the same pattern Delay Detection/Dependency Analysis/Reports already established.
5. Reuse `DelayDetectionApplicationService`/`DependencyAnalysisApplicationService` directly
   rather than re-deriving "overdue"/"blocked"/"broken" a second time.
6. Same `VIEW_PROJECT` authorization and organization boundary as every other project-scoped
   read. No mutation. No AI.

## 1. Prior open decisions (superseded by the approval above, kept for context)

The original proposal could not resolve a numeric score's output shape, formula/weights, or
persistence strategy without inventing business judgment `01-SPEC.md`/`03-DATABASE.md` never
defined. The approval above removes the need for a formula altogether by returning indicators
directly instead of a derived score.

## 2. Indicators (deterministic, from existing data only)

**[APPROVED — conservative]**

| Indicator | Definition | Source |
|---|---|---|
| `overdueItemCount` | Count of delayed Task/Milestone/Phase items | `DelayDetectionApplicationService.getDelayedItems(...).size()` — the exact definition `22-DELAY-DETECTION-SPEC.md` §2 already approved, not re-derived |
| `unresolvedRiskCount` | Active risks with `status = OPEN` | `RiskRepository.countActiveByStatus(projectId)`, already exists (added for `24-REPORTS-SPEC.md`) — the `OPEN` row's count, `0` if absent |
| `unresolvedIssueCount` | Active (non-archived) issues | `Issue` has no status field at all (confirmed by direct inspection — only `priority`), so there is no "resolved" state to filter on; "unresolved" is read as "still active," the only open/closed-like signal Issue's data model actually has. New derived-name repository method: `IssueRepository.countByProjectIdAndArchivedAtIsNull(UUID)` — the identical, already-precedented pattern `DecisionRepository.countByProjectIdAndArchivedAtIsNull` already uses (`24-REPORTS-SPEC.md` §4) |
| `blockedTaskCount` | Tasks with an unmet active prerequisite | `DependencyAnalysisApplicationService.getAnalysis(...).blockedTaskIds().size()` — the exact definition `23-DEPENDENCY-ANALYSIS-SPEC.md` §2 already approved |
| `brokenDependencyCount` | Active dependencies marked `broken` | `DependencyAnalysisApplicationService.getAnalysis(...).brokenDependencies().size()` — same call as above, both fields read from one result, not two separate calls |

## 3. Response shape

**[APPROVED — conservative]**
```java
record ProjectHealthResponse(
        long overdueItemCount,
        long unresolvedRiskCount,
        long unresolvedIssueCount,
        long blockedTaskCount,
        long brokenDependencyCount)
```
No score, no label, no thresholds — exactly the approved indicators, nothing derived from them.

## 4. API

**[APPROVED — conservative]** New endpoint: `GET /api/v1/projects/{projectId}/health`.

## 5. Authorization

**[ESTABLISHED]** Same `VIEW_PROJECT` check as every other project-scoped read.

## 6. Testing

**[ESTABLISHED]** Application-service tests (each indicator populated correctly, zero when
there is nothing to count, `DependencyAnalysisApplicationService.getAnalysis` called exactly
once and both fields read from that one result, authorization enforced), controller test
(response shape), persistence test for the one new `IssueRepository` derived-count method
against real PostgreSQL.

## 7. Out of scope

Any weighted/composite score, thresholds or ON_TRACK/AT_RISK/CRITICAL-style labels, persistence
of a `ProjectHealth` row, scheduled recalculation, historical health trend, AI-assisted
interpretation.

## 8. Classification

**IMPLEMENTED**, per the approval in this document's Status section.
