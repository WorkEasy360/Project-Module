import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { render, screen } from '@testing-library/react'
import App from './App'
import { navigateTo, replaceTo } from './api/session'
import { resetSession } from './context/sessionStore'
import { abortSigningOut } from './api/autoSignIn'
import { setIdentity } from './context/identityStore'
import { setSignInModeForTest } from './test/session'

vi.mock('./api/session', async (importOriginal) => ({
  ...(await importOriginal<typeof import('./api/session')>()),
  navigateTo: vi.fn(),
  replaceTo: vi.fn(),
}))

/**
 * The whole application, with its real HashRouter and routes, opened at addresses a person would type
 * (`/`, `/#/`, `/#/projects/...`, `/#/sign-in?sso_error=...`) in a browser with no Easy Project session.
 */
const originalFetch = globalThis.fetch
const GONE = 'Thu, 01 Jan 1970 00:00:00 GMT'
const START = '/api/auth/workeasy360/start?next='

function noSession() {
  globalThis.fetch = vi.fn().mockResolvedValue(
    new Response(JSON.stringify({ status: 401 }), { status: 401, headers: { 'Content-Type': 'application/json' } }),
  ) as unknown as typeof fetch
}

function openAt(hash: string) {
  window.location.hash = hash
  return render(<App />)
}

describe('App: opening a route with no Easy Project session, where Workeasy360 is the only way in', () => {
  beforeEach(() => {
    setSignInModeForTest('sso_only')
    window.sessionStorage.clear()
    resetSession()
    noSession()
  })
  afterEach(() => {
    globalThis.fetch = originalFetch
    document.cookie = `ep_signed_out=; expires=${GONE}; path=/`
    window.sessionStorage.clear()
    window.location.hash = ''
    vi.mocked(navigateTo).mockClear()
    vi.mocked(replaceTo).mockClear()
    abortSigningOut()
    resetSession()
  })

  it.each([
    ['', '/'],
    ['#/', '/'],
    ['#/dashboard', '/dashboard'],
    ['#/projects', '/projects'],
    ['#/projects/6f1b2c3d-0000-4000-8000-000000000001/tasks', '/projects/6f1b2c3d-0000-4000-8000-000000000001/tasks'],
    ['#/projects/42/kanban?status=OPEN', '/projects/42/kanban?status=OPEN'],
    ['#/settings', '/settings'],
  ])('%j goes straight to the start URL with next=%j, without the sign-in page', async (hash, next) => {
    openAt(hash)

    expect(await screen.findByRole('heading', { level: 1, name: 'Signing you in with Workeasy360…' })).toBeInTheDocument()
    expect(replaceTo).toHaveBeenCalledTimes(1)
    expect(replaceTo).toHaveBeenCalledWith(START + encodeURIComponent(next))
    expect(navigateTo).not.toHaveBeenCalled()
    expect(screen.queryByRole('heading', { name: 'Operations Login' })).not.toBeInTheDocument()
    expect(screen.queryByRole('link', { name: /continue with workeasy360/i })).not.toBeInTheDocument()
    expect(document.querySelectorAll('h1')).toHaveLength(1)
  })

  it.each([
    ['#/sign-in'],
    ['#/sign-in?sso_error=expired'],
    ['#/sign-in?sso_error=wrong_organization'],
    ['#/sign-in?sso_error=something_new'],
    ['#/sign-in?next=/projects/1'],
  ])('the sign-in route %j never starts by itself: it shows "Continue with Workeasy360"', async (hash) => {
    openAt(hash)

    expect(await screen.findByRole('link', { name: /continue with workeasy360/i })).toHaveAttribute(
      'href',
      '/api/auth/workeasy360/start?next=%2F',
    )
    expect(replaceTo).not.toHaveBeenCalled()
    expect(navigateTo).not.toHaveBeenCalled()
    expect(screen.queryByRole('heading', { name: 'Signing you in with Workeasy360…' })).not.toBeInTheDocument()
  })

  it('a failed sign-in shows its reason on the sign-in page and does not start again', async () => {
    openAt('#/sign-in?sso_error=expired')

    expect(await screen.findByRole('alert')).toHaveTextContent('That sign-in took too long or was already used. Start again.')
    expect(replaceTo).not.toHaveBeenCalled()
    expect(navigateTo).not.toHaveBeenCalled()
  })

  it('after a sign-out of Easy Project in this browser (the marker), a deep route shows the sign-in page', async () => {
    document.cookie = 'ep_signed_out=1; path=/'
    openAt('#/projects/42/tasks')

    expect(await screen.findByRole('link', { name: /continue with workeasy360/i })).toHaveAttribute(
      'href',
      '/api/auth/workeasy360/start?next=' + encodeURIComponent('/projects/42/tasks'),
    )
    expect(replaceTo).not.toHaveBeenCalled()
    expect(navigateTo).not.toHaveBeenCalled()
  })

  it('and the root shows the sign-in page too (the post-logout address of "...and Workeasy360")', async () => {
    document.cookie = 'ep_signed_out=1; path=/'
    openAt('')

    expect(await screen.findByRole('heading', { name: 'Operations Login' })).toBeInTheDocument()
    expect(replaceTo).not.toHaveBeenCalled()
    expect(navigateTo).not.toHaveBeenCalled()
  })

  it('an unreachable server shows the sign-in page with the reason, not a start', async () => {
    globalThis.fetch = vi.fn().mockRejectedValue(new TypeError('offline')) as unknown as typeof fetch
    openAt('#/projects')

    expect(await screen.findByRole('alert')).toHaveTextContent(/could not reach the server/i)
    expect(replaceTo).not.toHaveBeenCalled()
    expect(navigateTo).not.toHaveBeenCalled()
  })
})

