// @vitest-environment jsdom
// @vitest-environment-options {"url": "https://ops.workeasy360.test/"}
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { render, screen } from '@testing-library/react'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { AppLayout } from './AppLayout'
import { SessionProvider } from '../../context/SessionContext'
import { replaceTo } from '../../api/session'
import { resetSessionForTest } from '../../test/session'
import { setUnauthenticated } from '../../context/sessionStore'

vi.mock('../../api/session', async (importOriginal) => ({
  ...(await importOriginal<typeof import('../../api/session')>()),
  navigateTo: vi.fn(),
  replaceTo: vi.fn(),
}))

/** The same guard on an https page, where only the host-only `__Host-` marker may suppress the start. */
const GONE = 'Thu, 01 Jan 1970 00:00:00 GMT'

function renderAt(entry: string) {
  return render(
    <MemoryRouter initialEntries={[entry]}>
      <SessionProvider autoLoad={false}>
        <Routes>
          <Route path="sign-in" element={<div>SIGN IN PAGE</div>} />
          <Route element={<AppLayout />}>
            <Route path="projects/:id" element={<div>PROJECT CONTENT</div>} />
          </Route>
        </Routes>
      </SessionProvider>
    </MemoryRouter>,
  )
}

describe('on an https page the signed-out marker is the __Host- cookie only', () => {
  beforeEach(() => {
    window.sessionStorage.clear()
    setUnauthenticated()
  })
  afterEach(() => {
    document.cookie = `ep_signed_out=; expires=${GONE}; path=/`
    document.cookie = `ep_signed_out=; expires=${GONE}; path=/; domain=workeasy360.test`
    document.cookie = `__Host-ep_signed_out=; expires=${GONE}; path=/; Secure`
    window.sessionStorage.clear()
    vi.mocked(replaceTo).mockClear()
    resetSessionForTest()
  })

  it('runs at https', () => {
    expect(window.location.protocol).toBe('https:')
  })

  it('an unprefixed ep_signed_out planted by a sibling subdomain (Domain=workeasy360.test) is ignored: the start still happens', () => {
    document.cookie = 'ep_signed_out=1; path=/; domain=workeasy360.test'
    expect(document.cookie).toContain('ep_signed_out=1')

    renderAt('/projects/42')

    expect(replaceTo).toHaveBeenCalledWith('/api/auth/workeasy360/start?next=' + encodeURIComponent('/projects/42'))
    expect(screen.queryByText('SIGN IN PAGE')).not.toBeInTheDocument()
  })

  it('the host-only __Host- marker the server leaves does suppress it', () => {
    document.cookie = '__Host-ep_signed_out=1; path=/; Secure'
    expect(document.cookie).toContain('__Host-ep_signed_out=1')

    renderAt('/projects/42')

    expect(replaceTo).not.toHaveBeenCalled()
    expect(screen.getByText('SIGN IN PAGE')).toBeInTheDocument()
  })
})
