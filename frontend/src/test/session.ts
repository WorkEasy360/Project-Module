import { resetSession, setAuthenticated, setUnauthenticated, type SessionUser } from '../context/sessionStore'

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
