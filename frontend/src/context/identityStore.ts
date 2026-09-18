/**
 * This backend has no login system of its own — it trusts X-User-Id / X-Org-Id headers that a
 * real deployment's upstream gateway would inject (see docs/project/*-SPEC.md "RequestContext").
 * In this standalone frontend there is no gateway, so the app asks for those two identifiers
 * once and resends them on every request — it is a dev/operator identity switcher, not an
 * authentication system. Nothing here grants or checks permissions; the backend remains the
 * sole authority for authorization, exactly as it is for a real gateway-fronted deployment.
 */

export interface Identity {
  userId: string
  orgId: string
}

const STORAGE_KEY = 'projectmodule.identity'

function readStored(): Identity | null {
  try {
    const raw = window.localStorage.getItem(STORAGE_KEY)
    if (!raw) return null
    const parsed = JSON.parse(raw) as Partial<Identity>
    if (typeof parsed.userId === 'string' && typeof parsed.orgId === 'string') {
      return { userId: parsed.userId, orgId: parsed.orgId }
    }
    return null
  } catch {
    return null
  }
}

let current: Identity | null = readStored()
const listeners = new Set<() => void>()

export function getIdentity(): Identity | null {
  return current
}

export function setIdentity(identity: Identity | null): void {
  current = identity
  try {
    if (identity) {
      window.localStorage.setItem(STORAGE_KEY, JSON.stringify(identity))
    } else {
      window.localStorage.removeItem(STORAGE_KEY)
    }
  } catch {
    // localStorage may be unavailable (private browsing, etc.) — identity still works in-memory.
  }
  for (const listener of listeners) listener()
}

export function subscribeIdentity(listener: () => void): () => void {
  listeners.add(listener)
  return () => listeners.delete(listener)
}
