# Project Module — Frontend

A React single-page app for the Project Management Module backend. It is a plain client of the
existing `/api/v1` REST API — it holds no business rules, no database access, and no
authentication of its own: people sign in with Workeasy360 and the browser holds a session cookie. The Spring Boot backend remains the sole authority for validation,
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

- The backend running (see the repository root `README`/`pom.xml`) on `http://localhost:8080`.
- Node.js 20+.

## Install & run

```bash
cd frontend
npm install
npm run dev                # http://localhost:5173 (forwards /api to http://localhost:8080)
```

Other scripts:

```bash
npm run build      # type-check (tsc -b) + production build to dist/
npm run preview    # serve the production build locally (http://localhost:4173, /api proxied too)
npm test           # run the Vitest suite once
npm run lint        # oxlint
```

## Environment variables

| Variable | Purpose | Default |
|---|---|---|
| `VITE_API_BASE_URL` | Optional prefix for API calls. Leave it unset. | empty (same origin) |

See `.env.example`. If an old `.env` still sets `VITE_API_BASE_URL=http://localhost:8080`, delete that
line: a cross-origin base cannot carry the sign-in cookie, so sign-in would not work.

## Same origin only: cookies, CSRF and the dev proxy

The UI and the API must be served from **one origin**. The sign-in session is an HttpOnly cookie that
the browser sends with `credentials: 'same-origin'`, and writes carry a CSRF token. There is no CORS
setup, and a cross-origin deployment is not supported.

- **Production:** the Spring Boot jar serves the built UI and the API together.
- **Development:** `npm run dev` (and `npm run preview`) forward `/api` - and only `/api` - to
  `http://localhost:8080` through a Vite proxy (`vite.config.ts`). `changeOrigin` is deliberately off,
  so the `Host` header, the cookies and the sign-in redirects stay on `localhost:5173` (or `:4173`).
  Open the app on that address, not on `:8080`.
- **CSRF:** the backend sets a script-readable `XSRF-TOKEN` cookie. `src/api/client.ts` copies it
  (URL-decoded, read at send time) into an `X-XSRF-TOKEN` header on every POST/PATCH/DELETE, and never
  on reads. If the cookie is not there yet, the client first calls `GET /api/auth/session` to get it. A
  403 with `code: csrf_invalid` is shown as "Reload the page and try again".

## Signing in

There are two ways to sign in, and the sign-in page offers each one this deployment has
(`GET /api/v1/auth/config`, read once per page by `src/api/signInOptions.ts`):

- **An email account** (email and password, "Sign in with Google", an invitation link, the hand-over of
  an earlier sign-in). The backend returns a session token, which the API client sends as
  `Authorization: Bearer <token>`; a request with a token carries no CSRF header. "Remember me" keeps
  it in `localStorage`; otherwise it lasts for this tab only, in `sessionStorage`. When the backend
  stops accepting a token, the client clears it and the app returns to the sign-in page. See
  `docs/project/33-AUTHENTICATION.md`.
- **Workeasy360**, when the server has it configured (`workeasy360.enabled`): **Continue with
  Workeasy360**, a plain link to `/api/auth/workeasy360/start?next=<route>` (a top-level navigation,
  never `fetch`, and never followed automatically). The session is an HttpOnly cookie. See
  `docs/workeasy360-sso.md`.

`useIdentity()` (`src/context/IdentityContext.tsx`) is "who is signed in" for every page, whichever
way: the email session when there is one, otherwise the Workeasy360 session (`identity.provider`
says which). An email session in the same browser takes precedence.

### Workeasy360 sessions

1. `SessionProvider` asks `GET /api/auth/session` on load. While that is pending, and there is no email
   session, `AppLayout` shows a neutral loading state. A `401` means no Workeasy360 session. Then
   `SignedOutGate` (`src/components/layout`) sends the person to the sign-in page, which offers every
   way in. **Only on a deployment where Workeasy360 is the only way in** (`signInMode: 'sso_only'`) does
   it **start sign-in by itself**: it replaces the current page with
   `/api/auth/workeasy360/start?next=<the route that was asked for>` (`location.replace`, so Back does not
   bounce forward) and shows the neutral "Signing you in with Workeasy360..." screen (with a way out after
   about 5 seconds), so a person already signed in to Workeasy360 who types the address arrives signed in.
   Even there it does **not** start (and sends the person to `#/sign-in` with the route they wanted) when
   the signed-out marker cookie is present (the person signed out of Easy Project in this browser), when an
   automatic start happened less than 60 seconds ago (the loop guard), or when the session check failed.
   Rules and storage names: `src/api/autoSignIn.ts` and `docs/workeasy360-sso.md`. The sign-in route
   itself never starts anything, whatever its query. (With an email sign-in on the same page, the app
   cannot know which one the person wants: Workeasy360 integration guide 6b, point 6.)
