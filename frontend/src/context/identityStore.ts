/**
 * Who is signed in on this browser. Signing in (see pages/auth/SignInPage.tsx) yields a session
 * token, which the API client sends on every request; the backend works out the user and
 * organization from that token and remains the sole authority on what they may do.
 *
 * "Remember me" decides where the session lives: localStorage (survives closing the browser) or
 * sessionStorage (this tab only).
 */

export interface Identity {
  userId: string
  orgId: string
  token?: string
  email?: string
  name?: string
}

const STORAGE_KEY = 'projectmodule.identity'

function parse(raw: string | null): Identity | null {
  if (!raw) return null
  try {
    const parsed = JSON.parse(raw) as Partial<Identity>
    // An entry with no token predates email sign-in and can no longer authenticate anything.
    if (typeof parsed.userId === 'string' && typeof parsed.orgId === 'string' && typeof parsed.token === 'string') {
      return {
        userId: parsed.userId,
        orgId: parsed.orgId,
        token: parsed.token,
        email: parsed.email,
        name: parsed.name,
      }
    }
  } catch {
    // fall through
  }
  return null
}

function readStored(): Identity | null {
  try {
    return parse(window.localStorage.getItem(STORAGE_KEY)) ?? parse(window.sessionStorage.getItem(STORAGE_KEY))
  } catch {
    return null
  }
}

let current: Identity | null = readStored()

/**
 * The user and organization ids an earlier version of the app kept in this browser, before email
 * sign-in. They no longer sign anyone in; the sign-in page offers to move them onto an email and
 * password so the person keeps all their work.
 */
export function getEarlierSignIn(): { userId: string; orgId: string } | null {
  try {
    for (const raw of [window.localStorage.getItem(STORAGE_KEY), window.sessionStorage.getItem(STORAGE_KEY)]) {
      if (!raw) continue
      const parsed = JSON.parse(raw) as Partial<Identity>
      if (typeof parsed.userId === 'string' && typeof parsed.orgId === 'string' && typeof parsed.token !== 'string') {
        return { userId: parsed.userId, orgId: parsed.orgId }
      }
    }
  } catch {
    // Nothing usable stored.
  }
  return null
}

export function forgetEarlierSignIn(): void {
  try {
    window.localStorage.removeItem(STORAGE_KEY)
    window.sessionStorage.removeItem(STORAGE_KEY)
  } catch {
    // Storage unavailable: nothing to forget.
  }
}
const listeners = new Set<() => void>()

export function getIdentity(): Identity | null {
  return current
}

export function setIdentity(identity: Identity | null, remember = true): void {
  current = identity
  try {
    window.localStorage.removeItem(STORAGE_KEY)
    window.sessionStorage.removeItem(STORAGE_KEY)
    if (identity) {
      const store = remember ? window.localStorage : window.sessionStorage
      store.setItem(STORAGE_KEY, JSON.stringify(identity))
    }
  } catch {
    // Storage may be unavailable (private browsing, etc.) — identity still works in-memory.
  }
  for (const listener of listeners) listener()
}

export function subscribeIdentity(listener: () => void): () => void {
  listeners.add(listener)
  return () => listeners.delete(listener)
}
