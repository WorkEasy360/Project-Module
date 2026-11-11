import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { act, render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes, useLocation } from 'react-router-dom'
import { AppLayout } from './AppLayout'
import { SessionProvider } from '../../context/SessionContext'
import { getSessionState, setAccessProblem, setUnauthenticated } from '../../context/sessionStore'
import { buildAccessProblem } from '../../api/sessionMessages'
import { navigateTo, replaceTo } from '../../api/session'
import { abortSigningOut } from '../../api/autoSignIn'
import { resetSessionForTest, signInAs } from '../../test/session'

vi.mock('../../api/session', async (importOriginal) => ({
  ...(await importOriginal<typeof import('../../api/session')>()),
  navigateTo: vi.fn(),
  replaceTo: vi.fn(),
}))

const originalFetch = globalThis.fetch
const ME = '11111111-2222-4333-8444-555555555555'
const ORG = '99999999-2222-4333-8444-555555555555'
const GONE = 'Thu, 01 Jan 1970 00:00:00 GMT'

/** The server's logout answer: 204 and the session cookie that marks "signed out of Easy Project here". */
function serverLogout() {
  return vi.fn().mockImplementation(async () => {
    document.cookie = 'ep_signed_out=1; path=/'
    return new Response(null, { status: 204 })
  })
}

function SignInProbe() {
  const location = useLocation()
  const from = (location.state as { from?: string } | null)?.from
  return <div>SIGN IN PAGE from={from}</div>
}

function renderApp(autoLoad: boolean, entry = '/projects/42?tab=x') {
  return render(
    <MemoryRouter initialEntries={[entry]}>
      <SessionProvider autoLoad={autoLoad}>
        <Routes>
          <Route path="sign-in" element={<SignInProbe />} />
          <Route element={<AppLayout />}>
            <Route path="projects/:id" element={<div>PROJECT CONTENT</div>} />
          </Route>
        </Routes>
      </SessionProvider>
    </MemoryRouter>,
  )
}

function json(status: number, body: unknown) {
  return new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } })
}

const SESSION_BODY = {
  authenticated: true,
  user: { id: ME, email: 'ada@example.com', name: 'Ada Lovelace' },
  organization: { id: ORG },
  workeasy360SignOut: true,
}

