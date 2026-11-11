/**
 * When may Easy Project start "Sign in with Workeasy360" by itself?
 *
 * Easy Project is SSO-only. A person who is already signed in to Workeasy360 and opens Easy Project
 * directly must arrive signed in, without Easy Project's sign-in page and without a password
 * (Workeasy360 integration guide section 6b). So a page that needs a person, opened in a browser with no
 * Easy Project session at all, goes straight to `/api/auth/workeasy360/start?next=<route>`.
 *
 * It must never do that:
 *
 *  - on the sign-in page itself, whatever its query (`SignInPage` does not use this module);
 *  - after the person signed out of Easy Project in this browser (the signed-out marker, below), nor while
 *    a sign-out is in progress (see `beginSigningOut`);
 *  - when this browser already started automatically less than 60 seconds ago and still has no session
 *    (the loop guard, below);
 *  - when the session check itself failed: that is "could not tell", not "no session";
 *  - while a session exists (an ended access keeps its own screen; `AppLayout` never gets here then).
 *
 * Nothing in this module is a credential or a permission. A planted marker or guard can only make the app
 * show its sign-in page instead of starting a sign-in.
 */

/**
 * The server leaves this session cookie when `POST /api/auth/logout` is accepted, and clears it at sign-in.
 * Over https it is `__Host-ep_signed_out`, which a sibling subdomain cannot plant (the browser refuses a
 * `__Host-` cookie with a `Domain`); over plain http (the `local` profile) it is `ep_signed_out`.
 */
export const SIGNED_OUT_COOKIE_SECURE = '__Host-ep_signed_out'
export const SIGNED_OUT_COOKIE_PLAIN = 'ep_signed_out'

/**
 * The marker names this page honours. **Over https only the `__Host-` name**: an unprefixed cookie could be
 * planted by a sibling `*.workeasy360.com` site with `Domain=workeasy360.com`, and no sign-in would clear it
 * (the server clears only the host-only cookie). Over http both are read, because Chrome stores a `Secure`
 * `__Host-` cookie from a loopback address over http, and the plain name is what the local profile writes.
 */
export function signedOutCookieNames(protocol: string): readonly string[] {
  return protocol === 'https:' ? [SIGNED_OUT_COOKIE_SECURE] : [SIGNED_OUT_COOKIE_SECURE, SIGNED_OUT_COOKIE_PLAIN]
}

/** A second automatic start within this time of the first, with still no session, is a loop. */
export const AUTO_START_GUARD_MS = 60_000

const GUARD_KEY = 'ep.autoSignInStartedAt'
/** Fallback when `sessionStorage` is unavailable (a blocked or private context): a script-set session cookie. */
const GUARD_COOKIE = 'ep_auto_start_at'

export type AutoStartDecision = 'start' | 'signed-out' | 'recent-start' | 'session-unknown'

function readCookie(names: readonly string[]): string | null {
  if (typeof document === 'undefined') return null
  for (const part of document.cookie.split(';')) {
    const eq = part.indexOf('=')
    if (eq < 0) continue
    if (!names.includes(part.slice(0, eq).trim())) continue
    return part.slice(eq + 1).trim()
  }
  return null
}

/** True when this browser signed out of Easy Project and has not signed in again since. */
export function signedOutMarkerPresent(protocol: string = window.location.protocol): boolean {
  // Presence is what counts: the server writes `1`, and a planted value only suppresses auto-start.
  return readCookie(signedOutCookieNames(protocol)) !== null
}

let signingOut = false

/**
 * A sign-out is under way in this page. Call it **before** sending `POST /api/auth/logout`.
 *
 * The server ends the session before the 204 that carries the marker cookie reaches the browser, so a
 * request that was already in flight can get its 401 first and put the app in the signed-out state while the
 * marker is not there yet. Without this, the route guard would read "no session, no marker" and start a
 * sign-in; Workeasy360 is still signed in, so the person would be signed straight back in, and that sign-in
 * clears the marker. The server-set cookie stays the durable marker; this only covers the gap until it
 * arrives, and it lives for the life of the page (a sign-in always leaves the page).
 */
export function beginSigningOut(): void {
  signingOut = true
}

/** The logout did not happen (the request failed): the person is still signed in, so nothing is suppressed. */
export function abortSigningOut(): void {
  signingOut = false
}

export function isSigningOut(): boolean {
  return signingOut
}

function readGuard(): number | null {
  const candidates: Array<string | null> = []
  try {
    candidates.push(window.sessionStorage.getItem(GUARD_KEY))
  } catch {
    // Storage can be unavailable (blocked, private mode); the cookie below covers it.
  }
  candidates.push(readCookie([GUARD_COOKIE]))
  let latest: number | null = null
  for (const raw of candidates) {
    const value = raw === null ? Number.NaN : Number(raw)
    if (Number.isFinite(value) && (latest === null || value > latest)) latest = value
  }
  return latest
}

/** True when this browser started a sign-in automatically less than `AUTO_START_GUARD_MS` ago. */
export function autoStartedRecently(now: number = Date.now()): boolean {
  const startedAt = readGuard()
  return startedAt !== null && Math.abs(now - startedAt) < AUTO_START_GUARD_MS
}

/** Remembers that an automatic start is happening now. */
export function recordAutoStart(now: number = Date.now()): void {
  try {
    window.sessionStorage.setItem(GUARD_KEY, String(now))
    return
  } catch {
    // Storage unavailable (blocked, private mode): fall back to a session cookie.
  }
  try {
    document.cookie = `${GUARD_COOKIE}=${now}; path=/; SameSite=Lax${window.location.protocol === 'https:' ? '; Secure' : ''}`
  } catch {
    // Cookies unavailable too: the guard cannot be kept, and the sign-in still works.
  }
}

/** Forgets the guard: the person has a session now, so the premise of the guard ("still no session") is gone. */
export function clearAutoStartRecord(): void {
  try {
    window.sessionStorage.removeItem(GUARD_KEY)
  } catch {
    // see readGuard
  }
  try {
    document.cookie = `${GUARD_COOKIE}=; expires=Thu, 01 Jan 1970 00:00:00 GMT; path=/`
  } catch {
    // nothing to clear
  }
}

/**
 * Decides, for a browser the session check found to have no session, whether to start Workeasy360
 * sign-in now. `sessionCheckFailed` is the store's `loadError`: the server could not be asked, which says
 * nothing about whether a session exists.
 */
export function decideAutoStart(sessionCheckFailed: boolean, now: number = Date.now()): AutoStartDecision {
  if (sessionCheckFailed) return 'session-unknown'
  if (signingOut || signedOutMarkerPresent()) return 'signed-out'
  if (autoStartedRecently(now)) return 'recent-start'
  return 'start'
}
