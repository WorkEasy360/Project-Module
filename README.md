# Project Management Module

A project-management module made of a Spring Boot REST API and a React single-page app.

| Part | Location | Stack |
|---|---|---|
| Backend API | repository root (`src/`, `pom.xml`) | Java 21 · Spring Boot 3.5 · Spring Data JPA · Flyway · PostgreSQL 17 |
| Frontend | [`frontend/`](frontend/) | React 19 · TypeScript · Vite · React Router · Vitest |
| Specifications | [`docs/project/`](docs/project/) | Numbered design/spec documents (`01-SPEC.md` … `29-AGENTS-SPEC.md`) |

The backend is the single source of truth for validation, authorization, business rules and
persistence. The frontend is a plain client of `/api/v1` and holds no business logic.

The Maven build compiles the frontend and packages it inside the jar under `/static`, so one
deployed application serves both the UI and the API from a single URL. The backend itself stays
API-only: it gains no view layer, no session and no server-rendered pages — it simply serves the
built files as static resources.

## Prerequisites

- Java 21 (JDK) and the bundled Maven wrapper (`./mvnw` / `mvnw.cmd`)
- PostgreSQL 17 running locally
- Node.js 20+ (frontend)

## Backend — run locally

The backend never reads credentials from files; they come from environment variables.

```powershell
# PowerShell (Windows)
$env:SPRING_PROFILES_ACTIVE = "local"          # uses src/main/resources/application-local.yml
$env:PROJECTMODULE_DB_PASSWORD = "<your local postgres password>"
.\mvnw.cmd spring-boot:run                       # http://localhost:8080
```

```bash
# bash / macOS / Linux
SPRING_PROFILES_ACTIVE=local PROJECTMODULE_DB_PASSWORD='<password>' ./mvnw spring-boot:run
```

The `local` profile defaults to `jdbc:postgresql://localhost:5432/projectmodule` with user
`postgres`; create that database once (`CREATE DATABASE projectmodule;`). Flyway applies all
migrations in `src/main/resources/db/migration` on startup.

Health check: `GET http://localhost:8080/actuator/health` → `{"status":"UP"}` (public).
OpenAPI UI: `http://localhost:8080/swagger-ui.html` (needs a signed-in session in the same browser).

### Backend configuration (all environments)

| Variable | Required | Purpose |
|---|---|---|
| `PROJECTMODULE_DB_URL` | yes (default profile) | JDBC URL, e.g. `jdbc:postgresql://host:5432/projectmodule` |
| `PROJECTMODULE_DB_USERNAME` | yes (default profile) | Database user |
| `PROJECTMODULE_DB_PASSWORD` | yes | Database password — never commit it |
| `PROJECTMODULE_CORS_ALLOWED_ORIGINS` | no | Comma-separated browser origins allowed to call the API. Defaults to the Vite dev/preview ports (`http://localhost:5173,http://localhost:4173`), which is what the `npm run dev` workflow needs. A packaged deployment serves the UI from the same origin as the API, so it needs no CORS and no value here. Set it only when a frontend is hosted on a different origin. |
| `WORKEASY360_ISSUER`, `WORKEASY360_CLIENT_ID`, `WORKEASY360_REDIRECT_URI`, `WORKEASY360_KEY_ID`, `WORKEASY360_PRIVATE_KEY_FILE` (or `WORKEASY360_PRIVATE_KEY`) | all or none | Sign in with Workeasy360. With none of them, Workeasy360 sign-in is off and people use email sign-in; outside the `local`/`test` profiles the application **refuses to start** with only some of them. See [`docs/workeasy360-sso.md`](docs/workeasy360-sso.md). |
| `WORKEASY360_PRODUCT_CODE`, `WORKEASY360_POST_LOGOUT_REDIRECT_URI` | no | The product code the ID token must carry (default `easy_project`); the registered post-logout URI that enables "sign out of Workeasy360 too" |
| `WORKEASY360_SIGN_IN_MODE` | no | `sso_optional` (default: the sign-in page offers email and Workeasy360) or `sso_only` (Workeasy360 is the only way in: email sign-in endpoints refuse new sign-ins, and the app starts Workeasy360 sign-in by itself). `sso_only` needs the Workeasy360 settings. |
| `PROJECTMODULE_AUTH_TRUST_IDENTITY_HEADERS` | no | `false` by default. `true` only behind a gateway that sets `X-User-Id`/`X-Org-Id` and is the only way in; see [`docs/project/33-AUTHENTICATION.md`](docs/project/33-AUTHENTICATION.md) |
| `PROJECTMODULE_SESSION_IDLE_TIMEOUT`, `PROJECTMODULE_SESSION_COOKIE_SECURE` | no | Session idle timeout (default `8h`); `Secure` cookie flag (default `true`; the `local` profile sets `false`) |
| `SPRING_PROFILES_ACTIVE` | no | `local` for the developer profile above |
| `SPRING_MAIL_HOST` and related | no | Turns on email. See [`docs/project/34-DAILY-WORK-FEATURES.md`](docs/project/34-DAILY-WORK-FEATURES.md) |
| `PROJECTMODULE_GOOGLE_CLIENT_ID` | no | Turns on "Sign in with Google" |
| `PROJECTMODULE_APP_BASE_URL` | no | The public address used in email links |
| `ANTHROPIC_API_KEY` | no | Turns on the in-app assistant. See [`docs/project/35-ASSISTANT.md`](docs/project/35-ASSISTANT.md) |

