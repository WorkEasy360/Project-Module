import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { SignInPage } from './SignInPage'
import { SessionProvider } from '../../context/SessionContext'
import { setUnauthenticated } from '../../context/sessionStore'
import { setIdentity } from '../../context/identityStore'
import { SSO_ERROR_MESSAGES } from '../../api/sessionMessages'
import { resetSessionForTest, setSignInModeForTest, signInAs } from '../../test/session'

/**
 * The sign-in page with "Sign in with Workeasy360" configured. The first suite is a deployment where
 * Workeasy360 is the only way in (`sso_only`); the second, the usual one, where it is offered next to the
 * email sign-in (`sso_optional`). The email sign-in itself is covered by SignInPage.test.tsx.
 */

const originalFetch = globalThis.fetch

function renderPage(entry: string | { pathname: string; search?: string; state?: unknown } = '/sign-in') {
  return render(
    <MemoryRouter initialEntries={[entry]}>
      <SessionProvider autoLoad={false}>
        <Routes>
          <Route path="/sign-in" element={<SignInPage />} />
          <Route path="/" element={<div>APP HOME</div>} />
          <Route path="/projects/:id" element={<div>PROJECT PAGE</div>} />
        </Routes>
      </SessionProvider>
    </MemoryRouter>,
  )
}

