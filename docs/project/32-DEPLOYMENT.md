# 32 — Deployment (AWS Elastic Beanstalk + RDS PostgreSQL)

Written for whoever operates this deployment. It records what the pipeline actually does, the
settings the application requires, and how the local and cloud databases are kept apart.

## Pipeline

`.github/workflows/deploy.yml` runs on every push to `main`:

1. Checks out the repository, installs Corretto 21.
2. `mvn clean package -DskipTests` → produces `target/project-module-0.0.1-SNAPSHOT.jar`.
3. `einaregilsson/beanstalk-deploy@v21` uploads that jar to Elastic Beanstalk.

| Setting | Value |
|---|---|
| Application | `Project-Module` |
| Environment | `Project-Module-env` |
| Region | `eu-north-1` |
| Artifact | `target/project-module-0.0.1-SNAPSHOT.jar` (matches `artifactId` + `version` in `pom.xml`) |
| Version label | `v<github.run_number>` |

The artifact path is a literal filename because the deploy action does not expand wildcards.
If `pom.xml`'s `artifactId` or `version` ever changes, this path must change with it.

Only the **backend** is deployed. The React frontend in `frontend/` is not built or published by
this workflow; it currently has no deployment target.

## Required environment properties

Elastic Beanstalk's Java SE platform runs the jar behind nginx and expects the application on
port 5000, so `SERVER_PORT` must be `5000` (Spring maps it to `server.port`).

| Property | Purpose |
|---|---|
| `SERVER_PORT` | `5000` — the port the platform's nginx proxies to |
| `PROJECTMODULE_DB_URL` | `jdbc:postgresql://<rds-endpoint>:5432/<database>` |
| `PROJECTMODULE_DB_USERNAME` | RDS master user |
| `PROJECTMODULE_DB_PASSWORD` | RDS master password (set in the environment only) |
| `PROJECTMODULE_CORS_ALLOWED_ORIGINS` | Comma-separated origins of the deployed frontend. Defaults to the local Vite ports only, so browser calls from a deployed frontend fail until this is set. |

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

This is an API-only service. It maps nothing at `/`, so a browser visiting the environment URL
correctly receives `404 No endpoint exists at this path` — that is a healthy server, not a broken
one. Use these instead:

| Path | Expected |
|---|---|
| `/actuator/health` | `200 {"status":"UP"}`. Reports `DOWN` if the database is unreachable, so it is a real health signal. |
| `/api/v1/dashboard` | `401` without identity headers, `200` with `X-User-Id` and `X-Org-Id`. A `200` here proves the database is connected. |
| `/swagger-ui.html` | API documentation. |

**Recommended, not yet applied:** Elastic Beanstalk's default health check path is `/`. Pointing
it at `/actuator/health` (console → Configuration → Monitoring → Health check path) makes the
environment's health reflect the application and its database. Changing it alters environment
configuration, so it is left to an operator to apply.

## Frontend

`frontend/src/api/client.ts` reads `VITE_API_BASE_URL` and falls back to `http://localhost:8080`.
A production build made without that variable will call localhost and fail. Set it at build time
to the backend's public URL. Do not put secrets in `VITE_` variables; they are embedded in the
browser bundle. The app uses `HashRouter`, so deep links need no server-side rewrite rules.

Once a frontend origin exists, add it to `PROJECTMODULE_CORS_ALLOWED_ORIGINS` on the backend
environment, and keep both on HTTPS or both on HTTP — a browser blocks HTTPS pages calling HTTP.
