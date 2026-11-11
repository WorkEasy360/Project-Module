import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { act, fireEvent, render, screen } from '@testing-library/react'
import { MemoryRouter, Route, Routes, useLocation } from 'react-router-dom'
import { AppLayout } from './AppLayout'
import { SessionProvider } from '../../context/SessionContext'
import { getSessionState, setAccessProblem, setUnauthenticated } from '../../context/sessionStore'
import { buildAccessProblem } from '../../api/sessionMessages'
import { navigateTo, replaceTo } from '../../api/session'
import { abortSigningOut, AUTO_START_GUARD_MS, beginSigningOut, recordAutoStart } from '../../api/autoSignIn'
import { STALLED_AFTER_MS } from './SigningInScreen'
import { resetSessionForTest, signInAs } from '../../test/session'

vi.mock('../../api/session', async (importOriginal) => ({
  ...(await importOriginal<typeof import('../../api/session')>()),
  navigateTo: vi.fn(),
  replaceTo: vi.fn(),
}))

const originalFetch = globalThis.fetch
const ME = '11111111-2222-4333-8444-555555555555'
const GONE = 'Thu, 01 Jan 1970 00:00:00 GMT'
const START = '/api/auth/workeasy360/start?next='

function SignInProbe() {
  const location = useLocation()
  const from = (location.state as { from?: string } | null)?.from
  return <div>SIGN IN PAGE from={from}</div>
}

function routes() {
  return (
    <SessionProvider autoLoad={false}>
      <Routes>
        <Route path="sign-in" element={<SignInProbe />} />
        <Route element={<AppLayout />}>
          <Route index element={<div>HOME CONTENT</div>} />
          <Route path="projects/:id" element={<div>PROJECT CONTENT</div>} />
          <Route path="*" element={<div>OTHER CONTENT</div>} />
        </Route>
      </Routes>
    </SessionProvider>
  )
}

/** The app's route table in miniature: the sign-in route outside the guard, everything else behind it. */
function renderAt(entry: string) {
  return render(<MemoryRouter initialEntries={[entry]}>{routes()}</MemoryRouter>)
}

