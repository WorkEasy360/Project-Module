import {
  createContext,
  useCallback,
  useContext,
  useState,
  useSyncExternalStore,
  type ReactNode,
} from 'react'
import { authApi } from '../api/auth'
import { type Identity, getIdentity, setIdentity, subscribeIdentity } from './identityStore'

interface IdentityContextValue {
  identity: Identity | null
  isConfigured: boolean
  setIdentity: (identity: Identity, remember?: boolean) => void
  clearIdentity: () => void
  /** Shared so both the header and Settings open the same account dialog. */
  isPromptOpen: boolean
  openPrompt: () => void
  closePrompt: () => void
}

const IdentityContext = createContext<IdentityContextValue | null>(null)

export function IdentityProvider({ children }: { children: ReactNode }) {
  const identity = useSyncExternalStore(subscribeIdentity, getIdentity, () => null)
  const [isPromptOpen, setPromptOpen] = useState(false)

  const update = useCallback((next: Identity, remember = true) => setIdentity(next, remember), [])
  const clear = useCallback(() => {
    // The request captures the token before the identity is cleared. Signing out locally must
    // succeed even if the server cannot be reached.
    if (getIdentity()?.token) authApi.logout().catch(() => undefined)
    setIdentity(null)
  }, [])
  const openPrompt = useCallback(() => setPromptOpen(true), [])
  const closePrompt = useCallback(() => setPromptOpen(false), [])

  const value: IdentityContextValue = {
    identity,
    isConfigured: identity !== null,
    setIdentity: update,
    clearIdentity: clear,
    isPromptOpen,
    openPrompt,
    closePrompt,
  }

  return <IdentityContext.Provider value={value}>{children}</IdentityContext.Provider>
}

export function useIdentity(): IdentityContextValue {
  const ctx = useContext(IdentityContext)
  if (!ctx) throw new Error('useIdentity must be used within an IdentityProvider')
  return ctx
}
