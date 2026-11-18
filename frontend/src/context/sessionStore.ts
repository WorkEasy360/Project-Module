/**
 * Session state for "Sign in with Workeasy360".
 *
 * The browser holds an HttpOnly session cookie issued by the backend; this module only keeps the
 * *description* of that session (who is signed in, whether access has ended) so React and the API
 * client can agree on it. It is deliberately a tiny external store with no imports from the API
 * layer, so `api/client.ts` can report 401 / 403 events into it without a circular dependency.
 * Nothing here is persisted: the cookie is the only credential, and the server is the authority.
 */

export type SessionStatus = 'loading' | 'authenticated' | 'unauthenticated'

export interface SessionUser {
  id: string
  email: string | null
  name: string | null
}

/** A `workeasy360_*` 403 from the API: the organization's access to Easy Project is limited or gone. */
export interface AccessProblem {
  /** `workeasy360_access_ended`, `workeasy360_read_only` or `workeasy360_access_unverified`. */
  code: string
  /** Why, when the server says (expired, not_entitled, suspended, membership_inactive, no_seat). */
  reason: string | null
  message: string
}

export interface SessionState {
  status: SessionStatus
  user: SessionUser | null
  organization: { id: string } | null
  /** True when signing out may also end the Workeasy360 session (offered as a second option). */
  workeasy360SignOut: boolean
  accessProblem: AccessProblem | null
  /** Set when the session check itself could not reach the server (shown on the sign-in page). */
  loadError: string | null
}

export interface SessionPayload {
  user: SessionUser
  organization: { id: string }
  workeasy360SignOut: boolean
}

const INITIAL: SessionState = {
  status: 'loading',
  user: null,
  organization: null,
  workeasy360SignOut: false,
  accessProblem: null,
  loadError: null,
}

let current: SessionState = INITIAL
const listeners = new Set<() => void>()

function publish(next: SessionState): void {
  current = next
  for (const listener of listeners) listener()
}

export function getSessionState(): SessionState {
  return current
}

export function subscribeSession(listener: () => void): () => void {
  listeners.add(listener)
  return () => listeners.delete(listener)
}

export function setAuthenticated(payload: SessionPayload): void {
  publish({
    status: 'authenticated',
    user: payload.user,
    organization: payload.organization,
    workeasy360SignOut: payload.workeasy360SignOut,
    // A fresh, successful session check supersedes an earlier access problem only when the caller
    // clears it explicitly (see clearAccessProblem) — keep it so the screen does not flicker away.
    accessProblem: current.accessProblem,
    loadError: null,
  })
}

export function setUnauthenticated(loadError: string | null = null): void {
  publish({ ...INITIAL, status: 'unauthenticated', loadError })
}

/** Back to the unresolved state (used by tests to reset the store). */
export function resetSession(): void {
  publish(INITIAL)
}

export function setAccessProblem(problem: AccessProblem): void {
  // A read-only notice must never hide the more serious "access ended" screen.
  const existing = current.accessProblem
  if (problem.code === 'workeasy360_read_only' && existing && existing.code !== 'workeasy360_read_only') return
  publish({ ...current, accessProblem: problem })
}

export function clearAccessProblem(): void {
  if (current.accessProblem === null) return
  publish({ ...current, accessProblem: null })
}

/** Display name for greetings and the account menu: name, else the part of the email before @, else a short id. */
export function displayName(user: SessionUser | null): string | null {
  if (!user) return null
  if (user.name?.trim()) return user.name.trim()
  if (user.email?.includes('@')) return user.email.split('@')[0]
  return user.id.slice(0, 8)
}
