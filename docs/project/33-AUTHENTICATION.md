# 33 — Authentication (email and password)

Written for whoever operates or extends sign-in. This replaces the old sign-in, where a browser
typed in a User ID and Organization ID and sent them as headers. Those headers could be set by
anyone, so anyone could act as anyone.

## How it works

1. `POST /api/v1/auth/register` or `/auth/login` checks the password and returns a session token.
2. The browser sends `Authorization: Bearer <token>` on every request.
3. `RequestContextFilter` resolves the token to its account and builds the `RequestContext`.

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

## Trusting a gateway instead

If this service ever sits behind a gateway that authenticates callers and sets `X-User-Id` and
`X-Org-Id`, set `PROJECTMODULE_AUTH_TRUST_IDENTITY_HEADERS=true`. Do this **only** if the service
cannot be reached except through that gateway. A bearer token, valid or not, always takes
precedence over the headers.

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
can link the account to their old ids:

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
