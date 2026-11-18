# 31 — WorkEasy360 UI: reference mapping (2026-09-20)

The frontend was rebuilt to follow a supplied reference design ("WorkEasy360": sign-in page,
dashboard, calendar, consistent design system). This document records how each part of the
reference maps onto the real backend, and every place the UI deliberately differs because the
backend has no such data or capability. Nothing in the UI is simulated.

## Design system

- `frontend/src/styles/theme.css` — tokens (indigo→violet brand gradient, lavender canvas,
  16 px panels, layered shadows), dark theme under `:root[data-theme='dark']`.
- Theme preference: `context/themeStore.ts` (`localStorage: projectmodule.theme`, values
  `system | light | dark`), header toggle, Settings → Appearance.
- Per-page styles: `dashboard.css`, `calendar.css`, `workspace-calendar.css`, `mytasks.css`,
  `workspace.css`, `project.css`, `signin.css`. Colours only through tokens.

## Sign-in (reference: "Login page")

| Reference | Implementation | Why |
|---|---|---|
| Email + password login | **Kept** (email sign-in, register, "Sign in with Google", invitations, password reset, the hand-over of an earlier sign-in: `33-AUTHENTICATION.md`), plus a **Continue with Workeasy360** link when Workeasy360 sign-in is configured (`pages/auth/SignInPage.tsx`) | The product is `sso_optional` by default: the page offers both, and never starts Workeasy360 sign-in by itself (integration guide 6b point 6). The link starts `GET /api/auth/workeasy360/start?next=<route>`. Only with `WORKEASY360_SIGN_IN_MODE=sso_only` is the page Workeasy360-only and does opening any other route with no session start it automatically (`SignedOutGate`), except after a sign-out here, within 60 s of a previous automatic start, or when the session check failed: see `docs/workeasy360-sso.md`. |
| "Success animation on valid login" | Email sign-in: the app opens; Workeasy360: sign-in happens at Workeasy360 and the browser returns to the app signed in | The Workeasy360 session is created by the backend (`docs/workeasy360-sso.md`); failures come back as `/#/sign-in?sso_error=<code>` and are explained on the page. |
| "Remember me" | Email sign-in only | An email session lasts 30 days with it, 12 hours without. A Workeasy360 session has an idle timeout (default 8 hours); nothing identity-related is stored in the browser for it. |
| Continue with Google / Microsoft | Google, when `PROJECTMODULE_GOOGLE_CLIENT_ID` is set; Workeasy360 offers whatever sign-in methods the organization allows | |
| Forgot password / Create account | Email sign-in only | A Workeasy360 account's password and profile are managed in Workeasy360. |

The app shell (`components/layout/AppLayout.tsx`) opens straight away for an email session; otherwise it asks
`GET /api/auth/session` on load and redirects to `/sign-in` while there is no session (or, in `sso_only` mode,
starts Workeasy360 sign-in); for a Workeasy360 session the header account menu and Settings offer "Sign out of
Easy Project" and, when configured, "Sign out of Easy Project and Workeasy360". When Workeasy360 says the organization's
access has ended the whole app is replaced by one "access has ended" screen (Try again / Sign out); a
read-only subscription shows a notice and refuses writes.

## Dashboard (reference: "Home")

| Reference element | Implementation | Deviation |
|---|---|---|
| Greeting hero + quote | `HeroSection` | — |
| 4 stat cards with trend % | `Total Projects` (all non-archived), `Active`, `Planning`, `Completed` from `GET /dashboard` | **No trend arrows/percentages** — the backend has no historical data; card labels use real status names (there is no "In Progress" status) |
| Needs Attention table (status, date, assignee, priority) | `AttentionPanel`: stored `TaskStatusBadge` + separate "past due" marker, due date, assignee short id | **No priority column** (tasks have no priority); no checkboxes (no bulk action) |
| Mini calendar with dots, "Add Event" | `MiniCalendarCard`: per-project `GET /projects/{id}/calendar` for the month; "+ Add task" → real create-task dialog with the day preselected | "Events" are tasks and milestones (the only dated entities); no free-form events exist |
| Upcoming list with times | `UpcomingCard`: next dated open tasks + pending/at-risk milestones | **No clock times** — due dates are date-only |
| Your Projects cards with description / end date | Name, status, progress (from tasks), task & milestone counts, latest due date | `ProjectSummary` has no description or end date |
| Project Status donut | `donutSegments` from `byStatus` | — |
| Recent Activity | Honest placeholder | **No activity endpoint** in the backend (activity is recorded internally only) |
| AI Assistant chat | `AIRecommendationsCard`: real pending recommendations per project; "AI provider not configured" on 503 | **No chat endpoint**; recommendations are the only AI capability |

## Sidebar (reference: Home, Projects, Tasks, Calendar, My Team, Reports, Templates, Automations, Integrations, Settings)

All items come from the feature registry (`features/registry.ts`). Workspace pages aggregate
per-project APIs over the first page of up to 50 most-recently-updated projects
(`hooks/useWorkspaceData.ts`) and say so when an organisation has more.

| Item | Route | Data |
|---|---|---|
| Home | `/dashboard` | `GET /dashboard` + per-project health/tasks/milestones/calendar/AI |
| Projects | `/projects` | `GET /projects` (page/size/sort); header search filters the loaded page by name, client-side, and says so |
| My Tasks | `/tasks` | per-project `GET /projects/{id}/tasks`; inline status changes via the real PATCH |
| Calendar | `/calendar` | per-project `GET /projects/{id}/calendar` |
| My Team | `/team` | per-project `GET /projects/{id}/members`; people are user ids only (no directory service) |
| Reports | `/reports` | per-project `GET /projects/{id}/reports/summary` |
| Templates | `/templates` | `GET/POST /templates` |
| Automations | `/automations` | per-project `GET /projects/{id}/automations`; runs loaded on demand |
| **Integrations** | **omitted** | No integration API exists |
| Settings | `/settings` | signed-in account, theme, sidebar, feature registry, API base URL — no fake preferences |

## Project calendar (reference: "add, view, edit events")

- Add: day "+ Add" menu → Task (existing dialog) or Milestone (`CreateMilestoneDialog`, real
  `POST /projects/{id}/milestones`).
- View: event detail dialog with links to the entity.
- Edit: due date via `PATCH /tasks/{id}` / `PATCH /milestones/{id}` with `version`
  (409 → "changed elsewhere — reload"); task status via the shared status control.
- Only members with `EDIT_PROJECT` see the add/edit controls; the backend enforces it anyway.

## Status semantics (unchanged, verified in 30-FUNCTIONAL-AUDIT-REPORT.md)

Project: PLANNING / ACTIVE / ON_HOLD / COMPLETED / CANCELLED. Task: TODO / BLOCKED / OVERDUE /
COMPLETED. A Blocked task is always shown as Blocked; "past due" is a separate derived marker.
