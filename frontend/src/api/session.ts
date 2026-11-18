import { API_BASE_URL, authRequest, NetworkError } from './client'
import { getSessionState, setAuthenticated, setUnauthenticated, type SessionPayload } from '../context/sessionStore'
import { clearAutoStartRecord } from './autoSignIn'

const MAX_NEXT_LENGTH = 2048
// Percent-encoded slash, backslash or control character: refused outright, as the server does.
const ENCODED_DANGER = /%(2f|5c|[01][0-9a-f]|7f)/i

/**
 * Hash-route paths the sign-in flow may return to. Anything else falls back to `/`.
 *
 * This mirrors the server's `NextPath.sanitize` on purpose: a hostile value is never forwarded at all,
 * rather than relying on the server to refuse it. A route (written as a path, `/projects/1`) starts with a
 * single `/`, has no backslash, `#`, whitespace or control character, no encoded slash, backslash or
 * control character, is at most 2,048 characters, and is neither an authentication page nor an API path.
 */
export function safeNextRoute(candidate: string | null | undefined): string {
  if (!candidate || candidate.length > MAX_NEXT_LENGTH) return '/'
  const route = candidate.startsWith('/#/') ? candidate.slice(2) : candidate
  if (!route.startsWith('/') || route.startsWith('//')) return '/'
  for (const character of route) {
    const code = character.codePointAt(0) ?? 0
    // control characters and the C1 range, backslash (0x5c) and '#', and any kind of space
    if (code <= 0x20 || (code >= 0x7f && code <= 0x9f) || code === 0x5c || code === 0x23 || /\s/.test(character)) {
      return '/'
    }
  }
  if (ENCODED_DANGER.test(route)) return '/'
  const path = route.split('?')[0].toLowerCase()
  if (path === '/api' || path.startsWith('/api/') || path === '/sign-in' || path.startsWith('/sign-in/')) return '/'
  return route
}

/** Top-level navigation target that starts "Sign in with Workeasy360" (never fetch this). */
export function workeasy360StartUrl(next: string | null | undefined): string {
  return `${API_BASE_URL}/api/auth/workeasy360/start?next=${encodeURIComponent(safeNextRoute(next))}`
}

/** Top-level navigation target that sends the browser to Workeasy360's own sign-out page. */
export function workeasy360LogoutUrl(): string {
  return `${API_BASE_URL}/api/auth/workeasy360/logout`
}

/** Top-level navigation (isolated so tests can observe it; jsdom cannot navigate). */
export function navigateTo(url: string): void {
  window.location.assign(url)
}

/**
 * Top-level navigation that replaces the current history entry (isolated like `navigateTo`). Used for the
 * automatic start only: the Easy Project page that decided to start is not a place to come back to, so Back
 * from Workeasy360's page must not land on it and bounce forward again. The explicit "Continue with
 * Workeasy360" is an ordinary link and keeps its history entry.
 */
export function replaceTo(url: string): void {
  window.location.replace(url)
}

interface SessionResponse {
  authenticated?: boolean
  user?: { id: string; email?: string | null; name?: string | null }
  organization?: { id: string }
  workeasy360SignOut?: boolean
}

/**
 * Asks the backend who is signed in and publishes the answer to the session store.
 * 200 means a session, 401 means none; a network failure is reported as "not signed in" with a
 * message, rather than leaving the app on a spinner forever.
 */
export async function loadSession(): Promise<void> {
  try {
    const { status, data } = await authRequest<SessionResponse>('/api/auth/session', 'GET')
    if (status === 401 || !data || data.authenticated === false || !data.user || !data.organization) {
      setUnauthenticated()
      return
    }
    const payload: SessionPayload = {
      user: { id: data.user.id, email: data.user.email ?? null, name: data.user.name ?? null },
      organization: { id: data.organization.id },
      workeasy360SignOut: data.workeasy360SignOut === true,
    }
    // There is a session: "started automatically a moment ago and still no session" no longer holds.
    clearAutoStartRecord()
    setAuthenticated(payload)
  } catch (error) {
    // A transient failure must not sign out someone who is already in; just leave the state alone.
    if (getSessionState().status === 'authenticated') return
    setUnauthenticated(
      error instanceof NetworkError
        ? 'Could not reach the server. Check your connection and try again.'
        : 'Easy Project could not check your sign-in right now. Try again shortly.',
    )
  }
}

/** Ends only the Easy Project session. A 401 means it was already gone, which is the goal anyway. */
export async function logoutRequest(): Promise<void> {
  await authRequest<unknown>('/api/auth/logout', 'POST')
}
