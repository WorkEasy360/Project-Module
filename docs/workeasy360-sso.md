# Sign in with Workeasy360

**Written for: whoever deploys and operates Easy Project.**

Easy Project has no sign-in of its own. People sign in with Workeasy360 (the registered sign-in mode is
`sso_only`), and Easy Project keeps asking Workeasy360 whether their organization may still use it. This
document is everything you need to register the product, configure a deployment, and understand what you
will see in production.

Contents: [How sign-in works](#how-sign-in-works) - [Opening Easy Project directly](#opening-easy-project-directly-automatic-sign-in) -
[Who is who](#who-is-who-the-provisioning-rules) -
[Registering Easy Project](#registering-easy-project-in-workeasy360) - [Configuration](#configuration) -
[Entitlement while a session lasts](#entitlement-while-a-session-lasts) -
[Background work](#background-work) - [Sign-out](#sign-out) - [Back-channel logout](#back-channel-logout) -
[Account switching](#account-switching) -
[Sign-in errors](#sign-in-errors) - [Running locally](#running-locally) -
[Security properties](#security-properties) - [What this does not do](#what-this-does-not-do)

## How sign-in works

It is OpenID Connect, authorization code flow with PKCE, with `private_key_jwt` client authentication. The
protocol is Spring Security's OAuth2 client, customised to the Workeasy360 product contract
(`docs/phase-3/09-product-integration-guide.md` and `docs/universal-sso/completion-design.md` in the
Workeasy360 repository). The code is in `com.projectmodule.security`.

1. The person opens Easy Project from the Workeasy360 launcher, which sends them to
   `https://ops.workeasy360.com/?organization=<uuid>`. That always starts a sign-in for that organization,
   even if they already have a session (they picked that organization). Or they type
   `https://ops.workeasy360.com/` (or any route, such as `/#/projects/123`): with **no Easy Project session at
   all**, the app starts the sign-in itself, with no organization hint, and Workeasy360 completes it silently
   when it already has a session for them (or asks, or shows its own sign-in; see
   [Opening Easy Project directly](#opening-easy-project-directly-automatic-sign-in)). **The sign-in page never
   starts Workeasy360 sign-in by itself**, and neither does anything after a sign-out of Easy Project in
   that browser: there the person chooses "Continue with Workeasy360".
2. `GET /api/auth/workeasy360/start?organization=<uuid>&next=<route>` creates `state`, `nonce` and a PKCE
   S256 challenge, keeps them (with the organization asked for and where to land) **server-side in the
   browser's session, for ten minutes and one use**, and redirects to Workeasy360's authorization endpoint.
   `organization` is forwarded only when it is a UUID. The `redirect_uri` is the configured
   `WORKEASY360_REDIRECT_URI`, character for character; it is never built from the request.
3. Workeasy360 authenticates the person, applies their sign-in preference, checks the organization, the
   entitlement and the seat, and redirects back to `/api/auth/callback/workeasy360?code=...&state=...`.
4. The callback consumes the pending flow first, whatever happens next. A provider `error` becomes a
   sign-in error code (below). The state is compared in constant time. The code is exchanged at the token
   endpoint with a `private_key_jwt` assertion (issuer and subject = the client id, audience = the token
   endpoint, a unique `jti`, 60 seconds) and the PKCE verifier.
5. The ID token is verified offline: key by `kid` from Workeasy360's key set (cached; re-read once for an
   unknown `kid`), algorithm restricted to `RS256` and `ES256` (never taken from the token), `iss` equal to
   the configured issuer, `aud` exactly this client id, `exp`/`nbf`/`iat` with 30 seconds of leeway, `typ`
   equal to `id_token`, `jti` present, the nonce, `organization_id` present, `product_code` equal to
   `WORKEASY360_PRODUCT_CODE` (default `easy_project`). The userinfo endpoint is never called.
6. If the sign-in was started for a particular organization, the token's `organization_id` must be that
   organization (`wrong_organization` otherwise).
7. The person is mapped to an internal user id (next section) and a **new** session is created. The session
   holds the user id, the organization id, the issuer, the subject, the e-mail address and name (profile
   data), the assurance level and the authentication time. It holds no token of any kind. The session is also
   recorded against the person's (issuer, subject) and the ID token's `sid`, the Workeasy360 sign-in it came from,
   so that a [back-channel logout](#back-channel-logout) can end it. Workeasy360 sends `sid` only once the
   back-channel logout URI is registered.
8. The browser lands on the app at the route in `next` (`/#/projects/123`), or on the home page.

Everything the API does afterwards is for that user in that organization. **The organization is pinned to
the session**: nothing the browser sends later can change it. The old `X-User-Id` / `X-Org-Id` headers are
ignored; there is no mode, profile or setting that reads them.

### `next`

The app uses hash routes, so `next` is a route written as a path, such as `/projects/123`; after sign-in the
browser is sent to `/#/projects/123`. It is validated as a same-site path: it must start with a single `/`
(not `//`), contain no backslash, whitespace, control character or `#`, be at most 2,048 characters, and not
be an authentication page (`/sign-in`) or an API path; percent-encoded slashes, backslashes and control
characters are refused too. Anything else lands on the home page. The single-page app applies the same rules before it ever builds a
start URL (`safeNextRoute` in `frontend/src/api/session.ts`), so a hostile value is not forwarded at all, and
the server checks again.

## Opening Easy Project directly (automatic sign-in)

A person who is already signed in to Workeasy360 and types `https://ops.workeasy360.com/` arrives signed in,
without Easy Project's sign-in page and without a password (Workeasy360 product integration guide, section
6b). Easy Project has no password sign-in of its own, so whenever Workeasy360 is configured it is SSO-only
and the rule applies in full.

**Start, do not ask.** The server cannot see the route (it is in the `#` fragment), so the single-page app does
this. `SessionProvider` asks `GET /api/auth/session`; if the answer is "no session" (401), `AppLayout` renders
`SignedOutGate`, which navigates the browser (top-level, never `fetch`) to

```
/api/auth/workeasy360/start?next=<the route that was asked for>
```

and shows a neutral "Signing you in with Workeasy360..." screen (one `h1`, no form, no link, text in the
theme's text colours on the solid card colour, `aria-busy` while waiting) while the browser leaves. The start
replaces the current history entry (`location.replace`), so Back from Workeasy360's page does not land on a page
that bounces forward again; the explicit "Continue with Workeasy360" link is an ordinary link and keeps its
entry. If the browser has not left after about 5 seconds, the screen adds "Continue with Workeasy360" (the same
start URL) and "Go to the sign-in page", so a stalled navigation is not a dead end. There is no organization
hint.
Workeasy360 then completes the sign-in silently when it has a session and the person's preference is
Automatic (the default), asks first when it is "Ask me each time", and shows its central sign-in when it has
no session; the callback lands on `next`. `/` asks for `next=/` (then the home route, the dashboard); a deep
route such as `https://ops.workeasy360.com/#/projects/6f1b2c3d-.../tasks?status=OPEN` comes back to exactly
that route. The launcher's `?organization=<uuid>` is handled by the server before any of this, unchanged.

**Never by itself.** The app shows the sign-in page (with "Continue with Workeasy360") instead of starting when:

| Case | How it is detected |
|---|---|
| The sign-in route itself, whatever its query, including `?sso_error=` | `/sign-in` is outside the guard; `SignInPage` never starts anything. |
| The person signed out of Easy Project in this browser and has not signed in since | **The signed-out marker**: a session cookie. Set on the response to `POST /api/auth/logout` (so by both "Sign out of Easy Project" and the first half of "Sign out of Easy Project and Workeasy360"), cleared by a successful sign-in, never by a failed one. |
| This browser already started automatically less than 60 seconds ago and still has no session | **The loop guard**: the time of the last automatic start, in `sessionStorage` (`ep.autoSignInStartedAt`, per tab; a script-set session cookie `ep_auto_start_at` when storage is unavailable). Forgotten when a session read finds a session. |
| The session check itself failed (server unreachable, an error) | That is "could not tell", not "no session"; the sign-in page shows the reason. |

**The marker.** Name `__Host-ep_signed_out` over https and `ep_signed_out` over plain http (the `local` profile),
the same rule as the other cookies; value `1`; path `/`; `SameSite=Lax`; `Secure` over https; **no `Max-Age`**
(it ends with the browser session and is shared by every tab); **not `HttpOnly`**, because the app reads it. It
holds nothing and grants nothing: a planted marker can only make the app show its sign-in page instead of
starting a sign-in, and it is a user-experience guard, not an access control. Over https the `__Host-` prefix
stops a sibling `*.workeasy360.com` site from planting it, and **the app honours only the `__Host-` name on an
https page**: an unprefixed `ep_signed_out` planted with `Domain=workeasy360.com` would never be cleared by a
sign-in here, so it is ignored (over plain http, the `local` profile, both names are read). Code: `SignedOutMarker` (server), `signedOutMarkerPresent` in
`frontend/src/api/autoSignIn.ts` (app). **This matters for "Sign out of Easy Project and Workeasy360":**
Easy Project's registered post-logout address is its root `https://ops.workeasy360.com/`. After the person
confirms at Workeasy360 the browser returns to `/`, finds the marker, and shows Easy Project's sign-in page,
instead of bouncing straight back into a sign-in at Workeasy360 (which would be a silent sign-in right after a
sign-out, or Workeasy360's sign-in page for a person who just left it).

| After... | What `/` shows |
|---|---|
| Sign out of Easy Project (product only) | The sign-in page. Workeasy360's session is untouched; one click on "Continue with Workeasy360" signs back in. |
| Sign out of Easy Project and Workeasy360 | The sign-in page (the marker is already set when Workeasy360 sends the browser back to `/`). |
| The session expiring or the server restarting (no sign-out) | A sign-in starts by itself, for the route the person was on. |
| A sign-in (including from the launcher) | The marker is gone, so the next visit with no session starts automatically again. |

**Loop guard.** An automatic start writes the time before navigating. If the browser comes back with still no
session (a dropped cookie, a failure that was not reported as `sso_error`) within 60 seconds, the app shows the
sign-in page instead of starting again.

**Sign-out in progress.** The server ends the session before the 204 that carries the marker reaches the
browser, so a call already in flight can get its 401 first. The app therefore notes "signing out" (in memory,
for the life of the page) **before** it sends `POST /api/auth/logout`, and the route guard treats that as the
marker; if the logout request fails, the note is dropped (the person is still signed in). The server-set cookie
stays the durable marker.

**A session whose access ended keeps its explanation.** Automatic start happens only for "no session at all":
a session that exists but whose access ended (`workeasy360_access_ended`, `workeasy360_access_unverified`) keeps
the "Easy Project access has ended" screen; nothing replaces a session silently.

**API calls are never redirected.** `/api/**` answers 401 JSON as before; the app's API client never
navigates. Only the page's route guard starts sign-in. A 401 in the middle of a visit (an idle timeout, a
restart) leaves the session and starts a sign-in for the page the person is on.

## Who is who: the provisioning rules

| Question | Rule |
|---|---|
| What identifies a person? | The pair (issuer, `sub`). **Never the e-mail address.** E-mail and name are profile data, refreshed at each sign-in, shown in Settings, and never used to find or link anyone. |
| What is the internal user id (`ExternalUserId`) of a new (issuer, `sub`)? | `UUID(sub)` when `sub` is a UUID that is not already bound to **any** identity (of any issuer: the same `sub` under another issuer is another person). Otherwise a new random UUID. Workeasy360's `sub` is its own user id - exactly what `X-User-Id` was always meant to carry - so every row written before sign-in existed keeps its owner. |
| Does the same person always get the same user id? | Yes, for as long as the `federated_identities` row exists (`V14` migration; unique on (issuer, subject) and, globally, on user id). |
| What is the organization? | The token's `organization_id` (Workeasy360's organization id is already the external organization id the schema stores). |
| Are users or organizations created in a table here? | No. There is still no users or organizations table: only the mapping above. |
| Who may do what inside a project? | Unchanged: project membership roles (`OWNER`, `MANAGER`, `MEMBER`, `VIEWER`). Signing in grants no project access. |
| Two sign-ins at once for a new person? | The unique constraint lets one win; the other reads its row. |

## Registering Easy Project in Workeasy360

A platform operator does this once per environment (see the Workeasy360 product integration guide, section
2). Easy Project is registered as:

| | |
|---|---|
| Product code | `easy_project` (slug `easy-project`) |
| Launch URL | `https://ops.workeasy360.com/` |
| Redirect URI (exact, the only one) | `https://ops.workeasy360.com/api/auth/callback/workeasy360` |
| Post-logout URI | `https://ops.workeasy360.com/` |
| Back-channel logout URI | `https://ops.workeasy360.com/api/auth/workeasy360/backchannel-logout` (manifest `2026-11-15`; set in the Super Admin console, see [Rollout](#rollout)) |
| Sign-in mode | `sso_only` |
| Scopes | `openid email profile organization` |
| Client authentication | `private_key_jwt` |

1. Make a key pair for this environment. RSA (2048 bits or more) and EC P-256 are supported:

   ```bash
   openssl genpkey -algorithm EC -pkeyopt ec_paramgen_curve:P-256 -out easy-project.pem
   openssl pkey -in easy-project.pem -pubout -out easy-project.pub.pem
   # or: openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:3072 -out easy-project.pem
   ```

   The private key (`easy-project.pem`) stays on the Easy Project host. Only the public key goes to
   Workeasy360. Both PKCS#8 (`BEGIN PRIVATE KEY`) and the traditional `BEGIN RSA PRIVATE KEY` /
   `BEGIN EC PRIVATE KEY` forms are read; encrypted keys are not.

2. In the Workeasy360 backend, as a staff member holding the integration grants:

   ```bash
   python manage.py connect_product_sso --actor <staff email> --product easy_project \
       --redirect-uri https://ops.workeasy360.com/api/auth/callback/workeasy360 \
       --public-key-file easy-project.pub.pem \
       --sign-in-mode sso_only \
       --post-logout-redirect-uri https://ops.workeasy360.com/ \
       --label "production 2026-10"
   ```

   It prints the four values to set (below): the issuer, the client id, the credential id (the `kid`) and the
   redirect URI. The same can be done in the Super Admin console (Products, then Authentication).

3. Set the environment (next section) and deploy. **Rotation:** run the command again with a new public key
   (it adds a credential and keeps the old one), deploy the new private key and key id, then revoke the old
   credential in Workeasy360.

## Configuration

All settings are environment variables.

| Variable | Required | Purpose |
|---|---|---|
| `WORKEASY360_ISSUER` | yes | Exact issuer identifier, `https://accounts.workeasy360.com` (WorkEasy360 Accounts, platform ADR-067; never `app.`, which serves no discovery document). Nothing else about Workeasy360 is configured: every endpoint comes from its discovery document. |
| `WORKEASY360_CLIENT_ID` | yes | The client id `connect_product_sso` printed. |
| `WORKEASY360_REDIRECT_URI` | yes | `https://ops.workeasy360.com/api/auth/callback/workeasy360`. Must equal the registered URI exactly. |
| `WORKEASY360_KEY_ID` | yes | The credential id printed at registration (the `kid` of the client assertions). |
| `WORKEASY360_PRIVATE_KEY_FILE` | yes, or the next | Path of the PEM private key. Preferred: mount it as a secret file. |
| `WORKEASY360_PRIVATE_KEY` | alternative | The PEM itself; literal `\n` sequences are turned into newlines so it fits a one-line variable. |
| `WORKEASY360_PRODUCT_CODE` | no | The `product_code` the ID token must carry. Default `easy_project`. |
| `WORKEASY360_POST_LOGOUT_REDIRECT_URI` | no | `https://ops.workeasy360.com/`. When set, "Sign out of Easy Project and Workeasy360" is offered. Must be registered for the client. |
| `PROJECTMODULE_SESSION_IDLE_TIMEOUT` | no | Idle timeout of the session. Default `8h`. |
| `PROJECTMODULE_SESSION_COOKIE_SECURE` | no | `true` by default. The `local` profile sets it to `false` (plain http). |

**The application refuses to start** under any profile other than `local` or `test` when the issuer, client
id, redirect URI, key id or private key is missing, when the issuer or redirect URI is not an https URL, or
when the private key cannot be used. The message names the variables, never their values. Easy Project can
only be signed in to through Workeasy360, so a deployment without these would be one nobody can use.

Elastic Beanstalk note: the deploy workflow does not set these. Add them as environment properties (the
private key as a file on the instance or as `WORKEASY360_PRIVATE_KEY`) before deploying this version, or the
new version will not start. The application still boots, and keeps serving, when Workeasy360 itself is down:
discovery happens on first use, is cached for an hour, and a failure is the sign-in error `unavailable`.

The session cookie is `HttpOnly`, `SameSite=Lax` (it has to accompany the top-level redirect back from
Workeasy360) and `Secure`. TLS is terminated by Cloudflare / the platform's proxy; `server.forward-headers-strategy`
is `framework`, so the application sees the public scheme and host.

## Entitlement while a session lasts

Sign-in checks the person, the organization, the entitlement and the seat. A session then lives on (up to
the idle timeout), so for every `/api/v1/**` request Easy Project asks Workeasy360's entitlement endpoint
(`workeasy360_entitlement_endpoint` from discovery) about **(organization, person)**, with the person's
`sub` and a client assertion. It asks the **read** question (no `write` parameter), as Easy CRM and EasyBook
do: for a suspended or read-only organization Workeasy360 answers that with `allow` and
`write_allowed: false`, whereas it answers the *write* question with `deny` - which would lock the
organization out of reading its own projects. Writability is taken from `write_allowed`.

| Situation | What happens |
|---|---|
| `allow` | Trusted for `min(revalidate_after, 900 s)`, at least 30 s. Then asked again. |
| `deny` | Every request is refused with **403** `workeasy360_access_ended` and the provider's `reason` (`expired`, `not_entitled`, `suspended`, `membership_inactive`, `no_seat`...). Asked again after 60 s, so a renewal takes effect quickly. A known denial never lapses into access: it is kept without expiry until a new answer replaces it. |
| `allow`, not `write_allowed` | Reads work; every write (any method other than GET, HEAD, OPTIONS) is refused with **403** `workeasy360_read_only`. |
| Outage (5xx, 429, timeout, network, unreadable key, discovery without the endpoint) | Never a denial. The last answer stands; with none, access continues. The next attempt waits 60 s. It is logged as `workeasy360.entitlement_unavailable`. |
| Workeasy360 refuses this product's own client (400, 401, 403: a revoked credential, a suspended integration) | **Not** an outage, and **client-wide**: the whole client is marked refused for 60 s and *every* question (any person, any organization, even one with a fresh allowance) fails closed with **403** `workeasy360_access_unverified` without asking. After 60 s one question is asked again while everybody else stays closed; if it is refused again (or answered 429 - Workeasy360's failed-authentication limiter - within 5 minutes of a refusal) the minute starts over. Logged as an error (`workeasy360.entitlement_refused`). Fix the credential. |
| Many requests at once | One request asks; the others use the last answer. A first check is never waved through because another request is asking. |

The answers are held in memory, per process. Allowances and denials have separate bounds of 10,000 each, so a flood of one cannot push out the other; a denial that nobody has used for longer than the session idle timeout (8 h) is dropped, because the session it belonged to cannot exist any more. The
worst case after access ends is therefore one window - 15 minutes - for every cause: entitlement denial,
organization suspension, membership removal, a disabled account, a removed seat, an expired trial.

The 403 bodies are problem documents like every other error here, with extra `code` and `reason` fields:

```json
{"type":"https://errors.projectmodule/workeasy360-access-ended","title":"Access has ended","status":403,
 "detail":"Your access to Easy Project has ended. An owner can renew it in Workeasy360.",
 "traceId":"...","code":"workeasy360_access_ended","reason":"expired"}
```

The session itself stays valid, so the person sees one "Easy Project access has ended" screen and can sign
out or try again. The check does not apply to sign-in, sign-out, the session read (`/api/auth/**`), the
actuator health probe or the static application.

## Background work

There is none. A search of the code for scheduled tasks, asynchronous execution, executors, threads and
event listeners that run later finds nothing: no `@Scheduled`, `@Async`, `Executor`, `CompletableFuture`,
`TaskScheduler` or `@TransactionalEventListener`. Automation rules run **synchronously inside the request**
that triggers them (`AutomationDispatcher`, called from the application services), so they are covered by
that request's entitlement check: a denied or read-only organization cannot trigger an automation run, and
an organization whose access has ended cannot have a rule fire. This satisfies "class A" of the universal-SSO
design (section 5) by construction. The [back-channel logout](#back-channel-logout) endpoint is not background
work either: it runs inside Workeasy360's request, ends sessions and writes no organization's data, and its
clean-up of old `jti` records happens in that same request. **If background jobs are ever added**, they must re-check the
organization's entitlement (organization-level, `write=true`) when they run and skip the effect when it is
denied; `EntitlementService` is the place to extend.

## Sign-out

- **Sign out of Easy Project** - `POST /api/auth/logout` (CSRF-protected) ends the Easy Project session
  server-side and leaves the [signed-out marker](#opening-easy-project-directly-automatic-sign-in), so the next
  visit shows the sign-in page instead of signing the person straight back in. Workeasy360 is untouched.
- **Sign out of Easy Project and Workeasy360** - offered only when `WORKEASY360_POST_LOGOUT_REDIRECT_URI` is
  set. The app ends the session as above, then navigates to `GET /api/auth/workeasy360/logout`, which
  redirects to Workeasy360's `end_session_endpoint?client_id=...&post_logout_redirect_uri=...` (Workeasy360
  asks the person to confirm). That endpoint changes nothing in Easy Project - a link to it cannot sign anyone
  out - and without the setting, or without a published end-session endpoint, it redirects to `/`. The
  marker was left by the logout request, before the browser left, so the return to `/` shows the sign-in page.
- **Signed out at Workeasy360** - when the Workeasy360 sign-in that opened an Easy Project session ends
  (a Workeasy360 sign-out, an account switch, a revoked session, a password change or reset, "sign out
  everywhere"), Workeasy360 ends the Easy Project session too, within seconds, through
  [back-channel logout](#back-channel-logout), once its URI is registered. Without it, a Workeasy360 sign-out
  leaves the Easy Project session running, and access ending is handled by the entitlement check, within
  15 minutes. There is no front-channel logout.

## Back-channel logout

When a Workeasy360 sign-in that opened Easy Project sessions ends - the person signs out of Workeasy360,
switches account, has a session revoked, changes or resets their password, or uses "sign out everywhere" -
Workeasy360 tells Easy Project, server to server, and those Easy Project sessions end within seconds instead of
at the next entitlement re-check. This is OpenID Connect Back-Channel Logout 1.0 as the platform issues it
(Workeasy360 ADR-069; contract `docs/phase-3/04-oidc-contract.md` section 3.3 and `05-token-claims.md`, `sid`).
It starts working when the URI below is registered for Easy Project ([Rollout](#rollout)); until then
Workeasy360 sends nothing and its ID tokens carry no `sid`. The code is in `com.projectmodule.security.backchannel`.

**The endpoint.** `POST https://ops.workeasy360.com/api/auth/workeasy360/backchannel-logout`, with the form
field `logout_token` (`application/x-www-form-urlencoded`). It is on the same origin as the callback
`/api/auth/callback/workeasy360`, as the platform requires. It is for Workeasy360's server, never a browser, so
it has a security filter chain of its own: no session is read or created, no cookie is read or set (not even the
CSRF cookie), there is no CSRF token and no same-origin check. The logout token's signature is the
authentication. Every answer is `Cache-Control: no-store`.

| Answer | When | What Workeasy360 does |
|---|---|---|
| `200`, empty body | The token verified, and the sessions it names have ended (or had already ended). | Marks the delivery `delivered`. |
| `400 {"error":"invalid_request"}` | The token does not verify, or its `jti` was accepted before. Nothing was ended. | Marks it `failed`. No retry. |
| `503 {"error":"temporarily_unavailable"}` | Workeasy360's discovery document or key set could not be read; or recording the `jti` (the database) or ending a session failed. Nothing was recorded. | Retries with a fresh token: 0 s, 15 s, 1 min, 5 min, 15 min, 1 h, 6 h, 24 h. |
| `405` | Any method other than `POST`. | - |

**What is checked.** Each failure is a `400` unless the table above says otherwise. Each check closes a named
attack, as for the ID token:

1. The JOSE header `typ` is `logout+jwt` (or `application/logout+jwt`). An ID token or an access token, signed
   by the same key for the same audience, is never taken for a logout instruction.
2. The key is chosen by `kid` from Workeasy360's key set (the discovery document's `jwks_uri`, cached for an
   hour). A `kid` the cached set does not have makes Easy Project re-read the set **once**, which is how a key
   rotation is followed; if the key is still missing, the answer is `400`. Re-reads are at least 30 seconds
   apart, so tokens naming made-up keys cannot make Easy Project hammer Workeasy360. Inside those 30 seconds an
   unknown `kid` is answered `503`, so a genuine token signed with a brand-new key is retried, not lost.
3. The algorithm is **the key's**, never the token's: `RS256` for an RSA key of at least 2048 bits, `ES256` for
   a P-256 key. A key that publishes another `alg` is not used, and the header's `alg` must match. `none`,
   HMAC-with-the-public-key and every other algorithm are refused.
4. The signature.
5. `iss` equals `WORKEASY360_ISSUER` exactly.
6. `aud` contains `WORKEASY360_CLIENT_ID`. With several audiences, or whenever `azp` is present, `azp` is the
   client id.
7. `iat` is not more than 30 seconds in the future and not older than 10 minutes (Workeasy360's tokens live
   120 seconds and are minted fresh for every attempt). `exp` has not passed (30 seconds of leeway). `nbf`, if
   present, has come.
8. `events` is an object holding `http://schemas.openid.net/event/backchannel-logout`, itself an object.
9. There is **no** `nonce`. A `typ` claim, when present, is `logout_token`.
10. `sub` and/or `sid` are present (strings of at most 255 characters), and `jti` is present.
11. A `jti` is never accepted twice. Accepted ones are recorded in `received_logout_tokens` (migration `V15`)
    **in the same database transaction** that ends the sessions. If ending them fails, nothing is recorded and
    the answer is `503`, so the retry is not refused as a replay. A row is kept until its token could no longer
    pass the checks above (at most about 11 minutes), then deleted by the next accepted token. No background job
    is involved.

**What ends.**

- **A token with `sid`** (one sign-in ended: a sign-out, an account switch, a revoked session): the Easy Project
  sessions that Workeasy360 sign-in opened, and only that person's (Workeasy360 always sends `sub` too). The same
  person's sessions from other sign-ins, such as another browser, continue.
- **A token with only `sub`** ("sign out everywhere"): every Easy Project session of that person, matched on
  (issuer, `sub`), whichever sign-in opened it, including sessions whose ID token carried no `sid`.
- Ending is server-side: the session is invalidated, so its cookie opens nothing afterwards. The app's next API
  call gets a `401`, and the app starts a sign-in by itself for the page the person is on. Workeasy360 then shows
  its own sign-in page (the person signed out of it) or completes a sign-in as the account the person switched
  to. No [signed-out marker](#opening-easy-project-directly-automatic-sign-in) is set, because no browser takes part.
- Delivery is at least once. Ending a session that has already ended is not an error and still answers `200`.

**How sessions are recorded.** At sign-in, the new session is recorded in memory (`SignInSessions`) with the
person's issuer and `sub` and the ID token's `sid` (absent when the token carried none). The servlet container
reports every session that ends (sign-out, idle timeout, account switch, a back-channel logout), so the record
never outlives the session. A logout token can overtake the sign-in it ends: the browser may still be on its way
back to the callback with an ID token for that `sid`. Ended `sid`s are therefore remembered for 10 minutes, and
such a callback fails with `expired` and leaves no session.

**Sessions are in memory, so back-channel logout reaches one instance.** Easy Project's sessions are the
servlet container's own `HttpSession`s, held in memory by the instance that created them. There is no Spring
Session and no shared store. A logout token can therefore end only the sessions of the instance that receives
the `POST`. With one instance, as deployed, that is every session. With several instances behind a load
balancer, the token reaches whichever instance the balancer picks, which ends what it holds and answers `200`.
Sessions on the other instances are **not** ended; the entitlement re-check (15 minutes) and the idle timeout
remain their bound. Do not scale out without moving sessions, and this record, to a shared store such as Spring
Session JDBC or Redis. The `jti` record is already in PostgreSQL: it is shared by every instance and survives
restarts, so a captured token cannot be replayed against sessions opened after a deploy.

**Logs** carry codes and ids, never the token:

- `workeasy360.backchannel_logout jti=... subject=... sign_in=<sid, or all> sessions_ended=<n>` (200)
- `workeasy360.logout_token_rejected reason=...` (400). The reason is one of `wrong_issuer`, `wrong_audience`,
  `expired`, `stale`, `issued_in_future`, `bad_signature`, `unknown_key`, `algorithm_mismatch`, `nonce_present`,
  `missing_logout_event`, `not_a_logout_token`, `no_subject_or_session`, `missing_claim`, `malformed`,
  `replayed`, `not_configured`, ...
- `workeasy360.logout_token_unverifiable` (503: discovery or the key set)
- `workeasy360.backchannel_logout_failed` (503: the database or a session)

### Rollout

1. Deploy this version. It adds the migration `V15__create_received_logout_tokens.sql`, which Flyway applies at
   start-up. There is no new setting. The endpoint is live, but nothing calls it yet and ID tokens carry no `sid`.
2. Check the endpoint from outside: `curl -i -X POST https://ops.workeasy360.com/api/auth/workeasy360/backchannel-logout -d logout_token=x`
   must answer `400 {"error":"invalid_request"}` with `Cache-Control: no-store`. A `403`, a `404` or the app's
   HTML means something in front (Cloudflare, nginx) is blocking or rewriting the path. Exempt this path from
   any browser challenge: Workeasy360's server cannot solve one.
3. Check that Workeasy360 supports it: `https://accounts.workeasy360.com/.well-known/openid-configuration`
   advertises `"backchannel_logout_supported": true`.
4. Register the URI. In Super Admin, go to Products, then Easy Project, then Authentication, and set **Back-channel
   logout URL** to `https://ops.workeasy360.com/api/auth/workeasy360/backchannel-logout`. It must be https and
   share its origin with the registered redirect URI, which it does. The console stamps manifest version
   `2026-11-15`. From then on, Easy Project's ID tokens carry `sid`.
5. Sessions opened before step 4 have no `sid` on record. A token naming a `sid` cannot reach them, but a
   `sub`-only one ("sign out everywhere") can. They end at the idle timeout (8 hours), or at the next deploy.
6. Verify: sign in to Easy Project, then sign out of Workeasy360 in the same browser (or use "sign out
   everywhere"). Within seconds, Easy Project's next request is a `401` and the app starts a sign-in. Super
   Admin's back-channel logout card for Easy Project shows the delivery as `delivered`, and Easy Project's log
   shows `workeasy360.backchannel_logout ... sessions_ended=1`.

### Rollback

- **At the platform, with no deploy:** clear the Back-channel logout URL in Super Admin. Workeasy360 stops
  sending logout tokens and stops putting `sid` in new ID tokens. Easy Project is back to the entitlement re-check
  (up to 15 minutes) as the bound on a session after a Workeasy360 sign-out. The endpoint stays, and only answers
  whatever still reaches it.
- **The application:** clear the URL first, then redeploy the previous version. Without the endpoint, deliveries
  would meet a `403` from the browser chain and be marked `failed` in the console. The previous version starts
  with the `received_logout_tokens` table in place (Flyway ignores an applied migration newer than the ones it
  ships, and Hibernate validates only the tables it maps); the table can be dropped by hand.

## Account switching

Every sign-in creates a **new** session and invalidates the previous one in that browser first
(`sessionFixation().newSession()`; nothing is carried over). So if a different person signs in in a browser
that already has a session, the first person's session is dead server-side the moment the second finishes,
and a session id planted before sign-in is useless afterwards. A sign-in that *fails* leaves the current
session as it was.

## Sessions, deployment shape and edge protection

- **Sessions and pending sign-ins are in memory, per instance.** Run **one instance**, or put a load balancer
  with **sticky sessions** in front of several; a pending sign-in (state, nonce, PKCE verifier) lives in the
  browser's session, so a callback that reaches a different instance finds nothing and ends in `expired`.
  **A deploy or restart signs everyone out**: the next page load starts a sign-in by itself (a restart is
  not a sign-out, so there is no marker), and Workeasy360's own session, which is untouched, brings the person
  back to the page they were on without a password. Entitlement answers are in memory too and are simply asked
  again. **Back-channel logout reaches only the instance that receives Workeasy360's `POST`**, so with
  several instances it misses sessions held by the others (see [Back-channel logout](#back-channel-logout)).
- **Anonymous requests create no session.** A browser only gets a session cookie when it starts a sign-in, and
  that pre-sign-in session lives **10 minutes** (the life of the flow). The signed-in session is a new one
  with the 8 h idle timeout (`PROJECTMODULE_SESSION_IDLE_TIMEOUT`).
- **Cookies are `__Host-` prefixed over https** (`__Host-ep_session`, `__Host-XSRF-TOKEN`,
  `__Host-ep_signed_out`): `Secure`, path `/`, no `Domain`, so a sibling `*.workeasy360.com` site cannot plant
  or replace them. The `local` profile (plain http, where the prefix is not allowed) uses `ep_session`,
  `XSRF-TOKEN` and `ep_signed_out`. Do not set
  `PROJECTMODULE_SESSION_COOKIE_SECURE=false` in production: the browser would reject the prefixed cookie.
- **Writes must be same-origin.** A non-GET request whose `Origin` is present and is not this origin, or whose
  `Sec-Fetch-Site` is anything but `same-origin`/`none`, is refused (403 `origin_not_allowed`) before CSRF is
  checked.
- **Rate limiting belongs at the edge.** The application does not rate-limit `/api/auth/**`. Put a limit on
  `GET /api/auth/workeasy360/start` and `GET /api/auth/callback/workeasy360` (and `POST /api/auth/logout`) in
  Cloudflare or the proxy in front: each `start` creates a (short-lived, small) session and each callback does a
  token exchange against Workeasy360. A reasonable starting point is 30 requests a minute per client IP for the
  two sign-in paths.
- **Calls to Workeasy360 have an overall deadline** (`workeasy360.http-timeout`, 10 s) covering the whole
  response, never follow redirects, and never block each other: while one thread refreshes a stale discovery
  document, everyone else keeps using the previous one.
- `/v3/**` (API documents), Swagger UI and the actuator (except `/actuator/health`, which shows no details) need
  a session.

## Before go-live: data written under the old identity headers

Before sign-in existed, anybody could act as anybody: the browser's "Set identity" form sent whatever user and
organization ids it was given in `X-User-Id` / `X-Org-Id`. Every project, membership and ownership row created
that way is **trusted as-is** by the new code. Review them before opening the application to customers, and
reset what you do not recognise. Users that sign in later get `UUID(sub)` as their user id, so rows that name a
real Workeasy360 user id keep their owner - which is exactly why rows naming an id nobody should own must be
cleaned up. To list them:

```sql
-- Projects and who owns them (owner_id is a user id; organization_id is a Workeasy360 organization id).
SELECT p.id, p.organization_id, p.name, p.owner_id, p.created_at
FROM projects p
ORDER BY p.organization_id, p.created_at;

-- Everybody with a role on a project, OWNER and MANAGER first. user_id is the external user id.
SELECT m.project_id, p.organization_id, p.name, m.user_id, m.role, m.joined_at
FROM project_members m
JOIN projects p ON p.id = m.project_id
ORDER BY p.organization_id, p.name,
         CASE m.role WHEN 'OWNER' THEN 0 WHEN 'MANAGER' THEN 1 WHEN 'MEMBER' THEN 2 ELSE 3 END;

-- Members that are not (yet) a known Workeasy360 identity: nobody has ever signed in as these ids.
SELECT m.project_id, m.user_id, m.role
FROM project_members m
LEFT JOIN federated_identities f ON f.user_id = m.user_id
WHERE f.id IS NULL
ORDER BY m.role, m.project_id;
```

The same ids appear as authors and assignees in the other tables (tasks, comments, decisions, ...). A clean
start is an empty `projects` table (project data cascades from it); otherwise remove or re-assign the
ownership and OWNER/MANAGER grants you cannot attribute to a real person, in each organization, with the
organization's owner. Do this **before** pointing customers at the new version.

## Sign-in errors

A failed sign-in redirects to `/#/sign-in?sso_error=<code>` and creates no session. The code is one of:
`cancelled`, `expired`, `unavailable`, `refused`, `rate_limited`, `not_entitled`, `no_seat`,
`provider_membership_inactive`, `organization_inactive`, `access_policy`, `email_missing`,
`email_unverified`, `account_conflict`, `account_disabled`, `membership_inactive`, `wrong_product`,
`wrong_organization` (the shared vocabulary of Easy CRM, EasyBook and Easy Project).

| Cause | Code |
|---|---|
| Provider `error=access_denied` | `cancelled` |
| `login_required`, `organization_required`, `interaction_required`; an unknown, reused or timed-out state; a callback with no state | `expired` |
| `product_not_entitled` / `product_no_seat` | `not_entitled` / `no_seat` |
| `membership_inactive` / `organization_inactive` / `access_policy_denied` | `provider_membership_inactive` / `organization_inactive` / `access_policy` |
| `integration_suspended`, `unauthorized_client`, `invalid_scope`, `server_error`, `temporarily_unavailable`; Workeasy360 unreachable or describing itself oddly (discovery, key set, token endpoint 5xx); a credential that cannot be used | `unavailable` |
| Token endpoint `invalid_grant` (expired or reused code, PKCE mismatch); any invalid ID token (signature, audience, issuer, expiry, nonce, type, missing claims); any other provider error | `refused` |
| ID token for another product | `wrong_product` |
| Launched for organization A, token for organization B | `wrong_organization` |

Logs carry the code and ids (`workeasy360.sign_in_failed code=...`, `workeasy360.sign_in user=... organization=...`),
never an authorization code, token, state or nonce.

## Running locally

Start with the `local` profile; it may run without the `WORKEASY360_*` settings (sign-in then answers
`unavailable`). To sign in for real, register a local client in a Workeasy360 development environment with
the redirect URI `http://localhost:5173/api/auth/callback/workeasy360` (Workeasy360 accepts `http://localhost`
redirect URIs outside production), set the variables above with that redirect URI, and run
`mvnw spring-boot:run` and `npm run dev`. The Vite dev server proxies `/api` to the backend, so the browser
stays on one origin (`localhost:5173`) and the cookies and redirects work. The `local` profile turns the
`Secure` cookie flag off; it does not turn anything else off, and there is no way to sign in without
Workeasy360.

## Security properties

- The security chain is Spring Security's: `/api/v1/**` and every other path under `/api` except the sign-in
  endpoints needs a session (401 problem document otherwise); the static app, `/api/auth/**` sign-in
  endpoints and `/actuator/health` are public; API documentation and the other actuator endpoints need a
  session.
- `POST /api/auth/workeasy360/backchannel-logout` is public too, and has a filter chain of its own ahead of
  the browser chain: no session, no cookie, no CSRF token. A request there can do nothing without a logout
  token signed by Workeasy360's key for this client (see [Back-channel logout](#back-channel-logout)).
- CSRF is on: the `__Host-XSRF-TOKEN` cookie (`XSRF-TOKEN` under the `local` profile; readable by the app, `SameSite=Lax`, `Secure`) is echoed in the
  `X-XSRF-TOKEN` header on every POST, PATCH and DELETE. The OAuth redirects are GETs and are not writes.
- API and authentication responses are `Cache-Control: no-store`. `X-Content-Type-Options`, `X-Frame-Options:
  DENY` and `Referrer-Policy: same-origin` are set.
- CORS: credentials are off and the identity headers are not allowed; the app and API are one origin.
- The private key is never logged and never appears in an error message.

## What this does not do

- No password, no local accounts, no "dev headers" mode, no fallback of any kind.
- No front-channel logout; no refresh tokens; no access token is kept. Back-channel logout reaches only the
  instance that receives it (sessions are in memory).
- Two small divergences from the reference clients, deliberate: the ID token's nonce is compared by Spring
  Security (the provider echoes a hash of it, compared with `equals`, not in constant time; the nonce is not a
  secret an attacker can probe, as it only protects against a token replayed from another session), and the
  ID token must carry a `kid` header (as EasyBook requires).
- No "Confirm it's you" re-authentication (`prompt=login`) yet - nothing in Easy Project needs it.
- No rate limiting of the sign-in endpoints in the application (the proxy in front is the place for it); the
  `rate_limited` code is part of the shared vocabulary (and has a message in the app) but nothing in Easy
  Project produces it.
- Sign-in on a partner's custom domain (D-035) is not addressed here.
- The entitlement cache is per process: with several instances each asks Workeasy360 for itself.
