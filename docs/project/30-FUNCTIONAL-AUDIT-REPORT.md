# 30 — Functional Audit Report (2026-09-20)

Scope: the complete Project Management Module (Spring Boot backend + React frontend) as of
commit `dbb659c` plus the uncommitted Calendar/Dashboard work. Priority: project-status and
task-status correctness, count/filter/report agreement with stored records.

Sources of truth: the local PostgreSQL databases `projectmodule` (dev, populated only with
audit records created through the public API) and `projectmodule_test` (automated tests).
No production or company data was used, and no database was reset, dropped or truncated.

## 1. Method

| Layer | How it was exercised |
|---|---|
| REST API + PostgreSQL | Live requests with `curl` against the running backend (`local` profile), a fresh organisation id, real identity headers |
| Backend units/persistence/controllers | `mvnw verify` against `projectmodule_test` |
| Frontend logic + rendering | `tsc -b`, `vitest run` (React Testing Library, mocked API responses shaped like the real contracts), `oxlint`, `vite build` |
| Browser | **Not performed** in this audit. No claim is made about pixel rendering or manual browser flows. |

## 2. Test matrix (live API)

| # | Area | Case | Expected | Observed | Result |
|---|---|---|---|---|---|
| A1 | Project status | Create → `PLANNING` | `PLANNING` | `PLANNING` | PASS |
| A2 | Project status | PATCH `ACTIVE` / `ON_HOLD` / `COMPLETED` | GET, list and dashboard agree | GET = list = `byStatus` count | PASS |
| A3 | Project status | Completed project on dashboard | `byStatus.COMPLETED = 1`, `ACTIVE = 0` | as expected | PASS |
| A4 | Project status | Unknown status `IN_PROGRESS` in body | 4xx | **500** | **FAIL → fixed (D1)** |
| A5 | Concurrency | Stale `version` | 409 | 409 | PASS |
| A6 | Archive | DELETE project | 204; leaves list; `archivedProjectCount` +1 | as expected | PASS |
| B1 | Task status | Create → `TODO` | `TODO` | `TODO` | PASS |
| B2 | Task status | PATCH `BLOCKED` | GET, `?status=BLOCKED`, board column agree | all `BLOCKED` | PASS |
| B3 | Task status | `BLOCKED` → `TODO`, `BLOCKED` → `BLOCKED` | 422 | 422 / 422 | PASS |
| B4 | Delay detection | Blocked task with past due date in `/delayed` and `overdueItemCount` | listed (spec: due date passed, not completed) | listed, count 1 | PASS (by spec) |
| B5 | Health | `blockedTaskCount` for a status-BLOCKED task with no dependencies | 0 (spec 21 §: "tasks with an unmet active prerequisite") | 0 | PASS by spec, **label misleading → fixed (D4)** |
| B6 | Search | `?q=blocked` finds the task | 1 hit | 1 hit | PASS |
| C1 | AuthZ | Non-member GET project | 403 | 403 | PASS |
| C2 | AuthZ | VIEWER: GET 200; PATCH task, create risk, add member, archive → 403 | as listed | as listed | PASS |
| C3 | Tenancy | Other organisation GET | 404 | 404 | PASS |
| C4 | Identity | No headers | 401 | 401 | PASS |
| D1 | Validation | Blank task name | 400 field error | 400 | PASS |
| D2 | Validation | `dueDate: 2026-13-45` | 4xx | **500** | **FAIL → fixed (D1)** |
| D3 | Not found | Unknown task id | 404 | 404 | PASS |
| D4 | Risks | Resolve, then resolve again | 200 then 422 | 200 / 422 | PASS |

## 3. Defects found and fixed

| ID | Severity | Where | Defect | Fix | Regression test |
|---|---|---|---|---|---|
| D1 | High | Backend `GlobalExceptionHandler` | Unknown enum value or unparseable date in a JSON body raised `HttpMessageNotReadableException`, which had no handler → **HTTP 500** for a caller error | Additive `@ExceptionHandler(HttpMessageNotReadableException)` → 400 `validation-failed` (standard ProblemDetail, Jackson message not echoed) | `GlobalExceptionHandlerTest.reportsUnreadableBodyAsValidationFailure`, `TaskControllerTest.unreadableBodyReturns400` |
| D2 | High | Dashboard "Needs Attention" | A **BLOCKED** task whose due date had passed was relabelled **"Overdue"**, hiding its stored status | Stored status badge is always shown; "past due" is a separate derived marker | `dashboardUtils.test.ts` ("never relabels a BLOCKED task…"), `DashboardPage.test.tsx` |
| D3 | Medium | Dashboard stat cards + donut legend | Card "Active Projects" showed `activeProjectCount` (= all non-archived projects, including Completed); status `ACTIVE` was labelled "In Progress" while badges say "Active" | Cards relabelled "Total Projects" and "Active"; legend uses "Active" | `DashboardPage.test.tsx`, `dashboardUtils.test.ts` |
| D4 | Medium | Health tab, Overview tab | `blockedTaskCount` (dependency-based per spec 21) was labelled "Blocked tasks", easily confused with status BLOCKED | Relabelled "Tasks blocked by an unfinished dependency" / "…by a dependency", linked to the Dependencies tab | covered by existing tab tests |
| D5 | Medium | Severity colours | "Blocked" was red in badges but amber in the dashboard health level and Overview health badge | Blocked counts are now `critical` (red) everywhere | `dashboardUtils.test.ts` (`healthLevel`) |
| D6 | Medium | `utils/format.ts` | `formatDate("2026-09-10")` parsed as UTC midnight → previous day for users west of UTC | Date-only strings are parsed as local calendar dates | `format.test.ts` |

## 4. Known limitations (not defects, documented behaviour)

- A task's `OVERDUE`/`BLOCKED` status is set explicitly by a person (spec: `TaskStatus`); the
  "delayed items" rule (due date passed, not completed) is a separate derived view. The UI now
  shows both without conflating them.
- `PATCH` treats `null` as "unchanged", so a due date cannot be cleared through the API.
- `GET /projects` supports only `page`, `size`, `sort`; the dashboard filters the loaded page
  client-side and says so in the UI.
- No browser-based end-to-end suite exists; rendering was verified through RTL tests only.

## 5. Verification (final run)

- Backend: `target/` removed then `mvnw verify` against `projectmodule_test` —
  844 tests, 0 failures, 0 errors, 0 skipped, BUILD SUCCESS. (`mvnw clean` itself failed twice
  because OneDrive sync held files under `target/`; removing the directory first is equivalent.)
- Live re-check after the fix (rebuilt jar, `local` profile): unknown project status, bad task
  date and truncated JSON all return 400 `validation-failed`; valid reads and the dashboard
  summary are unchanged.
- Frontend: `tsc -b` clean; `vitest run` 104/104; `vite build` OK; `oxlint` only the five
  pre-existing benign warnings (fast-refresh/effect hints).
