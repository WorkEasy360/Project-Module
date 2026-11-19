import { useEffect, useSyncExternalStore } from 'react'
import { authApi, type AuthConfig } from './auth'

/**
 * How people sign in to this deployment, from `GET /api/v1/auth/config` (public), read once per page.
 *
 * Easy Project has its own email sign-in, so with Workeasy360 configured it is normally registered
 * `sso_optional`: the sign-in page offers both, and nothing starts Workeasy360 sign-in by itself
 * (Workeasy360 integration guide 6b, point 6: a product that is not SSO-only keeps its sign-in page).
 * Only a deployment registered `sso_only` starts it automatically for a visit with no session
 * (`SignedOutGate`). A server that cannot be asked, or does not say, is taken as "Workeasy360 not
 * offered": the person then sees the sign-in page, never a loop.
 */
export interface SignInOptions {
  /** The whole configuration, for the sign-in page (Google, email, hand-over). */
  config: AuthConfig
  /** "Continue with Workeasy360" is offered. */
  workeasy360: boolean
  /** Workeasy360 is the only way in: no email sign-in, and the app starts Workeasy360 sign-in by itself. */
  ssoOnly: boolean
}

const NOT_OFFERED: AuthConfig = { googleClientId: '', emailEnabled: false }

export function toSignInOptions(config: AuthConfig | null | undefined): SignInOptions {
  const safe = config ?? NOT_OFFERED
  const workeasy360 = safe.workeasy360?.enabled === true
  return { config: safe, workeasy360, ssoOnly: workeasy360 && safe.workeasy360?.signInMode === 'sso_only' }
}

let current: SignInOptions | null = null
/** The current options are the fallback for a failed read: the next load asks again. */
let currentFailed = false
let pending: Promise<SignInOptions> | null = null
/** Bumped whenever the options are replaced, so a read that was overtaken cannot overwrite them. */
let generation = 0
const listeners = new Set<() => void>()

function publish(options: SignInOptions | null, failed: boolean): void {
  current = options
  currentFailed = failed
  for (const listener of listeners) listener()
}

/** The options once known, else null (still loading). */
export function getSignInOptions(): SignInOptions | null {
  return current
}

export function subscribeSignInOptions(listener: () => void): () => void {
  listeners.add(listener)
  return () => listeners.delete(listener)
}

/** Reads the options (once; later calls share the answer). A failed read counts as "not offered". */
export function loadSignInOptions(): Promise<SignInOptions> {
  if (current && !currentFailed) return Promise.resolve(current)
  if (!pending) {
    const mine = ++generation
    pending = authApi.config().then(
      (config) => settle(mine, toSignInOptions(config), false),
      () => settle(mine, toSignInOptions(null), true),
    )
  }
  return pending
}

function settle(mine: number, options: SignInOptions, failed: boolean): SignInOptions {
  if (mine !== generation) return current ?? options
  pending = null
  publish(options, failed)
  return options
}

/** The options for a component: null until known; asks the server on mount when they are not. */
export function useSignInOptions(): SignInOptions | null {
  const options = useSyncExternalStore(subscribeSignInOptions, getSignInOptions, getSignInOptions)
  useEffect(() => {
    void loadSignInOptions()
  }, [])
  return options
}

/** Tests: put known options in place (no request), or `null` to forget them. */
export function setSignInOptions(options: SignInOptions | null): void {
  generation++
  pending = null
  publish(options, false)
}
