# 32 — Deployment (AWS Elastic Beanstalk + RDS PostgreSQL)

Written for whoever operates this deployment. It records what the pipeline actually does, the
settings the application requires, and how the local and cloud databases are kept apart.

## Pipeline

`.github/workflows/deploy.yml` runs on every push to `main` (or by hand with "Run workflow").

The environment is a **single t3.micro instance** in an account with an **8-vCPU limit**. A release
therefore updates that instance in place, and the app restarts once, for about one to two minutes.

1. **Test.** All frontend tests, the type check, and all backend tests against a real PostgreSQL.
   If anything fails, nothing is deployed.
2. **Check and back up.** The environment must be Ready. A database backup must exist: an RDS
   snapshot, or, since the deploy user `github-deployer` may not take snapshots, RDS automated
   backups restorable to within the last 15 minutes. The log gives the restore point.
3. **Deploy in place.** The deployment policy is set to AllAtOnce, so no extra instance is
   launched. Then the new version is deployed to the existing instance.
4. **Check the instance and roll back if needed.** The new version is checked directly at the
   environment's address (not through Cloudflare). If the check fails, the previous version is
   deployed back.

**Do not change the health reporting type, instance type or other launch settings from the
pipeline.** Those make Elastic Beanstalk replace the instance. On 11 November 2026 turning on
enhanced health did exactly that. With the vCPU limit no replacement could start, and the site was
down until a new instance came up. An immutable or rolling deployment also needs a second instance,
which this account cannot launch. If no-downtime releases are wanted later, first raise the EC2
vCPU limit and give the instances the permissions enhanced health needs.

`.github/workflows/recover.yml` is a manual workflow: `diagnose` (read only), `restore`,
`cleanup`, `restart` and `rebuild`.

The deployment is a bundle, not a bare jar:
- `application.jar`
- `Procfile`, which caps Java at 60% of memory
- `.ebextensions/01-swap.config`, which adds a 1 GB swap file once
- `.platform/nginx/conf.d/workeasy.conf`, which raises nginx's upload limit to 26 MB for 25 MB
  attachments, and its proxy timeout to 120 s so the assistant can plan large projects

The bundle's extra files are in `deploy/`.

Database migrations must stay backward compatible: add tables and columns with defaults; never drop
or rename in the same release. That way a roll-back to the previous version still works on the
upgraded database.

The assistant's key: add a repository secret `ANTHROPIC_API_KEY` (GitHub → Settings → Secrets and
variables → Actions). The next deployment passes it to the environment, with no extra restart.
Without it, the assistant shows "not set up yet".

Repository secrets the workflow uses:
- `AWS_ACCESS_KEY_ID` and `AWS_SECRET_ACCESS_KEY`. Besides deploying, they need permission to
  describe the environment and RDS instances. Permission to create RDS snapshots is optional;
  without it, the pipeline relies on RDS automated backups.
- `ANTHROPIC_API_KEY` (optional).

**Sessions.** The backend has no view layer and no server-rendered pages. Email sign-in uses bearer tokens
kept (hashed) in the database, so they survive a deploy. A person signed in with **Workeasy360** has a
server-side session instead (see `docs/workeasy360-sso.md`).

**Workeasy360 sign-in is optional for this pipeline.** The workflow sets no `WORKEASY360_*` property. Without
them the application starts with Workeasy360 sign-in off (email sign-in only, as before), so deploying this
version changes nothing for existing users. To turn it on, add **all** of them as environment properties
(the private key as a file on the instance or as `WORKEASY360_PRIVATE_KEY`) in the same change: a deployment
with only some of them refuses to start, and the release check then puts the previous version back.

## One instance, and what a deploy does

Workeasy360 sessions and pending Workeasy360 sign-ins are held in memory by the instance that created them.
Run **a single instance** (as this environment does), or enable **sticky sessions** on the load balancer if you
ever scale out; a request that reaches a different instance has no session and a sign-in callback that reaches
a different instance finds no pending flow (`expired`). Every deploy or restart therefore **signs Workeasy360
users out** (email sign-ins are unaffected): with `WORKEASY360_SIGN_IN_MODE=sso_only` their next page load starts
a sign-in by itself and brings them back to the page they were on, without another password (Workeasy360's own
session continues); otherwise the sign-in page offers "Continue with Workeasy360". See
`docs/workeasy360-sso.md`, "Sessions, deployment shape and edge protection", which also asks for rate limiting of
`/api/auth/**` at Cloudflare.

**Back-channel logout** (`docs/workeasy360-sso.md`, "Back-channel logout") is not a reason for a single
instance: Workeasy360's logout `POST` reaches one instance, but the sign-in record it ends is shared in PostgreSQL
and every instance checks it on every signed-in request, so the person's sessions end on all of them (and a
restart cannot bring one back). Keep `POST /api/auth/workeasy360/backchannel-logout` reachable from the internet
without a browser challenge: the caller is Workeasy360's server.