### Backend tests

Persistence and integration tests run against a real PostgreSQL database and are skipped
unless the following are set (a throwaway database is recommended; tests clean the schema):

```powershell
$env:PROJECTMODULE_TEST_DB_URL = "jdbc:postgresql://localhost:5432/projectmodule_test"
$env:PROJECTMODULE_TEST_DB_USERNAME = "postgres"
$env:PROJECTMODULE_TEST_DB_PASSWORD = "<password>"
.\mvnw.cmd clean verify
```

## Frontend — run locally

```bash
cd frontend
npm install
npm run dev                   # http://localhost:5173 (fixed port; see vite.config.ts)
npm test                      # Vitest
npm run build                 # type-check + production build into frontend/dist
```

Run the backend at the same time. A development build calls `http://localhost:8080` by default,
so no `.env` is needed; the backend's CORS allowlist already permits the Vite port. Copy
`.env.example` to `.env` and set `VITE_API_BASE_URL` only to point the dev server at a different
backend. That file is ignored by Git and must never hold a secret, because everything in a
`VITE_` variable is embedded in the browser bundle.

### Running it the way it is deployed

```powershell
.mvnw.cmd clean package                      # builds the frontend and packages it in the jar
$env:SPRING_PROFILES_ACTIVE = "local"
$env:PROJECTMODULE_DB_PASSWORD = "<your local postgres password>"
java -jar targetproject-module-0.0.1-SNAPSHOT.jar
```

Then open <http://localhost:8080/> — the UI and the API are on one origin, exactly as in the
deployed environment. Add `-DskipFrontend=true` for a faster backend-only build; the packaged
application then has no UI and the tests that serve it are skipped rather than passing vacuously.

There are two ways to sign in, and the sign-in page offers both:

- **Email and password** (and "Sign in with Google", invitations, password reset, the hand-over of an
  earlier sign-in): create an account on the sign-in page, or use `POST /api/v1/auth/register`. The
  browser holds a bearer token. See [`docs/project/33-AUTHENTICATION.md`](docs/project/33-AUTHENTICATION.md).
- **Workeasy360** ("Continue with Workeasy360"), when the `WORKEASY360_*` settings are present: the
  backend creates a server-side session (`HttpOnly`, `SameSite=Lax`, CSRF-protected), and while it lasts
  Easy Project keeps asking Workeasy360 whether the person's organization may still use it. When a
  Workeasy360 sign-in ends, Workeasy360 ends the Easy Project sessions it opened, on every instance
  (back-channel logout, once its URI is registered). Without the settings, Workeasy360 sign-in is simply
  off; a deployment with only some of them refuses to start. With `WORKEASY360_SIGN_IN_MODE=sso_only`,
  Workeasy360 is the only way in and the app starts it by itself for a visitor with no session.
  Everything - how sign-in works, the variables, registration with
  `manage.py connect_product_sso --product easy_project ...`, entitlement behaviour, sign-out,
  back-channel logout - is in [`docs/workeasy360-sso.md`](docs/workeasy360-sso.md).

See [`frontend/README.md`](frontend/README.md) for the architecture, the feature registry (how to add a
screen without touching navigation), and the full route list.

## Identity, authorization and organizations

People sign in with email and password (see
[`docs/project/33-AUTHENTICATION.md`](docs/project/33-AUTHENTICATION.md)) or with Workeasy360 (see
[`docs/workeasy360-sso.md`](docs/workeasy360-sso.md)). The `account` package is the only code that
handles passwords and bearer tokens; the `security` package verifies Workeasy360's assertion and holds
its session. Everything else sees user and organization ids (`ExternalUserId`, `OrganizationId`)
through `RequestContext`. An email account's id is the user id; for a Workeasy360 sign-in the
organization is the ID token's `organization_id`, and the user id is the token's `sub` when that is a
UUID not already held by anyone (otherwise a generated one, remembered in `federated_identities`). One
user id never belongs to both an email account and a Workeasy360 identity. Authorization is per project
via `ProjectMember` roles (`OWNER`, `MANAGER`, `MEMBER`, `VIEWER`); every request is checked
server-side.

## Deployment notes

- Build the backend as a runnable JAR: `./mvnw -DskipTests package` → `target/project-module-*.jar`,
  run with `java -jar` and the environment variables above (`SERVER_PORT` to change the port).
- Build the frontend with the deployed API origin baked in:
  `VITE_API_BASE_URL=https://api.example.com npm run build`, then serve `frontend/dist` as static
  files (it uses hash routing, so no server-side rewrite rules are needed).
- Set `PROJECTMODULE_CORS_ALLOWED_ORIGINS` on the backend to the frontend's public origin.
- Database migrations are applied automatically by Flyway at startup; never edit an applied
  migration — add a new `V<n>__*.sql`.

## Repository hygiene

- `target/`, `frontend/node_modules/`, `frontend/dist/`, `.env*` (except `.env.example`) and
  local profile overrides are git-ignored. Secrets are never committed.
- Line endings are normalized by `.gitattributes` (`text=auto`) so Windows and Unix checkouts
  produce identical diffs.
