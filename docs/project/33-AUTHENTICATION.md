# 33 — Authentication (email and password)

Written for whoever operates or extends sign-in. This replaces the old sign-in, where a browser
typed in a User ID and Organization ID and sent them as headers. Those headers could be set by
anyone, so anyone could act as anyone.

## How it works

1. `POST /api/v1/auth/register` or `/auth/login` checks the password and returns a session token.
2. The browser sends `Authorization: Bearer <token>` on every request to `/api/v1/**`.
3. In the security chain, `AccountTokenFilter` resolves the token to its account (through
   `BearerTokenAuthenticator`, which the account package implements), for that request only, and the
   `RequestContext` is built from it.

Nothing downstream changed. Projects, tasks and members keep their existing tables and permission
checks, because an account's `id` is the user id they already store and its `organization_id` is
the tenant they are already scoped by.

| Endpoint | Purpose | Answers |
|---|---|---|
| `POST /auth/register` | Create an account in a new organization, and sign in | 201, 400, 409 if the email is taken |
| `POST /auth/login` | Sign in | 200, 401 |
| `POST /auth/logout` | End this session | 204 |
| `GET /auth/me` | The signed-in account | 200, 401 |

## Security rules

| Rule | Detail |
|---|---|
| Passwords | BCrypt hash only. 8 characters minimum, 72 bytes maximum (BCrypt ignores anything beyond that). |
| Session tokens | 256 random bits. Only the SHA-256 is stored, so reading the database does not yield a session. |
| Session length | 30 days with "Remember me", otherwise 12 hours. |
| Sign-out | Revokes the session on the server immediately. |
| Wrong passwords | 5 in a row lock the account for 15 minutes. |
| Account discovery | A wrong password and an unknown email get the same message and take about the same time. Registering with a taken email does reveal it is taken. |
| Identity headers | `X-User-Id` and `X-Org-Id` are ignored. See below. |
| CSRF | Not needed with a bearer token: the request is identified by the header, never by a cookie, so a dead token is a `401` (the app signs out), never a CSRF `403`. The public endpoints in the table above need no CSRF token either. |

## Trusting a gateway instead

If this service ever sits behind a gateway that authenticates callers and sets `X-User-Id` and
`X-Org-Id`, set `PROJECTMODULE_AUTH_TRUST_IDENTITY_HEADERS=true`. Do this **only** if the service
cannot be reached except through that gateway. A bearer token, valid or not, always takes
precedence over the headers, and the headers take precedence over a Workeasy360 session cookie (such a
request is not identified by the cookie, so CSRF does not apply to it). The production deployment does
not set this.

## Alongside "Sign in with Workeasy360"

When the `WORKEASY360_*` settings are present, the sign-in page also offers "Continue with Workeasy360"
(`docs/workeasy360-sso.md`). Without them nothing here changes. The two ways in stay apart:

- **One user id, one credential holder.** An email account and a Workeasy360 account are never the same
  user automatically, and never matched by email address. A first Workeasy360 sign-in does not adopt an
  email account's id, and the hand-over below refuses an id that a Workeasy360 sign-in already holds
  ("This earlier sign-in now belongs to a Workeasy360 account"). Both sides take the same lock on the id
  first, so they cannot both win.
- **Email-account features are for email accounts.** `GET/PATCH /auth/me`, `/auth/change-password`,
  invitations and reset links answer a Workeasy360 session `403 managed_in_workeasy360`: its profile,
  password and organization membership are managed in Workeasy360. `GET /workspace/people` lists the
  organization's email accounts, which for a Workeasy360 organization is usually nobody.
- **Workeasy360 only.** With `WORKEASY360_SIGN_IN_MODE=sso_only`, registering, signing in, Google,
  the hand-over, invitations and password resets answer `403 sign_in_with_workeasy360`. Email sessions
  already open keep working until they end; signing out stays possible.
- Workeasy360's entitlement check and back-channel logout concern Workeasy360 sessions only.

## Moving existing customers over (the hand-over)

Before email sign-in, people were identified by a user id and an organization id kept in their
browser. Their projects belong to those ids. A newly registered account gets new ids, so it would
start with an empty workspace.

The hand-over keeps everyone's work without any manual step:
- When a browser still holds the old ids, the sign-in page shows "Welcome back! One quick step"
  instead of the empty sign-in form.
- The person enters their name, an email and a password. `POST /api/v1/auth/legacy/claim` then
  creates their account **with their old ids**, so every project, task and comment is still theirs.
- The first person to move over in an organization becomes its workspace Admin. Everyone after
  joins as a Member.

Rules (`LegacyHandoverService`):
- It works only for ids that own or belong to a project in that organization, so made-up ids are
  refused.
- It works once per id. After that, only the account's password gets in.
- It trusts the ids exactly as the product did before email sign-in, and only once.
- Turn it off once everyone has moved over: `PROJECTMODULE_AUTH_LEGACY_HANDOVER_ENABLED=false`.
  The page then shows the normal sign-in.

Someone who cleared their browser has lost the old ids. They can register normally, and an operator
can link the account to their old ids, after checking that no Workeasy360 sign-in holds them
(`SELECT 1 FROM federated_identities WHERE user_id = '<their old user id>'` returns nothing):

```sql
UPDATE user_accounts
SET id = '<their old user id>', organization_id = '<their old organization id>'
WHERE email = '<their email>';
```

The rehearsal on 11 November 2026 confirmed the upgrade path against a copy of the live version:
- Data made on the old version survived the database upgrade.
- The old version kept working while both ran on the same database.
- Two teammates each moved over and kept their shared project.

## Also available

Invitations, profile editing, changing a password, "Forgot password" and "Sign in with Google" are
described in `34-DAILY-WORK-FEATURES.md`, along with the settings they need. Without email, an admin
can create a one-time password reset link for a teammate from My Team.

## Known limits

- **Rate limiting by IP address:** lockout is per account only.
- **Email addresses are not verified** at registration.
- **HTTPS is required.** Passwords and tokens travel in the request. Cloudflare terminates TLS for
  `ops.workeasy360.com`, but the Elastic Beanstalk URL itself serves plain HTTP and should not be
  used to sign in.