describe('opening Easy Project with no session: automatic sign-in', () => {
  beforeEach(() => {
    window.sessionStorage.clear()
    setUnauthenticated()
  })
  afterEach(() => {
    globalThis.fetch = originalFetch
    document.cookie = `ep_signed_out=; expires=${GONE}; path=/`
    document.cookie = `ep_auto_start_at=; expires=${GONE}; path=/`
    window.sessionStorage.clear()
    vi.mocked(navigateTo).mockClear()
    vi.mocked(replaceTo).mockClear()
    abortSigningOut()
    vi.useRealTimers()
    resetSessionForTest()
    vi.restoreAllMocks()
  })

  it('goes straight to the start URL with the route as next, showing a neutral screen and not the sign-in page', () => {
    renderAt('/projects/42')

    expect(replaceTo).toHaveBeenCalledTimes(1)
    expect(replaceTo).toHaveBeenCalledWith(START + encodeURIComponent('/projects/42'))
    // The automatic start replaces the history entry (Back from Workeasy360 must not bounce forward again).
    expect(navigateTo).not.toHaveBeenCalled()
    expect(screen.queryByText(/SIGN IN PAGE/)).not.toBeInTheDocument()
    expect(screen.queryByText('PROJECT CONTENT')).not.toBeInTheDocument()
    expect(screen.queryByText('Continue with Workeasy360')).not.toBeInTheDocument()
  })

  it('the neutral screen has exactly one h1 and nothing to choose or type', () => {
    const { container } = renderAt('/projects/42')

    expect(container.querySelectorAll('h1')).toHaveLength(1)
    expect(screen.getByRole('heading', { level: 1, name: 'Signing you in with Workeasy360…' })).toBeInTheDocument()
    expect(screen.getByRole('status')).toHaveTextContent(/easy project uses your workeasy360 account/i)
    expect(container.querySelector('a, button, input')).toBeNull()
    expect(container.querySelectorAll('main')).toHaveLength(1)
  })

  it('opening the root (no route) asks for next=/ and sends no organization hint', () => {
    renderAt('/')

    expect(replaceTo).toHaveBeenCalledWith(START + '%2F')
    expect(vi.mocked(replaceTo).mock.calls[0][0]).not.toContain('organization')
  })

  it('a deep route keeps its path and query, encoded', () => {
    renderAt('/projects/42?tab=tasks&x=1')

    expect(replaceTo).toHaveBeenCalledWith(START + encodeURIComponent('/projects/42?tab=tasks&x=1'))
  })

  it('records the start, so a second automatic start inside the minute is refused (the loop guard)', () => {
    const first = renderAt('/projects/42')
    expect(replaceTo).toHaveBeenCalledTimes(1)
    first.unmount()

    // The browser came back with still no session, a few seconds later.
    renderAt('/projects/42')

    expect(replaceTo).toHaveBeenCalledTimes(1)
    expect(screen.getByText('SIGN IN PAGE from=/projects/42')).toBeInTheDocument()
  })

  it('starts again once the minute is over', () => {
    window.sessionStorage.setItem('ep.autoSignInStartedAt', String(Date.now() - AUTO_START_GUARD_MS - 1000))
    renderAt('/projects/42')
    expect(replaceTo).toHaveBeenCalledTimes(1)
  })

  it('still refuses just inside the minute', () => {
    recordAutoStart(Date.now() - (AUTO_START_GUARD_MS - 5000))
    renderAt('/projects/42')
    expect(replaceTo).not.toHaveBeenCalled()
    expect(screen.getByText(/SIGN IN PAGE/)).toBeInTheDocument()
  })

  it('does not decide again after recording: the screen stays until the browser leaves', () => {
    const { rerender } = renderAt('/projects/42')
    rerender(<MemoryRouter initialEntries={['/projects/42']}>{routes()}</MemoryRouter>)
    expect(screen.queryByText(/SIGN IN PAGE/)).not.toBeInTheDocument()
    expect(screen.getByRole('heading', { level: 1 })).toBeInTheDocument()
  })

  it('never starts while the signed-out marker is present', () => {
    document.cookie = 'ep_signed_out=1; path=/'
    renderAt('/projects/42?tab=a')

    expect(replaceTo).not.toHaveBeenCalled()
    expect(screen.getByText('SIGN IN PAGE from=/projects/42?tab=a')).toBeInTheDocument()
  })

  it('does not start when the session check itself failed (could not tell is not "no session")', () => {
    setUnauthenticated('Could not reach the server. Check your connection and try again.')
    renderAt('/projects/42')

    expect(replaceTo).not.toHaveBeenCalled()
    expect(screen.getByText(/SIGN IN PAGE/)).toBeInTheDocument()
  })

  it('a session that is lost while the app is open starts a sign-in for the page the person is on', () => {
    signInAs(ME)
    renderAt('/projects/42')
    expect(screen.getByText('PROJECT CONTENT')).toBeInTheDocument()
    expect(replaceTo).not.toHaveBeenCalled()

    act(() => setUnauthenticated())

    expect(replaceTo).toHaveBeenCalledWith(START + encodeURIComponent('/projects/42'))
  })

  it('never replaces a session whose access ended: the explanation stays, nothing starts', () => {
    signInAs(ME)
    renderAt('/projects/42')
    act(() => setAccessProblem(buildAccessProblem('workeasy360_access_ended', 'expired')))

    expect(screen.getByRole('heading', { name: 'Easy Project access has ended' })).toBeInTheDocument()
    expect(replaceTo).not.toHaveBeenCalled()
    expect(getSessionState().status).toBe('authenticated')
  })

  it('never replaces a session that cannot be confirmed either', () => {
    signInAs(ME)
    renderAt('/projects/42')
    act(() => setAccessProblem(buildAccessProblem('workeasy360_access_unverified', null)))

    expect(screen.getByRole('heading', { name: "Workeasy360 can't confirm access right now" })).toBeInTheDocument()
    expect(replaceTo).not.toHaveBeenCalled()
  })

  it('does not start while the session is still being checked', () => {
    resetSessionForTest()
    renderAt('/projects/42')

    expect(replaceTo).not.toHaveBeenCalled()
    expect(screen.getByRole('status')).toHaveTextContent(/checking your sign-in/i)
  })

  it('the sign-in route is outside the guard and never starts', () => {
    renderAt('/sign-in')
    expect(replaceTo).not.toHaveBeenCalled()
    expect(screen.getByText(/SIGN IN PAGE/)).toBeInTheDocument()
  })

  it('never starts while a sign-out is in progress, even before the marker cookie has arrived', () => {
    signInAs(ME)
    renderAt('/projects/42')
    expect(screen.getByText('PROJECT CONTENT')).toBeInTheDocument()

    beginSigningOut()
    // A call that was already in flight gets its 401 before the logout's own answer (and its Set-Cookie).
    act(() => setUnauthenticated())

    expect(screen.getByText('SIGN IN PAGE from=/projects/42')).toBeInTheDocument()
    expect(replaceTo).not.toHaveBeenCalled()
  })

  describe('a start that does not complete', () => {
    beforeEach(() => {
      vi.useFakeTimers()
    })

    it('offers a way out after about 5 seconds, with exactly one h1 throughout and aria-busy while waiting', () => {
      const { container } = renderAt('/projects/42?tab=x')
      const main = container.querySelector('main') as HTMLElement
      expect(STALLED_AFTER_MS).toBe(5000)
      expect(main).toHaveAttribute('aria-busy', 'true')
      expect(screen.queryByRole('link')).not.toBeInTheDocument()

      act(() => {
        vi.advanceTimersByTime(STALLED_AFTER_MS - 1)
      })
      expect(screen.queryByRole('link')).not.toBeInTheDocument()

      act(() => {
        vi.advanceTimersByTime(1)
      })

      expect(container.querySelectorAll('h1')).toHaveLength(1)
      expect(main).toHaveAttribute('aria-busy', 'false')
      const start = screen.getByRole('link', { name: 'Continue with Workeasy360' })
      expect(start).toHaveAttribute('href', START + encodeURIComponent('/projects/42?tab=x'))
      expect(screen.getByRole('link', { name: 'Go to the sign-in page' })).toBeInTheDocument()
      expect(screen.getByRole('status')).toHaveTextContent(/taking longer than expected/i)
      // Still nothing was done on the person's behalf beyond the one automatic start.
      expect(replaceTo).toHaveBeenCalledTimes(1)
    })

    it('the link to the sign-in page leads there, carrying the route that was asked for', () => {
      renderAt('/projects/42?tab=x')
      act(() => {
        vi.advanceTimersByTime(STALLED_AFTER_MS)
      })

      fireEvent.click(screen.getByRole('link', { name: 'Go to the sign-in page' }))

      expect(screen.getByText('SIGN IN PAGE from=/projects/42?tab=x')).toBeInTheDocument()
      expect(replaceTo).toHaveBeenCalledTimes(1)
    })

    it('shows no way out when the screen is gone before the time is up (no timer left running)', () => {
      const view = renderAt('/projects/42')
      view.unmount()
      act(() => {
        vi.advanceTimersByTime(STALLED_AFTER_MS * 2)
      })
      expect(screen.queryByRole('link')).not.toBeInTheDocument()
    })
  })

  it.each([['//evil.example/x'], ['/%2F/evil.example'], ['/api/v1/projects']])(
    'a hostile route %j is never forwarded: next falls back to /',
    (path) => {
      renderAt(path)
      expect(replaceTo).toHaveBeenCalledWith(START + '%2F')
    },
  )
})
