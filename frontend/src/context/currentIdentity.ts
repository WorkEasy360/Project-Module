import { authApi } from '../api/auth'
import { abortSigningOut, beginSigningOut } from '../api/autoSignIn'
import { logoutRequest } from '../api/session'
import { type Identity, getIdentity, setIdentity, subscribeIdentity } from './identityStore'
import { getSessionState, setUnauthenticated, subscribeSession, type SessionState } from './sessionStore'

/**
 * Who is signed in, whichever way they signed in, as one external store (read by `useIdentity()`):
 *
 * - **an email account** (email and password, Google, an invitation, the hand-over): the identity
 *   `identityStore` keeps, with its session token (`provider: 'password'`);
 * - **Workeasy360**: the signed-in session `sessionStore` describes (`provider: 'workeasy360'`). The
 *   HttpOnly cookie is its credential, so this identity has no token and is never stored.
 *
 * An email session wins when both exist: it is the one the API client sends.
 */

let lastStored: Identity | null = null
let lastSession: SessionState | null = null
let lastMerged: Identity | null = null

function workeasy360Identity(session: SessionState): Identity | null {
  if (session.status !== 'authenticated' || !session.user || !session.organization) return null
  return {
    userId: session.user.id,
    orgId: session.organization.id,
    email: session.user.email ?? undefined,
    name: session.user.name ?? undefined,
    provider: 'workeasy360',
  }
}

function sameIdentity(a: Identity | null, b: Identity | null): boolean {
  if (a === null || b === null) return a === b
  return (
    a.userId === b.userId &&
    a.orgId === b.orgId &&
    a.token === b.token &&
    a.email === b.email &&
    a.name === b.name &&
    a.provider === b.provider
  )
}

/** The current identity: the same object until either store changes it, as `useSyncExternalStore` requires. */
export function getCurrentIdentity(): Identity | null {
  const stored = getIdentity()
  const session = getSessionState()
  if (stored !== lastStored || session !== lastSession) {
    lastStored = stored
    lastSession = session
    const next: Identity | null = stored ? { ...stored, provider: 'password' } : workeasy360Identity(session)
    // A session check republishes the same person: keep the object, so effects keyed on it do not rerun.
    if (!sameIdentity(next, lastMerged)) lastMerged = next
  }
  return lastMerged
}

export function subscribeCurrentIdentity(listener: () => void): () => void {
  const offIdentity = subscribeIdentity(listener)
  const offSession = subscribeSession(listener)
  return () => {
    offIdentity()
    offSession()
  }
}

/**
 * Ends whichever session is signed in. An email session is revoked on the server (best effort) and
 * forgotten here, so signing out locally works even when the server cannot be reached. A Workeasy360
 * sign-in to Easy Project is ended with `POST /api/auth/logout`, as the account menu does (which also
 * leaves the signed-out marker, so the app does not sign the person straight back in).
 */
export async function signOutCurrent(): Promise<void> {
  if (getIdentity()?.token) {
    // The request captures the token before the identity is cleared.
    authApi.logout().catch(() => undefined)
    setIdentity(null)
    return
  }
  if (getSessionState().status === 'authenticated') {
    beginSigningOut()
    try {
      await logoutRequest()
    } catch (error) {
      abortSigningOut()
      throw error
    }
    setUnauthenticated()
  }
}
