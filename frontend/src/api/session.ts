import { API_BASE_URL, authRequest, NetworkError } from './client'
import { getSessionState, setAuthenticated, setUnauthenticated, type SessionPayload } from '../context/sessionStore'

/** Hash-route paths the sign-in flow may return to. Anything else falls back to `/`. */
export function safeNextRoute(candidate: string | null | undefined): string {
  if (!candidate || !candidate.startsWith('/') || candidate.startsWith('//')) return '/'
  if (candidate === '/sign-in' || candidate.startsWith('/sign-in?') || candidate.startsWith('/sign-in/')) return '/'
  return candidate
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
