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

Health check: `GET http://localhost:8080/actuator/health` → `{"status":"UP"}`.
OpenAPI UI: `http://localhost:8080/swagger-ui.html`.

### Backend configuration (all environments)

| Variable | Required | Purpose |
|---|---|---|
| `PROJECTMODULE_DB_URL` | yes (default profile) | JDBC URL, e.g. `jdbc:postgresql://host:5432/projectmodule` |
| `PROJECTMODULE_DB_USERNAME` | yes (default profile) | Database user |
| `PROJECTMODULE_DB_PASSWORD` | yes | Database password — never commit it |
| `PROJECTMODULE_CORS_ALLOWED_ORIGINS` | no | Comma-separated browser origins allowed to call the API. Defaults to the Vite dev/preview ports (`http://localhost:5173,http://localhost:4173`), which is what the `npm run dev` workflow needs. A packaged deployment serves the UI from the same origin as the API, so it needs no CORS and no value here. Set it only when a frontend is hosted on a different origin. |
| `SPRING_PROFILES_ACTIVE` | no | `local` for the developer profile above |

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

The app has no login screen by design: the backend expects an upstream gateway to supply
`X-User-Id` and `X-Org-Id` headers. The frontend asks for those two UUIDs once ("Set identity",
top-right) and sends them on every request. See [`frontend/README.md`](frontend/README.md) for
the architecture, the feature registry (how to add a screen without touching navigation), and
the full route list.

## Identity, authorization and organizations

Users and organizations are external, opaque identifiers (`ExternalUserId`, `OrganizationId`).
Authorization is per project via `ProjectMember` roles (`OWNER`, `MANAGER`, `MEMBER`,
`VIEWER`); every request is checked server-side. The module never stores or authenticates
users itself.

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