/**
 * The usual deployment: Easy Project's own email sign-in, with Workeasy360 next to it. The whole app, with
 * the configuration read from the server as in production (`GET /api/v1/auth/config`).
 */
describe('App: opening a route with no session, where the email sign-in is on (sso_optional)', () => {
  function server(config: Record<string, unknown>) {
    globalThis.fetch = vi.fn().mockImplementation((url: string) => {
      if (String(url) === '/api/v1/auth/config') {
        return Promise.resolve(new Response(JSON.stringify(config), { status: 200 }))
      }
      return Promise.resolve(new Response(JSON.stringify({ status: 401 }), { status: 401 }))
    }) as unknown as typeof fetch
  }

  beforeEach(() => {
    window.sessionStorage.clear()
    resetSession()
    setIdentity(null)
  })
  afterEach(() => {
    globalThis.fetch = originalFetch
    window.sessionStorage.clear()
    window.location.hash = ''
    vi.mocked(navigateTo).mockClear()
    vi.mocked(replaceTo).mockClear()
    resetSession()
  })

  it('a deep route shows the sign-in page with both ways in, and nothing starts by itself', async () => {
    server({ googleClientId: '', emailEnabled: true, workeasy360: { enabled: true, signInMode: 'sso_optional' } })
    openAt('#/projects/42/tasks')

    expect(await screen.findByRole('link', { name: /continue with workeasy360/i })).toHaveAttribute(
      'href',
      '/api/auth/workeasy360/start?next=' + encodeURIComponent('/projects/42/tasks'),
    )
    expect(screen.getByLabelText('Email Address')).toBeInTheDocument()
    expect(replaceTo).not.toHaveBeenCalled()
    expect(navigateTo).not.toHaveBeenCalled()
  })

  it('without Workeasy360 configured, the sign-in page is the email one only', async () => {
    server({ googleClientId: '', emailEnabled: true, workeasy360: { enabled: false, signInMode: 'sso_optional' } })
    openAt('#/dashboard')

    expect(await screen.findByLabelText('Email Address')).toBeInTheDocument()
    expect(screen.queryByRole('link', { name: /workeasy360/i })).not.toBeInTheDocument()
    expect(replaceTo).not.toHaveBeenCalled()
  })

  it('an email session opens the app at once, with no Workeasy360 start', async () => {
    server({ googleClientId: '', emailEnabled: true, workeasy360: { enabled: true, signInMode: 'sso_only' } })
    setIdentity({ userId: '11111111-2222-4333-8444-555555555555', orgId: '99999999-2222-4333-8444-555555555555', token: 'tok' })
    openAt('#/settings')

    // At once: no loading state, no sign-in page (this fake server then refuses the token, which is another test's matter).
    expect(screen.getByRole('heading', { level: 1, name: 'Workspace & Access' })).toBeInTheDocument()
    expect(screen.queryByText('Checking your sign-in…')).not.toBeInTheDocument()
    expect(replaceTo).not.toHaveBeenCalled()
    setIdentity(null)
  })
})
