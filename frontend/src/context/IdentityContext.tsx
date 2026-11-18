import { useCallback, useEffect, useSyncExternalStore, type ReactNode } from 'react'
import { type Identity, setIdentity } from './identityStore'
import { getCurrentIdentity, signOutCurrent, subscribeCurrentIdentity } from './currentIdentity'

/**
 * Who is signed in, for every page of the app, whichever way they signed in (see `currentIdentity.ts`):
 * an email account (`provider: 'password'`, with its session token) or Workeasy360
 * (`provider: 'workeasy360'`, no token: the HttpOnly cookie is the credential). An email session wins
 * when both exist. Pages that only need "who am I" use `identity.userId` / `identity.orgId` and do not
 * care which; pages that differ (Settings, Team, the account menu) look at `identity.provider`.
 */
interface IdentityContextValue {
  identity: Identity | null
  isConfigured: boolean
  /** Starts an email session (an identity with its token). */
  setIdentity: (identity: Identity, remember?: boolean) => void
  /** Signs out: the email session on the server and here, or the Workeasy360 sign-in to Easy Project. */
  clearIdentity: () => void
  /** Shared so both the header and Settings open the same account dialog. */
  isPromptOpen: boolean
  openPrompt: () => void
  closePrompt: () => void
}

// The account dialog's open state, shared by the header and Settings.
let promptOpen = false
const promptListeners = new Set<() => void>()

function setPromptOpen(open: boolean): void {
  if (promptOpen === open) return
  promptOpen = open
  for (const listener of promptListeners) listener()
}

function subscribePrompt(listener: () => void): () => void {
  promptListeners.add(listener)
  return () => promptListeners.delete(listener)
}

/**
 * Kept so the app (and tests) can still wrap the tree in it. The state lives in the two external
 * stores and a module-level dialog flag, so `useIdentity` works with or without it.
 */
export function IdentityProvider({ children }: { children: ReactNode }) {
  // The dialog belongs to the tree that opened it.
  useEffect(() => () => setPromptOpen(false), [])
  return <>{children}</>
}

export function useIdentity(): IdentityContextValue {
  const identity = useSyncExternalStore(subscribeCurrentIdentity, getCurrentIdentity, getCurrentIdentity)
  const isPromptOpen = useSyncExternalStore(subscribePrompt, () => promptOpen, () => false)

  const update = useCallback((next: Identity, remember = true) => setIdentity(next, remember), [])
  const clear = useCallback(() => {
    signOutCurrent().catch(() => undefined)
  }, [])
  const openPrompt = useCallback(() => setPromptOpen(true), [])
  const closePrompt = useCallback(() => setPromptOpen(false), [])

  return {
    identity,
    isConfigured: identity !== null,
    setIdentity: update,
    clearIdentity: clear,
    isPromptOpen,
    openPrompt,
    closePrompt,
  }
}