2. The backend redirects to Workeasy360 and, after sign-in, lands the browser on `/#/<route>`. `next` is
   validated by `safeNextRoute` (the server's rules: a hostile value becomes `/`).
3. If sign-in fails the backend returns to `#/sign-in?sso_error=<code>`. Each code has a plain-language
   message in `src/api/sessionMessages.ts`; an unknown code is shown as `refused`.
4. **Sign out of Easy Project** calls `POST /api/auth/logout` (the server leaves the `ep_signed_out` marker
   cookie, so nothing signs the person straight back in) and returns to the sign-in page. When the
   session says `workeasy360SignOut: true`, **Sign out of Easy Project and Workeasy360** does the same and
   then navigates to `/api/auth/workeasy360/logout`. Both are in the header account dialog and in
   Settings (which also shows the signed-in account and organization id, read-only; the name, email and
   password are managed in Workeasy360, so the email-account dialogs are not offered).

API errors feed the session they came from:

- `401` on a call that carried an email session's token ends that session (only if it is still the
  current one); `401` on a call without one ends the Workeasy360 session in the UI. The API client
  itself never navigates.
- `403` with `workeasy360_access_ended` or `workeasy360_access_unverified` (only a Workeasy360 session
  gets one) replaces the app with an "Easy Project access has ended" / "Workeasy360 can't confirm access
  right now" screen, with the reason in plain words and *Try again* / *Sign out* buttons.
- `403` with `workeasy360_read_only` keeps the app and shows a dismissible notice; the failed write
  reports its own error as usual.
- Other `403`s (project permissions, `managed_in_workeasy360` for an email-account page asked for with a
  Workeasy360 sign-in) are ordinary errors.

The signed-in identity's `userId` is "me" for My Tasks, comment authorship and the project permission
check. The `X-User-Id` / `X-Org-Id` headers are not sent; the server ignores them.

## Architecture

```
React UI  →  src/api/*  →  fetch(`${VITE_API_BASE_URL}/api/v1/...`)  →  Spring Boot REST API
```

- `src/api/client.ts` — the single fetch wrapper. Sends the email session's token, or the session cookie and CSRF token; reports 401 to the session it came from and `workeasy360_*` 403 responses to the Workeasy360 session store; throws `ApiError`
  (carrying the backend's RFC 9457 `ProblemDetail`) on any non-2xx response, and `NetworkError`
  when the request never reaches the server.
- `src/api/*.ts` — one thin module per backend controller area (projects, tasks, risks,
  automations, ai, …), each just a typed wrapper around `api.get/post/patch/del`. No endpoint is
  called that isn't in the backend's actual controllers.
- `src/types/*.ts` — TypeScript types mirroring the backend's request/response DTOs and enums
  exactly (field names, nullability, and enum values were read from the Java source, not guessed).
- `src/context/IdentityContext.tsx` — who is signed in, whichever way (email session or Workeasy360), and signing out.
- `src/context/identityStore.ts` — the email session (token) kept in this browser.
- `src/context/sessionStore.ts` / `SessionContext.tsx` — the Workeasy360 session (status, user, organization, access problem) as a tiny external store, so the API client can report into it without importing React.
- `src/api/session.ts` — session check, logout and the start/logout URLs; `src/api/sessionMessages.ts` — sign-in error and access-ended texts.
- `src/api/signInOptions.ts` — how this deployment signs people in (`GET /api/v1/auth/config`).
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
  `Modal`, `ConfirmDialog`, `Pagination`, status `Badge`s, `AccountMenu`.

## Routes

```
/sign-in, /join/:token, /reset/:token           → signing in, joining by invitation, resetting a password
/dashboard                                     → Home
/tasks, /calendar, /team, /timesheet, /reports, /automations, /settings, /search
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
/projects/:projectId/activity                  → who did what on the project
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

`npm test` runs the Vitest suite: API client behavior (same-origin credentials, CSRF header on writes only, 401/403 handling, error mapping, conflict/
unavailable detection), permission derivation, the sign-in page and its error messages, the session guard, the access-ended screen and both sign-out flows, table loading/empty/error
states, project creation validation, automation creation (including that the trigger-event
dropdown is exactly the backend's catalogue, and the NOTIFY/CHAT_MESSAGE field switch), and the
AI "provider unavailable" path.
