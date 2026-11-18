import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { act, render, screen, waitFor } from '@testing-library/react'
import { useIdentity } from './IdentityContext'
import { getCurrentIdentity } from './currentIdentity'
import { getEarlierSignIn, getIdentity, setIdentity } from './identityStore'
import { getSessionState, setAuthenticated } from './sessionStore'
import { resetSessionForTest, signInAs } from '../test/session'

const ME = '11111111-2222-4333-8444-555555555555'
const ORG = '99999999-2222-4333-8444-555555555555'
const originalFetch = globalThis.fetch

/** Shows what `useIdentity()` reports. Rendered without IdentityProvider on purpose: the hook does not need it. */
function Probe() {
  const { identity, isConfigured, clearIdentity } = useIdentity()
  return (
    <div>
      <output data-testid="who">{identity ? `${identity.provider}:${identity.userId}:${identity.orgId}` : 'nobody'}</output>
      <output data-testid="name">{identity?.name ?? ''}</output>
      <output data-testid="token">{identity?.token ?? 'no token'}</output>
      <output data-testid="configured">{String(isConfigured)}</output>
      <button type="button" onClick={clearIdentity}>
        sign out
      </button>
    </div>
  )
}

describe('useIdentity: one "who is signed in" for both ways of signing in', () => {
  beforeEach(() => {
    setIdentity(null)
    resetSessionForTest()
    window.localStorage.clear()
    window.sessionStorage.clear()
  })
  afterEach(() => {
    globalThis.fetch = originalFetch
    setIdentity(null)
    resetSessionForTest()
    window.localStorage.clear()
    document.cookie = 'XSRF-TOKEN=; expires=Thu, 01 Jan 1970 00:00:00 GMT; path=/'
  })

  it('presents a Workeasy360 session as an identity with no token', () => {
    signInAs(ME, { orgId: ORG, name: 'Ada Lovelace', email: 'ada@example.com' })
    render(<Probe />)
    expect(screen.getByTestId('who')).toHaveTextContent(`workeasy360:${ME}:${ORG}`)
    expect(screen.getByTestId('name')).toHaveTextContent('Ada Lovelace')
    expect(screen.getByTestId('token')).toHaveTextContent('no token')
    expect(screen.getByTestId('configured')).toHaveTextContent('true')
  })

  it('an email session wins over a Workeasy360 one in the same browser', () => {
    signInAs(ME, { orgId: ORG })
    setIdentity({ userId: 'email-user', orgId: 'email-org', token: 'tok', name: 'Ben' })
    render(<Probe />)
    expect(screen.getByTestId('who')).toHaveTextContent('password:email-user:email-org')
    expect(screen.getByTestId('token')).toHaveTextContent('tok')
  })

  it('nobody, while the Workeasy360 session is still being checked or is absent', () => {
    render(<Probe />)
    expect(screen.getByTestId('who')).toHaveTextContent('nobody')
    expect(screen.getByTestId('configured')).toHaveTextContent('false')
  })

  it('follows the stores as they change, and keeps the same object when a session check republishes the same person', () => {
    render(<Probe />)
    act(() => signInAs(ME, { orgId: ORG, name: 'Ada' }))
    expect(screen.getByTestId('who')).toHaveTextContent(`workeasy360:${ME}`)
    const first = getCurrentIdentity()
    act(() =>
      setAuthenticated({ user: { id: ME, email: null, name: 'Ada' }, organization: { id: ORG }, workeasy360SignOut: false }),
    )
    expect(getCurrentIdentity()).toBe(first)
    act(() => setIdentity({ userId: 'email-user', orgId: ORG, token: 'tok' }))
    expect(screen.getByTestId('who')).toHaveTextContent('password:email-user')
    act(() => setIdentity(null))
    expect(screen.getByTestId('who')).toHaveTextContent(`workeasy360:${ME}`)
  })

  it('never stores an identity without a token (it would look like the ids of an earlier version)', () => {
    setIdentity({ userId: ME, orgId: ORG, provider: 'workeasy360' })
    expect(getIdentity()).toBeNull()
    expect(window.localStorage.getItem('projectmodule.identity')).toBeNull()
    expect(getEarlierSignIn()).toBeNull()
  })

  it('stores an email session without the provider it is presented with', () => {
    setIdentity({ userId: ME, orgId: ORG, token: 'tok', provider: 'password' })
    expect(window.localStorage.getItem('projectmodule.identity')).not.toContain('provider')
    expect(getIdentity()).toMatchObject({ userId: ME, token: 'tok' })
  })

  it('signing out a Workeasy360 identity ends the Easy Project session with POST /api/auth/logout', async () => {
    document.cookie = 'XSRF-TOKEN=t; path=/'
    const fetchMock = vi.fn().mockResolvedValue(new Response(null, { status: 204 }))
    globalThis.fetch = fetchMock as unknown as typeof fetch
    signInAs(ME, { orgId: ORG })
    render(<Probe />)
    act(() => screen.getByRole('button', { name: 'sign out' }).click())
    await waitFor(() => expect(getSessionState().status).toBe('unauthenticated'))
    const [url, init] = fetchMock.mock.calls[0] as [string, RequestInit]
    expect(url).toBe('/api/auth/logout')
    expect(init.method).toBe('POST')
    expect(screen.getByTestId('who')).toHaveTextContent('nobody')
  })

  it('signing out an email identity forgets it here even if the server cannot be reached', () => {
    globalThis.fetch = vi.fn().mockRejectedValue(new TypeError('offline')) as unknown as typeof fetch
    setIdentity({ userId: ME, orgId: ORG, token: 'tok' })
    render(<Probe />)
    act(() => screen.getByRole('button', { name: 'sign out' }).click())
    expect(getIdentity()).toBeNull()
    expect(screen.getByTestId('who')).toHaveTextContent('nobody')
  })
})
