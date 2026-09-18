# Project Module — Frontend

A React single-page app for the Project Management Module backend. It is a plain client of the
existing `/api/v1` REST API — it holds no business rules, no database access, and no
authentication of its own. The Spring Boot backend remains the sole authority for validation,
authorization, organization boundaries, and persistence.

## Stack

- React 19 + TypeScript
- Vite (dev server & build)
- React Router (`HashRouter`, so the built app can be served as static files with no server-side
  routing configuration)
- Plain CSS (`src/styles/global.css`) — no UI framework
- Vitest + React Testing Library for tests

No state-management library, UI kit, or CSS framework was added — none was already in use in this
repository and the app's needs (server state + a little local UI state) are met by React's own
hooks.

## Prerequisites

- The backend running (see the repository root `README`/`pom.xml`), reachable over HTTP.
- Node.js 20+.

## Install & run

```bash
cd frontend
npm install
cp .env.example .env      # then edit VITE_API_BASE_URL if the backend isn't on localhost:8080
npm run dev                # http://localhost:5173
```

Other scripts:

```bash
npm run build      # type-check (tsc -b) + production build to dist/
npm run preview    # serve the production build locally
npm test           # run the Vitest suite once
npm run lint        # oxlint
```

## Environment variables

| Variable | Purpose | Default |
|---|---|---|
| `VITE_API_BASE_URL` | Origin the backend is served from (the app appends `/api/v1` itself) | `http://localhost:8080` |

See `.env.example`.

## Backend CORS

The backend only accepts cross-origin browser requests from origins listed in
`projectmodule.cors.allowed-origins` (see `ApiConfiguration` in the backend). It defaults to this
app's Vite dev (`5173`) and preview (`4173`) ports. If you serve the built frontend from another
origin, set the backend's `PROJECTMODULE_CORS_ALLOWED_ORIGINS` environment variable to match.

## Identity — there is no login screen

This backend has no authentication of its own: every request must carry `X-User-Id` and
`X-Org-Id` headers, which a real deployment's upstream gateway would inject. Since this frontend
talks to the backend directly, it asks you for those two values once (top-right of the header,
"Set identity") and resends them on every request. They are stored only in `localStorage` in your
browser — this is a developer/operator convenience, not an authentication system, and it grants
nothing by itself: the backend still authorizes (or rejects) every request based on the project
membership rows for the user id you provide.

To use the app you need at least one organization id and one user id that the backend already
knows about (e.g. one you created a project with, or one added as a project member) — the
frontend cannot create organizations or users; those concepts are entirely external to this
module (`ExternalUserId` / `OrganizationId` are opaque identifiers here, per the backend's own
architecture).

## Architecture

```
React UI  →  src/api/*  →  fetch(`${VITE_API_BASE_URL}/api/v1/...`)  →  Spring Boot REST API
```