describe('AppLayout guard and SessionProvider', () => {
  beforeEach(() => {
    document.cookie = 'XSRF-TOKEN=tok; path=/'
    window.sessionStorage.clear()
  })
  afterEach(() => {
    globalThis.fetch = originalFetch
    document.cookie = 'XSRF-TOKEN=; expires=Thu, 01 Jan 1970 00:00:00 GMT; path=/'
    document.cookie = `ep_signed_out=; expires=${GONE}; path=/`
    window.sessionStorage.clear()
    vi.mocked(navigateTo).mockClear()
    vi.mocked(replaceTo).mockClear()
    abortSigningOut()
    resetSessionForTest()
    vi.restoreAllMocks()
  })

  it('shows a neutral loading state, then the app, while GET /api/auth/session is answered 200', async () => {
    let resolve!: (r: Response) => void
    const fetchMock = vi.fn().mockReturnValue(new Promise<Response>((r) => (resolve = r)))
    globalThis.fetch = fetchMock as unknown as typeof fetch
    renderApp(true)

    expect(screen.getByRole('status')).toHaveTextContent(/checking your sign-in/i)
    expect(screen.queryByText('PROJECT CONTENT')).not.toBeInTheDocument()
    expect(screen.queryByText(/SIGN IN PAGE/)).not.toBeInTheDocument()

    await act(async () => resolve(json(200, SESSION_BODY)))

    expect(await screen.findByText('PROJECT CONTENT')).toBeInTheDocument()
    const [url, init] = fetchMock.mock.calls[0] as [string, RequestInit]
    expect(url).toBe('/api/auth/session')
    expect(init).toMatchObject({ method: 'GET', credentials: 'same-origin', cache: 'no-store' })
    expect(getSessionState()).toMatchObject({
      status: 'authenticated',
      user: { id: ME, email: 'ada@example.com', name: 'Ada Lovelace' },
      organization: { id: ORG },
      workeasy360SignOut: true,
    })
  })

  it('starts Workeasy360 sign-in for the requested route when the session check answers 401 and nothing forbids it', async () => {
    globalThis.fetch = vi.fn().mockResolvedValue(
      json(401, { type: 'https://errors.projectmodule/identity-missing', status: 401 }),
    ) as unknown as typeof fetch
    renderApp(true)

    await waitFor(() =>
      expect(replaceTo).toHaveBeenCalledWith('/api/auth/workeasy360/start?next=' + encodeURIComponent('/projects/42?tab=x')),
    )
    expect(screen.queryByText(/SIGN IN PAGE/)).not.toBeInTheDocument()
    expect(screen.queryByText('PROJECT CONTENT')).not.toBeInTheDocument()
    expect(getSessionState().status).toBe('unauthenticated')
  })

  it('redirects to the sign-in page carrying the requested route when the session check answers 401 after a sign-out here', async () => {
    document.cookie = 'ep_signed_out=1; path=/'
    globalThis.fetch = vi.fn().mockResolvedValue(
      json(401, { type: 'https://errors.projectmodule/identity-missing', status: 401 }),
    ) as unknown as typeof fetch
    renderApp(true)

    expect(await screen.findByText('SIGN IN PAGE from=/projects/42?tab=x')).toBeInTheDocument()
    expect(screen.queryByText('PROJECT CONTENT')).not.toBeInTheDocument()
    expect(getSessionState().status).toBe('unauthenticated')
    expect(replaceTo).not.toHaveBeenCalled()
  })

  it('treats an unreachable server as signed out, with a message for the sign-in page, and starts nothing', async () => {
    globalThis.fetch = vi.fn().mockRejectedValue(new TypeError('offline')) as unknown as typeof fetch
    renderApp(true)
    expect(await screen.findByText(/SIGN IN PAGE/)).toBeInTheDocument()
    expect(getSessionState().loadError).toMatch(/could not reach the server/i)
    expect(replaceTo).not.toHaveBeenCalled()
  })

  it('starts a sign-in as soon as any API call reports the session is gone', async () => {
    signInAs(ME, { orgId: ORG })
    renderApp(false)
    expect(screen.getByText('PROJECT CONTENT')).toBeInTheDocument()
    act(() => setUnauthenticated())
    await waitFor(() =>
      expect(replaceTo).toHaveBeenCalledWith('/api/auth/workeasy360/start?next=' + encodeURIComponent('/projects/42?tab=x')),
    )
  })

  it('sends a signed-in person back to the sign-in page when the session is gone and they signed out here before', async () => {
    document.cookie = 'ep_signed_out=1; path=/'
    signInAs(ME, { orgId: ORG })
    renderApp(false)
    expect(screen.getByText('PROJECT CONTENT')).toBeInTheDocument()
    act(() => setUnauthenticated())
    expect(await screen.findByText(/SIGN IN PAGE/)).toBeInTheDocument()
    expect(replaceTo).not.toHaveBeenCalled()
  })

  it.each([
    ['expired', "Your organization's Easy Project subscription has ended. An owner can renew it in Workeasy360."],
    ['not_entitled', 'does not include Easy Project'],
    ['suspended', 'is suspended. Contact your administrator.'],
    ['membership_inactive', 'Your account or membership in this organization is no longer active. Contact your administrator.'],
    ['no_seat', 'You no longer have an Easy Project seat. Ask an administrator to assign one.'],
    ['something_else', 'Your access to Easy Project has ended'],
  ])('replaces the app with the access-ended screen (reason %s)', (reason, text) => {
    signInAs(ME, { orgId: ORG })
    renderApp(false)
    act(() => setAccessProblem(buildAccessProblem('workeasy360_access_ended', reason)))

    expect(screen.queryByText('PROJECT CONTENT')).not.toBeInTheDocument()
    expect(screen.getByRole('heading', { name: 'Easy Project access has ended' })).toBeInTheDocument()
    expect(screen.getByRole('alert')).toHaveTextContent(text)
    expect(screen.getByRole('button', { name: 'Try again' })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Sign out' })).toBeInTheDocument()
  })

  it('says Workeasy360 cannot confirm access when the access check is unverified', () => {
    signInAs(ME, { orgId: ORG })
    renderApp(false)
    act(() => setAccessProblem(buildAccessProblem('workeasy360_access_unverified', null)))
    expect(screen.getByRole('heading', { name: "Workeasy360 can't confirm access right now" })).toBeInTheDocument()
    expect(screen.queryByText('PROJECT CONTENT')).not.toBeInTheDocument()
  })

  it('"Try again" clears the problem, re-checks the session and brings the app back', async () => {
    const fetchMock = vi.fn().mockResolvedValue(json(200, SESSION_BODY))
    globalThis.fetch = fetchMock as unknown as typeof fetch
    signInAs(ME, { orgId: ORG })
    renderApp(false)
    act(() => setAccessProblem(buildAccessProblem('workeasy360_access_ended', 'expired')))

    await userEvent.click(screen.getByRole('button', { name: 'Try again' }))

    expect(await screen.findByText('PROJECT CONTENT')).toBeInTheDocument()
    expect(getSessionState().accessProblem).toBeNull()
    expect(fetchMock).toHaveBeenCalledWith('/api/auth/session', expect.objectContaining({ method: 'GET' }))
  })

  it('"Try again" starts a sign-in if the re-check finds the session gone (there is no session left to explain)', async () => {
    globalThis.fetch = vi.fn().mockResolvedValue(json(401, { status: 401 })) as unknown as typeof fetch
    signInAs(ME, { orgId: ORG })
    renderApp(false)
    act(() => setAccessProblem(buildAccessProblem('workeasy360_access_ended', 'suspended')))
    await userEvent.click(screen.getByRole('button', { name: 'Try again' }))
    await waitFor(() => expect(replaceTo).toHaveBeenCalledTimes(1))
    expect(vi.mocked(replaceTo).mock.calls[0][0]).toContain('/api/auth/workeasy360/start?next=')
    expect(screen.queryByText(/SIGN IN PAGE/)).not.toBeInTheDocument()
  })

  it('"Sign out" on the access-ended screen posts the logout and goes to sign-in', async () => {
    const fetchMock = serverLogout()
    globalThis.fetch = fetchMock as unknown as typeof fetch
    signInAs(ME, { orgId: ORG })
    renderApp(false)
    act(() => setAccessProblem(buildAccessProblem('workeasy360_access_ended', 'no_seat')))

    await userEvent.click(screen.getByRole('button', { name: 'Sign out' }))

    expect(await screen.findByText(/SIGN IN PAGE/)).toBeInTheDocument()
    expect(replaceTo).not.toHaveBeenCalled()
    const [url, init] = fetchMock.mock.calls[0] as [string, RequestInit]
    expect(url).toBe('/api/auth/logout')
    expect(init.method).toBe('POST')
    expect((init.headers as Record<string, string>)['X-XSRF-TOKEN']).toBe('tok')
  })

  it('read-only: keeps the app, shows a dismissible notice', async () => {
    signInAs(ME, { orgId: ORG })
    renderApp(false)
    act(() => setAccessProblem(buildAccessProblem('workeasy360_read_only', null)))

    expect(screen.getByText('PROJECT CONTENT')).toBeInTheDocument()
    const notice = screen.getByText(/read-only: you can view/)
    expect(notice).toHaveTextContent("Your organization's Easy Project subscription is read-only: you can view but not change.")

    await userEvent.click(screen.getByRole('button', { name: 'Dismiss' }))
    await waitFor(() => expect(screen.queryByText(/read-only/)).not.toBeInTheDocument())
    expect(screen.getByText('PROJECT CONTENT')).toBeInTheDocument()
  })
})
