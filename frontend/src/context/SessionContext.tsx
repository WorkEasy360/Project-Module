import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useSyncExternalStore,
  type ReactNode,
} from 'react'
import { loadSession, logoutRequest, navigateTo, workeasy360LogoutUrl } from '../api/session'
import {
  clearAccessProblem,
  getSessionState,
  setUnauthenticated,
  subscribeSession,
  type AccessProblem,
  type SessionState,
  type SessionStatus,
  type SessionUser,
} from './sessionStore'

export interface SessionContextValue {
  status: SessionStatus
  user: SessionUser | null
  organization: { id: string } | null
  workeasy360SignOut: boolean
  accessProblem: AccessProblem | null
  loadError: string | null
  /** Re-reads the session from the server (no loading flash when already signed in). */
  refresh: () => Promise<void>
  /** Clears the access-problem screen/banner. */
  dismissAccessProblem: () => void
  /** Ends the Easy Project session only. Rejects if the server could not be reached. */
  signOut: () => Promise<void>
  /** Ends the Easy Project session, then sends the browser to Workeasy360's sign-out page. */
  signOutEverywhere: () => Promise<void>
}

const SessionContext = createContext<SessionContextValue | null>(null)

/**
 * Provides the Workeasy360 sign-in session. On mount it asks `GET /api/auth/session`; `autoLoad`
 * is switched off by tests that put a ready-made state into the store instead.
 */
export function SessionProvider({ children, autoLoad = true }: { children: ReactNode; autoLoad?: boolean }) {
  const state: SessionState = useSyncExternalStore(subscribeSession, getSessionState, getSessionState)

  useEffect(() => {
    if (autoLoad) void loadSession()
  }, [autoLoad])

  const refresh = useCallback(() => loadSession(), [])
  const dismissAccessProblem = useCallback(() => clearAccessProblem(), [])
  const signOut = useCallback(async () => {
    await logoutRequest()
    setUnauthenticated()
  }, [])
  const signOutEverywhere = useCallback(async () => {
    await logoutRequest()
    setUnauthenticated()
    navigateTo(workeasy360LogoutUrl())
  }, [])

  const value = useMemo<SessionContextValue>(
    () => ({
      status: state.status,
      user: state.user,
      organization: state.organization,
      workeasy360SignOut: state.workeasy360SignOut,
      accessProblem: state.accessProblem,
      loadError: state.loadError,
      refresh,
      dismissAccessProblem,
      signOut,
      signOutEverywhere,
    }),
    [state, refresh, dismissAccessProblem, signOut, signOutEverywhere],
  )

  return <SessionContext.Provider value={value}>{children}</SessionContext.Provider>
}

export function useSession(): SessionContextValue {
  const ctx = useContext(SessionContext)
  if (!ctx) throw new Error('useSession must be used within a SessionProvider')
  return ctx
}
