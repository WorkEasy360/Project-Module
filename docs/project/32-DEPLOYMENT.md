# 32 — Deployment (AWS Elastic Beanstalk + RDS PostgreSQL)

Written for whoever operates this deployment. It records what the pipeline actually does, the
settings the application requires, and how the local and cloud databases are kept apart.

## Pipeline

`.github/workflows/deploy.yml` runs on every push to `main` (or by hand, with "Run workflow"). It
is written so that customers using the product never notice a release:

1. **Test.** All frontend tests, the type check, and all backend tests against a real PostgreSQL.
   If anything fails, nothing is deployed.
2. **Check, then back up.** It checks:
   - the environment must be Ready
   - enhanced health reporting must be on
   - the current version is recorded

   Then it takes an RDS snapshot named `before-<run>-<attempt>-<time>` and waits for it to be
   available. Nothing in AWS changes before this point.
3. **Immutable deployment.** The environment's deployment policy is set to Immutable. Elastic
   Beanstalk then starts the new version on fresh instances and checks its health before moving
   traffic to it. On a single-instance environment it moves the Elastic IP. If the new version is
   unhealthy, it is discarded and the old one keeps serving.
4. **Live check, and roll back if needed.** It checks the live site's health, the new page title
   and the sign-in configuration. If that fails, the previous version is deployed back
   automatically.

The deployment is a bundle, not a bare jar:
- `application.jar`
- `Procfile`
- `.platform/nginx/conf.d/workeasy.conf`, which raises nginx's upload limit to 26 MB for 25 MB
  attachments, and its proxy timeout to 120 s so the assistant can plan large projects

The bundle's extra files are in `deploy/`.

Database migrations must stay backward compatible: add tables and columns with defaults; never drop
or rename in the same release. During an immutable deployment the old and new versions briefly
share the database.

The assistant's key: add a repository secret `ANTHROPIC_API_KEY` (GitHub → Settings → Secrets and
variables → Actions). The next deployment passes it to the environment, with no extra restart.
Without it, the assistant shows "not set up yet".

Repository secrets the workflow uses:
- `AWS_ACCESS_KEY_ID` and `AWS_SECRET_ACCESS_KEY`. Besides deploying, they need permission to
  describe the environment and RDS instances, and to create RDS snapshots.
- `ANTHROPIC_API_KEY` (optional).

## Required environment properties

Elastic Beanstalk's Java SE platform runs the jar behind nginx and expects the application on
port 5000, so `SERVER_PORT` must be `5000` (Spring maps it to `server.port`).

| Property | Purpose |
|---|---|
| `SERVER_PORT` | `5000` — the port the platform's nginx proxies to |
| `PROJECTMODULE_DB_URL` | `jdbc:postgresql://<rds-endpoint>:5432/<database>` |
| `PROJECTMODULE_DB_USERNAME` | RDS master user |
| `PROJECTMODULE_DB_PASSWORD` | RDS master password (set in the environment only) |
| `PROJECTMODULE_CORS_ALLOWED_ORIGINS` | **Not needed for this deployment.** The UI is served from the same origin as the API, so the browser makes no cross-origin request and no CORS exchange occurs. The default (the local Vite ports) exists for `npm run dev`. Set this only if a frontend is ever hosted on a different origin. |

`SPRING_DATASOURCE_URL` / `SPRING_DATASOURCE_USERNAME` / `SPRING_DATASOURCE_PASSWORD` are also
honoured and take precedence over the names above. This was verified by starting the jar with
only the `SPRING_DATASOURCE_*` names set; it connected normally. Use one naming scheme, not both.

The RDS *instance identifier* and the *database name* are different things. The instance
identifier is `projectmodule-test`; the database name is the last path segment of the JDBC URL.
An RDS instance created without an explicit initial database name has only the built-in
`postgres` database, which is why the recorded URL ends in `/postgres`. Flyway will create this
application's tables in whichever database the URL names.

## Local versus cloud

The two are separated by Spring profile, and neither can reach the other by accident:

- **Local** — start with `SPRING_PROFILES_ACTIVE=local`. `application-local.yml` defaults the URL
  to `jdbc:postgresql://localhost:5432/projectmodule` and the user to `postgres`; only
  `PROJECTMODULE_DB_PASSWORD` must be supplied. No AWS credentials are involved.