## Required environment properties

Elastic Beanstalk's Java SE platform runs the jar behind nginx and expects the application on
port 5000, so `SERVER_PORT` must be `5000` (Spring maps it to `server.port`).

| Property | Purpose |
|---|---|
| `SERVER_PORT` | `5000` — the port the platform's nginx proxies to |
| `PROJECTMODULE_DB_URL` | `jdbc:postgresql://<rds-endpoint>:5432/<database>` |
| `PROJECTMODULE_DB_USERNAME` | RDS master user |
| `PROJECTMODULE_DB_PASSWORD` | RDS master password (set in the environment only) |
| `WORKEASY360_ISSUER` | **For Workeasy360 sign-in (all five, or none).** Workeasy360's issuer, `https://accounts.workeasy360.com` (WorkEasy360 Accounts, platform ADR-067; never `app.`, which serves no discovery document). With none of the five, Workeasy360 sign-in is off; with only some, the jar **refuses to start**. |
| `WORKEASY360_CLIENT_ID` | **For Workeasy360 sign-in.** The client id printed by `manage.py connect_product_sso --product easy_project ...`. |
| `WORKEASY360_REDIRECT_URI` | **For Workeasy360 sign-in.** `https://ops.workeasy360.com/api/auth/callback/workeasy360`, exactly as registered. |
| `WORKEASY360_KEY_ID` | **For Workeasy360 sign-in.** The credential id printed at registration. |
| `WORKEASY360_PRIVATE_KEY_FILE` / `WORKEASY360_PRIVATE_KEY` | **For Workeasy360 sign-in (one of them).** The PEM private key matching the registered public key; the variable form accepts literal backslash-n for newlines. Keep it out of the repository and the workflow. |
| `WORKEASY360_POST_LOGOUT_REDIRECT_URI` | Optional. `https://ops.workeasy360.com/`; enables "Sign out of Easy Project and Workeasy360". |
| `WORKEASY360_SIGN_IN_MODE` | Optional. `sso_optional` (default; register the product `sso_optional` with `login_url` = the sign-in page) or `sso_only` (Workeasy360 is the only way in; needs the five settings above, and refuses new email sign-ins). |
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
| `/api/v1/dashboard` | `401` without a session token or a Workeasy360 sign-in session (the old `X-User-Id` / `X-Org-Id` headers are ignored); `200` with `Authorization: Bearer <token>` or a signed-in session. A `200` here proves the database is connected. |
| `/api/v1/auth/config` | Public. What the sign-in page offers, including `workeasy360.enabled` and `workeasy360.signInMode`. The release check looks for `legacyHandover` here. |
| `/api/auth/session` | `401` without a Workeasy360 session; `200` with who is signed in. |
| `/api/auth/workeasy360/start` | `302` to Workeasy360 (or to `/#/sign-in?sso_error=unavailable` when it cannot be reached or is not configured). |
| `POST /api/auth/workeasy360/backchannel-logout` | Workeasy360's back-channel logout. `400 {"error":"invalid_request"}` to anything but a valid logout token, with no session or CSRF token needed; `405` to a `GET`. |
| `/swagger-ui.html` | API documentation (needs a signed-in Workeasy360 session in the same browser). |
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

- **Serving the UI from the same origin grants nothing.** Every API request needs a credential: an email
  sign-in's bearer token (see `33-AUTHENTICATION.md`, including the step that keeps access to existing data
  before deploying) or the session that "Sign in with Workeasy360" creates (`docs/workeasy360-sso.md`). The
  former `X-User-Id` / `X-Org-Id` headers are ignored unless a deployment declares a trusted gateway.
  Membership and cross-organisation checks are untouched.
- **The AI provider is still unavailable.** `UnavailableAIProviderAdapter` throws
  `IntegrationUnavailableException`, so AI requests return `503 integration-unavailable` rather
  than inventing an answer. Nothing in this packaging changes that.
- **No AWS resource, credential or environment variable was created or altered.**

## Remaining manual steps

- The Elastic Beanstalk URL itself serves **HTTP, not HTTPS**. Passwords and session tokens would
  travel in clear text there, so sign in only through `https://ops.workeasy360.com`, where
  Cloudflare terminates TLS. The Cloudflare-to-origin leg should also be confirmed as encrypted.
  Workeasy360 sign-in needs HTTPS at the public address anyway (its session cookie is `Secure`, and the
  production start-up check requires an https issuer and redirect URI): a direct HTTP environment URL cannot
  hold a Workeasy360 session.
- The RDS instance accepts connections on 5432 from the public internet. It should be restricted
  to the Elastic Beanstalk instances' security group.
- The health check path recommendation below still applies.