- `src/api/client.ts` — the single fetch wrapper. Attaches identity headers, throws `ApiError`
  (carrying the backend's RFC 9457 `ProblemDetail`) on any non-2xx response, and `NetworkError`
  when the request never reaches the server.
- `src/api/*.ts` — one thin module per backend controller area (projects, tasks, risks,
  automations, ai, …), each just a typed wrapper around `api.get/post/patch/del`. No endpoint is
  called that isn't in the backend's actual controllers.
- `src/types/*.ts` — TypeScript types mirroring the backend's request/response DTOs and enums
  exactly (field names, nullability, and enum values were read from the Java source, not guessed).
- `src/context/IdentityContext.tsx` — the dev identity switcher described above.
- `src/context/ProjectWorkspaceContext.tsx` — shares the loaded `Project`, the caller's derived
  role, and a `can(permission)` check across every tab of a project workspace. This client-side
  permission check is UX only (hides/disables actions); the backend re-checks authorization on
  every request regardless.
- `src/features/registry.ts` — **the single source of truth for what the product can do.** Every
  screen is a `FeatureDefinition` (name, plain-language description, category, route, icon,
  `tier: CORE | ADVANCED`, `releaseState`, `requiredPermission`). The sidebar, project tabs,
  mobile tab bar, header breadcrumb and the "More" hub are all derived from it.
- `src/features/featureState.ts` — resolves a feature's access state for the current user
  (AVAILABLE / BETA / REQUIRES_PERMISSION / COMING_SOON) from the registry + project role.
- `src/pages/features/AdvancedFeaturesPage.tsx` — the "More" hub (`/more` and
  `/projects/:id/more`): advanced features grouped by category with honest access states.
- `src/pages/projects/tabs/*` — one file per project screen.
- `src/components/common/*` — shared building blocks: `DataTable` (loading/empty/error states),
  `Modal`, `ConfirmDialog`, `Pagination`, status `Badge`s, `IdentityWidget`.

## Routes

```
/dashboard                                     → Home
/projects
/templates
/projects/:projectId                          → redirects to overview
/projects/:projectId/overview
/projects/:projectId/tasks
/projects/:projectId/tasks/:taskId             → task detail (subtasks, checklist, dependencies)
/projects/:projectId/kanban
/projects/:projectId/calendar
/projects/:projectId/timeline
/projects/:projectId/gantt
/projects/:projectId/phases
/projects/:projectId/milestones
/projects/:projectId/task-lists
/projects/:projectId/risks
/projects/:projectId/issues
/projects/:projectId/decisions
/projects/:projectId/dependencies              → dependency analysis (cycles/blocked/broken)
/projects/:projectId/members
/projects/:projectId/comments
/projects/:projectId/activity                  → shows "not available" — see below
/projects/:projectId/custom-fields
/projects/:projectId/search
/projects/:projectId/health
/projects/:projectId/delayed                   → delay detection
/projects/:projectId/reports
/projects/:projectId/automations
/projects/:projectId/automations/:id/runs
/projects/:projectId/ai
/projects/:projectId/more                      → project advanced features hub
```

## Information architecture: simple by default, powerful when needed

Only **CORE** features appear in navigation (Home, Projects, Templates; inside a project: Overview,
Tasks, Board, Calendar, Team). Everything else is **ADVANCED** and lives behind **More**, grouped into
Advanced planning / Project control / Productivity / Automation & intelligence, each card showing
whether it is Available, Beta, Requires permission (with an explanation of who can grant it —
there is no access-request API on this backend, so nothing is faked) or Coming soon.

### Adding a feature without touching navigation

1. Add the route to `App.tsx` (as today).
2. Add one `FeatureDefinition` to `src/features/registry.ts` with the same `route`.
3. Pick `tier: 'CORE'` (always visible — keep ≤5 per scope; a test enforces this) or
   `'ADVANCED'` (appears in More under its `category`).
4. Set `releaseState` honestly: `AVAILABLE`, `BETA`, or `COMING_SOON` (no backend yet).
5. Add an icon name to `Icon.tsx` if none fits.

That is the whole change — sidebar, tabs, mobile tab bar, breadcrumb and the hub update
themselves. `registry.test.ts` fails if a definition points at a route that doesn't exist.
Future modules (time tracking, budgets, files, a client portal…) slot in the same way.

## Modules that are intentionally not implemented (or partially so)

- **Activity / Audit feed** — the backend records `ProjectActivity` internally but exposes no
  REST endpoint for it. The Activity tab says so explicitly; nothing is simulated.
- **AI Actions / Approvals, AI-driven project creation, and P6 Agents** — not implemented, because
  the backend itself has not implemented them (see `docs/project/27-AI-ACTIONS-SPEC.md` and
  `docs/project/29-AGENTS-SPEC.md` — both are open specifications with no corresponding API). The
  AI tab only exposes the recommendation types the backend actually serves (task breakdown/
  improvement, project summary, risk analysis, what-if) and clearly reports "AI service is
  currently unavailable" when the backend's AI provider isn't configured, rather than fabricating
  a response.
- **Report export** — the backend's `/reports/summary` endpoint returns JSON only; no export
  format exists on the backend, so none is offered in the UI.

## Testing

`npm test` runs the Vitest suite: API client behavior (headers, error mapping, conflict/
unavailable detection), permission derivation, the identity-setup form, table loading/empty/error
states, project creation validation, automation creation (including that the trigger-event
dropdown is exactly the backend's catalogue, and the NOTIFY/CHAT_MESSAGE field switch), and the
AI "provider unavailable" path.
