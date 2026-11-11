import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { act, render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { AppLayout } from '../components/layout/AppLayout'
import { SessionProvider, useSession } from './SessionContext'
import { getSessionState, resetSession, setUnauthenticated } from './sessionStore'
import { navigateTo, replaceTo } from '../api/session'
import { abortSigningOut, autoStartedRecently, recordAutoStart } from '../api/autoSignIn'

vi.mock('../api/session', async (importOriginal) => ({
  ...(await importOriginal<typeof import('../api/session')>()),
  navigateTo: vi.fn(),
  replaceTo: vi.fn(),
}))

/**
 * Both sign-outs, end to end through the real session code, against a stand-in for the server that does
 * what the server does: `POST /api/auth/logout` ends the session and leaves the session cookie
 * `ep_signed_out`; a successful sign-in clears it. (The server's half is proven in SignedOutMarkerTest.)
 */
const GONE = 'Thu, 01 Jan 1970 00:00:00 GMT'
const originalFetch = globalThis.fetch
const START_PREFIX = '/api/auth/workeasy360/start'
const ME = '11111111-2222-4333-8444-555555555555'
const ORG = '99999999-2222-4333-8444-555555555555'

let serverSession = false
let logouts = 0

function fakeServer() {
  return vi.fn(async (url: string, init?: RequestInit) => {
    if (url === '/api/auth/logout' && init?.method === 'POST') {
      logouts += 1
      serverSession = false
      document.cookie = 'ep_signed_out=1; path=/' // the Set-Cookie of the logout response
      return new Response(null, { status: 204 })
    }
    if (url === '/api/auth/session') {
      return serverSession
        ? new Response(
            JSON.stringify({
              authenticated: true,
              user: { id: ME, email: 'ada@example.com', name: 'Ada' },
              organization: { id: ORG },
              workeasy360SignOut: true,
            }),
            { status: 200, headers: { 'Content-Type': 'application/json' } },
          )
        : new Response(JSON.stringify({ status: 401 }), { status: 401, headers: { 'Content-Type': 'application/json' } })
    }
    return new Response('{}', { status: 404 })
  })
}

/** What the server does when a sign-in succeeds: a session, and the marker cleared. */
function serverSignsIn() {
  serverSession = true
  document.cookie = `ep_signed_out=; expires=${GONE}; path=/`
}

function SignOutButtons() {
  const { signOut, signOutEverywhere } = useSession()
  return (
    <div>
      <p>APP CONTENT</p>
      <button type="button" onClick={() => signOut().catch(() => undefined)}>
        Sign out of Easy Project
      </button>
      <button type="button" onClick={() => signOutEverywhere().catch(() => undefined)}>
        Sign out of Easy Project and Workeasy360
      </button>
    </div>
  )
}

/** A fresh page load at `entry`: the session is asked for, as `SessionProvider` does on mount. */
function openPage(entry: string) {
  return render(
    <MemoryRouter initialEntries={[entry]}>
      <SessionProvider>
        <Routes>
          <Route path="sign-in" element={<div>SIGN IN PAGE</div>} />
          <Route element={<AppLayout />}>
            <Route path="*" element={<SignOutButtons />} />
          </Route>
        </Routes>
      </SessionProvider>
    </MemoryRouter>,
  )
}

function startCalls() {
  return vi.mocked(replaceTo).mock.calls.filter(([url]) => url.startsWith(START_PREFIX))
}

describe('sign-out leaves a marker that suppresses automatic sign-in until the next sign-in', () => {
  beforeEach(() => {
    serverSession = false
    logouts = 0
    window.sessionStorage.clear()
    document.cookie = 'XSRF-TOKEN=tok; path=/'
    globalThis.fetch = fakeServer() as unknown as typeof fetch
  })
  afterEach(() => {
    globalThis.fetch = originalFetch
    document.cookie = `ep_signed_out=; expires=${GONE}; path=/`
    document.cookie = `XSRF-TOKEN=; expires=${GONE}; path=/`
    window.sessionStorage.clear()
    vi.mocked(navigateTo).mockClear()
    vi.mocked(replaceTo).mockClear()
    abortSigningOut()
    resetSession()
    vi.restoreAllMocks()
  })

  it('"Sign out of Easy Project": the sign-in page, not a silent sign-in; a later visit to / shows the sign-in page too', async () => {
    serverSignsIn()
    const first = openPage('/projects/42')
    expect(await screen.findByText('APP CONTENT')).toBeInTheDocument()
    expect(startCalls()).toHaveLength(0)

    await userEvent.click(screen.getByRole('button', { name: 'Sign out of Easy Project' }))

    expect(await screen.findByText('SIGN IN PAGE')).toBeInTheDocument()
    expect(logouts).toBe(1)
    expect(document.cookie).toContain('ep_signed_out=1')
    expect(startCalls()).toHaveLength(0)
    first.unmount()

    // Later in the same browser session: the person types the address again.
    resetSession()
    openPage('/')
    expect(await screen.findByText('SIGN IN PAGE')).toBeInTheDocument()
    expect(startCalls()).toHaveLength(0)
    expect(navigateTo).not.toHaveBeenCalled()
  })

  it('"Sign out of Easy Project and Workeasy360": leaves for Workeasy360, and the return to / shows the sign-in page', async () => {
    serverSignsIn()
    const first = openPage('/projects/42')
    expect(await screen.findByText('APP CONTENT')).toBeInTheDocument()

    await userEvent.click(screen.getByRole('button', { name: 'Sign out of Easy Project and Workeasy360' }))

    await waitFor(() => expect(navigateTo).toHaveBeenCalledWith('/api/auth/workeasy360/logout'))
    expect(logouts).toBe(1)
    expect(document.cookie).toContain('ep_signed_out=1')
    expect(startCalls()).toHaveLength(0)
    first.unmount()

    // Workeasy360's confirmation page sends the browser to the registered post-logout address: the root.
    resetSession()
    openPage('/')

    expect(await screen.findByText('SIGN IN PAGE')).toBeInTheDocument()
    expect(startCalls()).toHaveLength(0)
    expect(navigateTo).toHaveBeenCalledTimes(1)
  })

  it('a sign-in clears the marker: the next visit with no session starts automatically again', async () => {
    document.cookie = 'ep_signed_out=1; path=/'
    const signedOut = openPage('/projects/42')
    expect(await screen.findByText('SIGN IN PAGE')).toBeInTheDocument()
    expect(startCalls()).toHaveLength(0)
    signedOut.unmount()

    // The person chooses "Continue with Workeasy360" and signs in: the server clears the marker.
    serverSignsIn()
    resetSession()
    const signedIn = openPage('/projects/42')
    expect(await screen.findByText('APP CONTENT')).toBeInTheDocument()
    expect(document.cookie).not.toContain('ep_signed_out')
    signedIn.unmount()

    // Later the session is gone (an idle timeout, a restart) and the person opens the address again.
    serverSession = false
    resetSession()
    openPage('/projects/42')

    await waitFor(() => expect(startCalls()).toHaveLength(1))
    expect(startCalls()[0][0]).toBe(`${START_PREFIX}?next=${encodeURIComponent('/projects/42')}`)
    expect(screen.queryByText('SIGN IN PAGE')).not.toBeInTheDocument()
  })

  it('a session read that finds a session forgets the loop guard: the premise "still no session" is gone', async () => {
    recordAutoStart()
    expect(autoStartedRecently()).toBe(true)
    serverSignsIn()

    openPage('/projects/42')

    expect(await screen.findByText('APP CONTENT')).toBeInTheDocument()
    expect(getSessionState().status).toBe('authenticated')
    expect(autoStartedRecently()).toBe(false)
  })

  it('a first visit with no session and no marker starts automatically, from the real session read (401)', async () => {
    openPage('/projects/42')

    await waitFor(() => expect(startCalls()).toHaveLength(1))
    expect(screen.queryByText('SIGN IN PAGE')).not.toBeInTheDocument()
    expect(screen.queryByText('APP CONTENT')).not.toBeInTheDocument()
  })

  it('a 401 that arrives while the logout is still in flight never starts a sign-in (sign-out race)', async () => {
    serverSignsIn()
    let finishLogout!: () => void
    const base = fakeServer()
    globalThis.fetch = vi.fn(async (url: string, init?: RequestInit) => {
      if (url === '/api/auth/logout') {
        // The server has ended the session; the 204 and its Set-Cookie are still on the way.
        serverSession = false
        await new Promise<void>((resolve) => (finishLogout = resolve))
        return base(url, init)
      }
      return base(url, init)
    }) as unknown as typeof fetch
    openPage('/projects/42')
    expect(await screen.findByText('APP CONTENT')).toBeInTheDocument()

    await userEvent.click(screen.getByRole('button', { name: 'Sign out of Easy Project' }))
    expect(document.cookie).not.toContain('ep_signed_out') // the marker has not arrived yet

    // A call that was already in flight is answered 401 first.
    act(() => setUnauthenticated())

    expect(await screen.findByText('SIGN IN PAGE')).toBeInTheDocument()
    expect(startCalls()).toHaveLength(0)

    await act(async () => finishLogout())
    await waitFor(() => expect(document.cookie).toContain('ep_signed_out=1'))
    expect(startCalls()).toHaveLength(0)
    expect(screen.getByText('SIGN IN PAGE')).toBeInTheDocument()
  })

  it('same race, "Sign out of Easy Project and Workeasy360": the 401 starts nothing and the logout page is still visited', async () => {
    serverSignsIn()
    let finishLogout!: () => void
    const base = fakeServer()
    globalThis.fetch = vi.fn(async (url: string, init?: RequestInit) => {
      if (url === '/api/auth/logout') {
        serverSession = false
        await new Promise<void>((resolve) => (finishLogout = resolve))
      }
      return base(url, init)
    }) as unknown as typeof fetch
    openPage('/projects/42')
    expect(await screen.findByText('APP CONTENT')).toBeInTheDocument()

    await userEvent.click(screen.getByRole('button', { name: 'Sign out of Easy Project and Workeasy360' }))
    act(() => setUnauthenticated())
    expect(await screen.findByText('SIGN IN PAGE')).toBeInTheDocument()
    expect(startCalls()).toHaveLength(0)

    await act(async () => finishLogout())
    await waitFor(() => expect(navigateTo).toHaveBeenCalledWith('/api/auth/workeasy360/logout'))
    expect(startCalls()).toHaveLength(0)
  })

  it('a logout that fails suppresses nothing: the person is still signed in, and a later lost session starts a sign-in', async () => {
    serverSignsIn()
    globalThis.fetch = vi.fn(async (url: string, init?: RequestInit) => {
      if (url === '/api/auth/logout') return new Response(JSON.stringify({ status: 500 }), { status: 500 })
      return fakeServer()(url, init)
    }) as unknown as typeof fetch
    openPage('/projects/42')
    expect(await screen.findByText('APP CONTENT')).toBeInTheDocument()

    await userEvent.click(screen.getByRole('button', { name: 'Sign out of Easy Project' }))
    await waitFor(() => expect(getSessionState().status).toBe('authenticated'))
    expect(screen.getByText('APP CONTENT')).toBeInTheDocument()

    act(() => setUnauthenticated())

    await waitFor(() => expect(startCalls()).toHaveLength(1))
  })
})