- **Deployed** — no profile. `application.yml` declares the datasource with **no defaults**, so a
  deployment that is missing its settings fails at startup instead of quietly connecting to
  something unintended. Verified: with the variables removed the application exits and never
  attempts `localhost:5432`.

No password is committed. `.gitignore` excludes `.env`, `*.local.yml` and
`application-local-*.yml`; `frontend/.gitignore` excludes `frontend/.env`.

When the settings are absent the connection pool used to report only
`Driver org.postgresql.Driver claims to not accept jdbcUrl, ${PROJECTMODULE_DB_URL}`, which names
neither the missing variable nor the fix. `DataSourceConfigurationFailureAnalyzer` now replaces
that with the list of settings to provide. It runs only after a startup failure.

## Endpoints worth knowing

The environment URL now opens the application. The API is unchanged and shares the origin.

| Path | Expected |
|---|---|
| `/` | The React application (`200 text/html`). |
| `/assets/...` | The hashed JavaScript and CSS bundles the entry page references. |
| `/actuator/health` | `200 {"status":"UP"}`. Reports `DOWN` if the database is unreachable, so it is a real health signal. |
| `/api/v1/dashboard` | `401` without a session token, `200` with `Authorization: Bearer <token>`. A `200` here proves the database is connected. |
| `/swagger-ui.html` | API documentation. |
| `/api/v1/<anything unmapped>` | `404` as a problem document, never the application HTML. |
| `/<anything unmapped>` | `404` as a problem document. The UI uses `HashRouter`, so its routes live in the fragment (`/#/projects`) and the browser never asks the server for them. There is deliberately no catch-all rewriting unknown paths to `index.html`, because that would turn genuine API and server errors into a silent HTML page. |

**Recommended, not yet applied:** Elastic Beanstalk's default health check path is `/`. Pointing
it at `/actuator/health` (console → Configuration → Monitoring → Health check path) makes the
environment's health reflect the application and its database. Changing it alters environment
configuration, so it is left to an operator to apply.

## Frontend

`frontend/src/api/client.ts` resolves the API address at build time:

| Build | API base | Why |
|---|---|---|
| production (`vite build`) | empty, so requests go to `/api/v1/...` | Same origin as the page that served the app. No CORS, and no hostname baked into the bundle. |
| development (`npm run dev`) | `http://localhost:8080` | The Vite server is a different origin from the backend; the CORS default already allows it. |
| either, with `VITE_API_BASE_URL` | that value | For hosting the UI apart from the API. |

No AWS hostname appears anywhere in the source. `VITE_` variables are embedded in the browser
bundle, so they may hold a public address but never a secret.

The production build emits no source maps, and only `index.html`, `favicon.svg` and the hashed
`assets/` bundles are copied into the jar — no `.env`, config or source file is packaged.

## Local development

Two ways to run it:

- **Two processes (fast feedback):** `mvnw spring-boot:run` with the `local` profile, and
  `npm run dev` in `frontend/`. The UI is on :5173 and calls the backend on :8080 across origins,
  which the CORS default permits. This is unchanged by the packaging work.
- **One process (as deployed):** `mvnw clean package` then run the jar, and open
  <http://localhost:8080/>. Same-origin, exactly like the deployed environment.

`-DskipFrontend=true` skips the frontend build for a quicker backend-only loop. The resulting
application has no UI, and the tests that serve it skip rather than pass vacuously.

## What this does not change

- **Authentication is separate.** Serving the UI from the same origin grants nothing. People sign
  in with email and password; see `33-AUTHENTICATION.md`, including the step that keeps access to
  existing data before deploying. Membership and cross-organisation checks are untouched.
- **The AI provider is still unavailable.** `UnavailableAIProviderAdapter` throws
  `IntegrationUnavailableException`, so AI requests return `503 integration-unavailable` rather
  than inventing an answer. Nothing in this packaging changes that.
- **No AWS resource, credential or environment variable was created or altered.**

## Remaining manual steps

- The Elastic Beanstalk URL itself serves **HTTP, not HTTPS**. Passwords and session tokens would
  travel in clear text there, so sign in only through `https://ops.workeasy360.com`, where
  Cloudflare terminates TLS. The Cloudflare-to-origin leg should also be confirmed as encrypted.
- The RDS instance accepts connections on 5432 from the public internet. It should be restricted
  to the Elastic Beanstalk instances' security group.
- The health check path recommendation below still applies.
