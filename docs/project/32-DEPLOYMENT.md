# 32 — Deployment (AWS Elastic Beanstalk + RDS PostgreSQL)

Written for whoever operates this deployment. It records what the pipeline actually does, the
settings the application requires, and how the local and cloud databases are kept apart.

## Pipeline

`.github/workflows/deploy.yml` runs on every push to `main`:

1. Checks out the repository, installs Corretto 21.
2. `mvn clean package -DskipTests` → produces `target/project-module-0.0.1-SNAPSHOT.jar`.
   That one command now also builds the React frontend: `frontend-maven-plugin` downloads the
   pinned Node, runs `npm ci` against `frontend/package-lock.json` and then `npm run build`
   (which is `tsc -b && vite build`). The built files are copied to `target/classes/static` and
   travel inside the jar. A type error or a failed bundle fails Maven, which fails the workflow.
3. `einaregilsson/beanstalk-deploy@v21` uploads that jar to Elastic Beanstalk.

The workflow file itself needed no change for this: the frontend build hangs off the Maven
lifecycle, and the artifact path was already the literal jar name.

| Setting | Value |
|---|---|
| Application | `Project-Module` |
| Environment | `Project-Module-env` |
| Region | `eu-north-1` |
| Artifact | `target/project-module-0.0.1-SNAPSHOT.jar` (matches `artifactId` + `version` in `pom.xml`) |
| Version label | `v<github.run_number>` |

The artifact path is a literal filename because the deploy action does not expand wildcards.
If `pom.xml`'s `artifactId` or `version` ever changes, this path must change with it.

**One deployment serves both.** The jar contains the API and the UI, so the environment URL
serves the application and `/api/v1/**` on the same origin. The backend remains API-only in its
architecture: it has no view layer and no server-rendered pages, and simply serves the built files as
static resources from the classpath. It does hold a server-side **session** for the person signed in with
Workeasy360 (see `docs/workeasy360-sso.md`).

## One instance, and what a deploy does

Sessions and pending sign-ins are held in memory by the instance that created them. Run **a single instance**
(as this environment does), or enable **sticky sessions** on the load balancer if you ever scale out; a request
that reaches a different instance has no session and a sign-in callback that reaches a different instance
finds no pending flow (`expired`). Every deploy or restart therefore **signs everyone out**: they see the
sign-in page and one click on "Continue with Workeasy360" brings them back, without another password
(Workeasy360's own session continues). See `docs/workeasy360-sso.md`, "Sessions, deployment shape and edge
protection", which also asks for rate limiting of `/api/auth/**` at Cloudflare.

## Required environment properties

Elastic Beanstalk's Java SE platform runs the jar behind nginx and expects the application on
port 5000, so `SERVER_PORT` must be `5000` (Spring maps it to `server.port`).

| Property | Purpose |
|---|---|
| `SERVER_PORT` | `5000` — the port the platform's nginx proxies to |
| `PROJECTMODULE_DB_URL` | `jdbc:postgresql://<rds-endpoint>:5432/<database>` |
| `PROJECTMODULE_DB_USERNAME` | RDS master user |
| `PROJECTMODULE_DB_PASSWORD` | RDS master password (set in the environment only) |
| `WORKEASY360_ISSUER` | **Required.** Workeasy360's issuer, e.g. `https://app.workeasy360.com`. The jar **refuses to start** without it and the four settings below. |
| `WORKEASY360_CLIENT_ID` | **Required.** The client id printed by `manage.py connect_product_sso --product easy_project ...`. |
| `WORKEASY360_REDIRECT_URI` | **Required.** `https://ops.workeasy360.com/api/auth/callback/workeasy360`, exactly as registered. |
| `WORKEASY360_KEY_ID` | **Required.** The credential id printed at registration. |
| `WORKEASY360_PRIVATE_KEY_FILE` / `WORKEASY360_PRIVATE_KEY` | **Required (one of them).** The PEM private key matching the registered public key; the variable form accepts literal backslash-n for newlines. Keep it out of the repository and the workflow. |
| `WORKEASY360_POST_LOGOUT_REDIRECT_URI` | Optional. `https://ops.workeasy360.com/`; enables "Sign out of Easy Project and Workeasy360". |
| `PROJECTMODULE_SESSION_IDLE_TIMEOUT` | Optional. Session idle timeout, default `8h`. |
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
| `/api/v1/dashboard` | `401` without a sign-in session (the old `X-User-Id` / `X-Org-Id` headers are ignored); `200` for a signed-in person. |
| `/api/auth/session` | `401` without a session; `200` with who is signed in. |
| `/api/auth/workeasy360/start` | `302` to Workeasy360 (or to `/#/sign-in?sso_error=unavailable` when it cannot be reached). |
| `/swagger-ui.html` | API documentation (needs a signed-in session in the same browser). |
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
| development (`npm run dev`) | empty; the Vite server proxies `/api` to `http://localhost:8080` | Keeps the browser on one origin so the session cookie, CSRF cookie and sign-in redirects work. |
| either, with `VITE_API_BASE_URL` | that value | For hosting the UI apart from the API. |

No AWS hostname appears anywhere in the source. `VITE_` variables are embedded in the browser
bundle, so they may hold a public address but never a secret.

The production build emits no source maps, and only `index.html`, `favicon.svg` and the hashed
`assets/` bundles are copied into the jar — no `.env`, config or source file is packaged.

## Local development

Two ways to run it:

- **Two processes (fast feedback):** `mvnw spring-boot:run` with the `local` profile, and
  `npm run dev` in `frontend/`. The UI is on :5173 and the Vite server proxies `/api` to the backend on
  :8080, so the browser sees one origin. Sign-in needs a Workeasy360 client registered with the redirect
  URI `http://localhost:5173/api/auth/callback/workeasy360` (`docs/workeasy360-sso.md`).
- **One process (as deployed):** `mvnw clean package` then run the jar, and open
  <http://localhost:8080/>. Same-origin, exactly like the deployed environment.

`-DskipFrontend=true` skips the frontend build for a quicker backend-only loop. The resulting
application has no UI, and the tests that serve it skip rather than pass vacuously.

## What this does not change

- **Serving the UI from the same origin grants nothing.** Every API request needs the session that
  "Sign in with Workeasy360" creates (`docs/workeasy360-sso.md`); the former `X-User-Id` / `X-Org-Id`
  trust-the-gateway headers are gone. Membership and cross-organisation checks are untouched.
- **The AI provider is still unavailable.** `UnavailableAIProviderAdapter` throws
  `IntegrationUnavailableException`, so AI requests return `503 integration-unavailable` rather
  than inventing an answer. Nothing in this packaging changes that.
- **No AWS resource, credential or environment variable was created or altered.**

## Remaining manual steps

- Sign-in requires **HTTPS** at the public address (the session cookie is `Secure`, and the production
  start-up check requires an https issuer and redirect URI). The site is reached through Cloudflare, which
  terminates TLS; a direct HTTP environment URL cannot hold a sign-in session.
- The RDS instance accepts connections on 5432 from the public internet. It should be restricted
  to the Elastic Beanstalk instances' security group.
- The health check path recommendation below still applies.