describe('SignInPage where Workeasy360 is the only way in (sso_only)', () => {
  beforeEach(() => setSignInModeForTest('sso_only'))
  afterEach(() => {
    globalThis.fetch = originalFetch
    resetSessionForTest()
    vi.restoreAllMocks()
  })

  it('offers "Continue with Workeasy360" as a plain link and has no password or id form', () => {
    setUnauthenticated()
    renderPage()
    expect(screen.getByText('Already a Workeasy360 user?')).toBeInTheDocument()
    const link = screen.getByRole('link', { name: /continue with workeasy360/i })
    expect(link).toHaveAttribute('href', '/api/auth/workeasy360/start?next=%2F')
    expect(screen.queryByRole('textbox')).not.toBeInTheDocument()
    expect(screen.queryByLabelText(/password|user id|organization id/i)).not.toBeInTheDocument()
    expect(screen.queryByRole('alert')).not.toBeInTheDocument()
  })

  it('carries the route AppLayout asked for as the encoded next parameter', () => {
    setUnauthenticated()
    renderPage({ pathname: '/sign-in', state: { from: '/projects/123/tasks?q=a%20b' } })
    expect(screen.getByRole('link', { name: /continue with workeasy360/i })).toHaveAttribute(
      'href',
      '/api/auth/workeasy360/start?next=' + encodeURIComponent('/projects/123/tasks?q=a%20b'),
    )
  })

  it.each([['/sign-in'], [''], ['//evil.example'], ['https://evil.example'], ['/sign-in?sso_error=expired']])(
    'falls back to "/" when the requested route is %j',
    (from) => {
      setUnauthenticated()
      renderPage({ pathname: '/sign-in', state: { from } })
      expect(screen.getByRole('link', { name: /continue/i })).toHaveAttribute('href', '/api/auth/workeasy360/start?next=%2F')
    },
  )

  it('never starts sign-in by itself: no fetch, no navigation, only a link to click', () => {
    const fetchMock = vi.fn()
    globalThis.fetch = fetchMock as unknown as typeof fetch
    setUnauthenticated()
    renderPage({ pathname: '/sign-in', state: { from: '/projects/1' } })
    expect(fetchMock).not.toHaveBeenCalled()
    expect(screen.getByRole('link', { name: /continue/i })).toBeInTheDocument()
  })

  it('shows a loading state, not the link, while the session is being checked', () => {
    renderPage()
    expect(screen.getByRole('status')).toHaveTextContent(/checking your sign-in/i)
    expect(screen.queryByRole('link', { name: /continue/i })).not.toBeInTheDocument()
  })

  it('shows a plain-language message for the reported sso_error', () => {
    setUnauthenticated()
    renderPage('/sign-in?sso_error=wrong_organization')
    expect(screen.getByRole('alert')).toHaveTextContent(
      'You signed in to a different organization than the one you chose. Start again from the Workeasy360 launcher.',
    )
    // The way to try again is still right there.
    expect(screen.getByRole('link', { name: /continue/i })).toBeInTheDocument()
  })

  it('has a distinct, non-technical message for every documented sso_error code', () => {
    const codes = [
      'cancelled', 'expired', 'unavailable', 'refused', 'rate_limited', 'not_entitled', 'no_seat',
      'provider_membership_inactive', 'organization_inactive', 'access_policy', 'email_missing',
      'email_unverified', 'account_conflict', 'account_disabled', 'membership_inactive', 'wrong_product',
      'wrong_organization',
    ]
    expect(Object.keys(SSO_ERROR_MESSAGES).sort()).toEqual([...codes].sort())
    const seen = new Set<string>()
    setUnauthenticated()
    for (const code of codes) {
      const { unmount } = renderPage(`/sign-in?sso_error=${code}`)
      const text = screen.getByRole('alert').textContent ?? ''
      expect(text.length).toBeGreaterThan(20)
      expect(text).not.toMatch(/_/)
      seen.add(text)
      unmount()
    }
    expect(seen.size).toBe(codes.length)
  })

  it('states the key messages exactly', () => {
    setUnauthenticated()
    const { unmount } = renderPage('/sign-in?sso_error=unavailable')
    expect(screen.getByRole('alert')).toHaveTextContent('Workeasy360 sign-in is not available right now. Try again shortly.')
    unmount()
    renderPage('/sign-in?sso_error=expired')
    expect(screen.getByRole('alert')).toHaveTextContent('That sign-in took too long or was already used. Start again.')
  })

  it('treats an unknown sso_error code as refused', () => {
    setUnauthenticated()
    renderPage('/sign-in?sso_error=something_new')
    expect(screen.getByRole('alert')).toHaveTextContent(SSO_ERROR_MESSAGES.refused)
  })

  it('explains a session check that could not reach the server', () => {
    setUnauthenticated('Could not reach the server. Check your connection and try again.')
    renderPage()
    expect(screen.getByRole('alert')).toHaveTextContent(/could not reach the server/i)
  })

  it('goes straight into the app when already signed in', () => {
    signInAs('11111111-2222-4333-8444-555555555555')
    renderPage()
    expect(screen.getByText('APP HOME')).toBeInTheDocument()
  })

  it('keeps the error visible when a launcher sign-in failed but the person is still signed in', () => {
    signInAs('11111111-2222-4333-8444-555555555555')
    renderPage('/sign-in?sso_error=wrong_organization')
    expect(screen.queryByText('APP HOME')).not.toBeInTheDocument()
    expect(screen.getByRole('alert')).toHaveTextContent(SSO_ERROR_MESSAGES.wrong_organization)
    expect(screen.getByRole('link', { name: /continue in current organization/i })).toHaveAttribute('href', '/')
    expect(screen.getByRole('button', { name: /^sign out$/i })).toBeInTheDocument()
  })

  it('continues in the current organization from the error screen', async () => {
    signInAs('11111111-2222-4333-8444-555555555555')
    renderPage('/sign-in?sso_error=wrong_organization')
    await userEvent.click(screen.getByRole('link', { name: /continue in current organization/i }))
    expect(screen.getByText('APP HOME')).toBeInTheDocument()
  })

  it.each([['__proto__'], ['constructor'], ['toString'], ['hasOwnProperty']])(
    'treats the prototype key %j as an unknown code, not as a message',
    (code) => {
      setUnauthenticated()
      renderPage(`/sign-in?sso_error=${code}`)
      expect(screen.getByRole('alert')).toHaveTextContent(SSO_ERROR_MESSAGES.refused)
    },
  )

  it('returns an already signed-in person to the route they asked for', () => {
    signInAs('11111111-2222-4333-8444-555555555555')
    renderPage({ pathname: '/sign-in', state: { from: '/projects/9' } })
    expect(screen.getByText('PROJECT PAGE')).toBeInTheDocument()
  })
})

