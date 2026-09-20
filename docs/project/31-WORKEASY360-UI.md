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
| Email + password login | User ID + Organization ID (`pages/auth/SignInPage.tsx`) | The backend has no accounts or passwords; every request is authorised by `X-User-Id` / `X-Org-Id` headers |
| "Success animation on valid login" | Identity is **verified against the backend** (`GET /dashboard` with those headers must succeed) → checkmark animation → workspace | Nothing is stored unless the backend accepts it; 401/403 clears it and shakes the form |
| "Remember me" | localStorage (device) vs sessionStorage (tab) | `context/identityStore.ts` |
| Continue with Google / Microsoft | **Omitted** | No SSO integration exists in the backend |
| Forgot password / Create account | **Omitted** | No such flows exist |

The app shell (`components/layout/AppLayout.tsx`) redirects to `/sign-in` while no identity is
configured; Settings and the header widget offer "Sign out" (clears the identity).

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
| Settings | `/settings` | identity, theme, sidebar, feature registry, API base URL — no fake preferences |

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
