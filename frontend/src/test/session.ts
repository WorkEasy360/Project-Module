import { resetSession, setAuthenticated, setUnauthenticated, type SessionUser } from '../context/sessionStore'
import { setSignInOptions, toSignInOptions } from '../api/signInOptions'
import type { AuthConfig } from '../api/auth'

export const TEST_ORG = '99999999-2222-4333-8444-555555555555'

/** Puts a signed-in session straight into the store (use with `<SessionProvider autoLoad={false}>`). */
export function signInAs(
  userId: string,
  options: { orgId?: string; email?: string | null; name?: string | null; workeasy360SignOut?: boolean } = {},
): void {
  const user: SessionUser = { id: userId, email: options.email ?? null, name: options.name ?? null }
  setAuthenticated({
    user,
    organization: { id: options.orgId ?? TEST_ORG },
    workeasy360SignOut: options.workeasy360SignOut ?? false,
  })
}

export function signOutForTest(): void {
  setUnauthenticated()
}

export function resetSessionForTest(): void {
  resetSession()
}

/**
 * Says how this deployment signs people in, without a request (`GET /api/v1/auth/config`):
 * `'sso_only'` (Workeasy360 is the only way in; the app starts it by itself), `'sso_optional'`
 * (Workeasy360 next to the email sign-in) or `'off'` (no Workeasy360). Reset after every test
 * (src/test/setup.ts).
 */
export function setSignInModeForTest(
  mode: 'sso_only' | 'sso_optional' | 'off',
  config: Partial<AuthConfig> = {},
): void {
  setSignInOptions(
    toSignInOptions({
      googleClientId: '',
      emailEnabled: true,
      ...config,
      workeasy360: mode === 'off' ? { enabled: false, signInMode: 'sso_optional' } : { enabled: true, signInMode: mode },
    }),
  )
}