describe('SignInPage with Workeasy360 next to the email sign-in (sso_optional)', () => {
  beforeEach(() => {
    setIdentity(null)
    setSignInModeForTest('sso_optional')
  })
  afterEach(() => {
    globalThis.fetch = originalFetch
    setIdentity(null)
    resetSessionForTest()
    vi.restoreAllMocks()
  })

  it('offers both: the email form and "Continue with Workeasy360" as a plain link carrying next', () => {
    setUnauthenticated()
    renderPage({ pathname: '/sign-in', state: { from: '/projects/123/tasks' } })
    expect(screen.getByLabelText('Email')).toBeInTheDocument()
    expect(screen.getByLabelText('Password')).toHaveAttribute('type', 'password')
    expect(screen.getByRole('link', { name: /continue with workeasy360/i })).toHaveAttribute(
      'href',
      '/api/auth/workeasy360/start?next=' + encodeURIComponent('/projects/123/tasks'),
    )
  })

  it('never starts sign-in by itself: no request, no navigation', () => {
    const fetchMock = vi.fn()
    globalThis.fetch = fetchMock as unknown as typeof fetch
    setUnauthenticated()
    renderPage({ pathname: '/sign-in', state: { from: '/projects/1' } })
    expect(fetchMock).not.toHaveBeenCalled()
    expect(screen.getByRole('link', { name: /continue with workeasy360/i })).toBeInTheDocument()
  })

  it('shows the reported sso_error above the form, which stays usable', () => {
    setUnauthenticated()
    renderPage('/sign-in?sso_error=no_seat')
    expect(screen.getByRole('alert')).toHaveTextContent(SSO_ERROR_MESSAGES.no_seat)
    expect(screen.getByLabelText('Email')).toBeInTheDocument()
    expect(screen.getByRole('link', { name: /continue with workeasy360/i })).toBeInTheDocument()
  })

  it('shows a loading state instead of the link while the Workeasy360 session is being checked', () => {
    renderPage()
    expect(screen.getByLabelText('Email')).toBeInTheDocument()
    expect(screen.getByRole('status')).toHaveTextContent(/checking your sign-in/i)
    expect(screen.queryByRole('link', { name: /continue with workeasy360/i })).not.toBeInTheDocument()
  })

  it('does not offer Workeasy360 on an invitation or a reset link', () => {
    setUnauthenticated()
    const { unmount } = render(
      <MemoryRouter initialEntries={['/reset/abc']}>
        <SessionProvider autoLoad={false}>
          <Routes>
            <Route path="/reset/:resetToken" element={<SignInPage />} />
          </Routes>
        </SessionProvider>
      </MemoryRouter>,
    )
    expect(screen.getByLabelText('New password')).toBeInTheDocument()
    expect(screen.queryByRole('link', { name: /continue with workeasy360/i })).not.toBeInTheDocument()
    unmount()
  })

  it('a Workeasy360 session goes straight into the app; with a failed launcher sign-in it explains instead', () => {
    signInAs('11111111-2222-4333-8444-555555555555')
    const { unmount } = renderPage()
    expect(screen.getByText('APP HOME')).toBeInTheDocument()
    unmount()
    renderPage('/sign-in?sso_error=wrong_organization')
    expect(screen.queryByText('APP HOME')).not.toBeInTheDocument()
    expect(screen.getByRole('alert')).toHaveTextContent(SSO_ERROR_MESSAGES.wrong_organization)
    expect(screen.getByRole('link', { name: /continue in current organization/i })).toHaveAttribute('href', '/')
  })
})

describe('SignInPage without Workeasy360 sign-in', () => {
  afterEach(() => {
    resetSessionForTest()
  })

  it('offers no "Continue with Workeasy360" when the server has not configured it', () => {
    setSignInModeForTest('off')
    setUnauthenticated()
    renderPage()
    expect(screen.getByLabelText('Email')).toBeInTheDocument()
    expect(screen.queryByRole('link', { name: /workeasy360/i })).not.toBeInTheDocument()
  })

  it('offers none either when the server does not say (a server from before Workeasy360 sign-in)', async () => {
    globalThis.fetch = vi.fn().mockResolvedValue(
      new Response(JSON.stringify({ googleClientId: '', emailEnabled: true }), { status: 200 }),
    ) as unknown as typeof fetch
    setUnauthenticated()
    renderPage()
    expect(await screen.findByLabelText('Email')).toBeInTheDocument()
    await vi.waitFor(() => expect(globalThis.fetch).toHaveBeenCalled())
    expect(screen.queryByRole('link', { name: /workeasy360/i })).not.toBeInTheDocument()
    globalThis.fetch = originalFetch
  })
})
