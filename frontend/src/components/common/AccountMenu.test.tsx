import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { SessionProvider } from '../../context/SessionContext'
import { getSessionState } from '../../context/sessionStore'
import { navigateTo } from '../../api/session'
import { resetSessionForTest, signInAs } from '../../test/session'
import { AccountMenu } from './AccountMenu'

vi.mock('../../api/session', async (importOriginal) => ({
  ...(await importOriginal<typeof import('../../api/session')>()),
  navigateTo: vi.fn(),
}))

const originalFetch = globalThis.fetch
const ME = '11111111-1111-4111-8111-111111111111'
const ORG = '22222222-2222-4222-8222-222222222222'

function renderMenu() {
  return render(
    <SessionProvider autoLoad={false}>
      <AccountMenu />
    </SessionProvider>,
  )
}

describe('AccountMenu', () => {
  beforeEach(() => {
    document.cookie = 'XSRF-TOKEN=abc; path=/'
  })
  afterEach(() => {
    globalThis.fetch = originalFetch
    document.cookie = 'XSRF-TOKEN=; expires=Thu, 01 Jan 1970 00:00:00 GMT; path=/'
    vi.mocked(navigateTo).mockClear()
    resetSessionForTest()
  })

  it('shows the signed-in name and a short organization id on the trigger', () => {
    signInAs(ME, { orgId: ORG, name: 'Ada Lovelace', email: 'ada@example.com' })
    renderMenu()
    const trigger = screen.getByRole('button', { name: /ada lovelace/i })
    expect(trigger).toHaveTextContent('AL')
    expect(trigger).toHaveTextContent('org 22222222')
  })

  it('falls back to the email name, then to a short id, when no name is shared', () => {
    signInAs(ME, { orgId: ORG, email: 'grace@example.com' })
    const { unmount } = renderMenu()
    expect(screen.getByRole('button', { name: /grace/i })).toBeInTheDocument()
    unmount()
    signInAs(ME, { orgId: ORG })
    renderMenu()
    expect(screen.getByRole('button', { name: /11111111/ })).toBeInTheDocument()
  })

  it('has no identity form: the dialog shows the account and only sign-out actions', async () => {
    signInAs(ME, { orgId: ORG, name: 'Ada', email: 'ada@example.com' })
    renderMenu()
    await userEvent.click(screen.getByRole('button', { name: /ada/i }))
    const dialog = screen.getByRole('dialog', { name: 'Your account' })
    expect(within(dialog).getByText('ada@example.com')).toBeInTheDocument()
    expect(within(dialog).getByText(ORG)).toBeInTheDocument()
    expect(within(dialog).queryByRole('textbox')).not.toBeInTheDocument()
    expect(within(dialog).getByRole('button', { name: 'Sign out of Easy Project' })).toBeInTheDocument()
    expect(within(dialog).queryByRole('button', { name: /and Workeasy360/ })).not.toBeInTheDocument()
  })

  it('"Sign out of Easy Project" posts the logout and leaves the Workeasy360 session alone', async () => {
    const fetchMock = vi.fn().mockResolvedValue(new Response(null, { status: 204 }))
    globalThis.fetch = fetchMock as unknown as typeof fetch
    signInAs(ME, { orgId: ORG, name: 'Ada', workeasy360SignOut: true })
    renderMenu()
    await userEvent.click(screen.getByRole('button', { name: /ada/i }))
    await userEvent.click(screen.getByRole('button', { name: 'Sign out of Easy Project' }))
    await waitFor(() => expect(getSessionState().status).toBe('unauthenticated'))
    expect(fetchMock).toHaveBeenCalledTimes(1)
    const [url, init] = fetchMock.mock.calls[0] as [string, RequestInit]
    expect(url).toBe('/api/auth/logout')
    expect(init).toMatchObject({ method: 'POST', credentials: 'same-origin' })
    expect(navigateTo).not.toHaveBeenCalled()
  })

  it('"Sign out of Easy Project and Workeasy360" logs out here, then goes to the Workeasy360 sign-out', async () => {
    const fetchMock = vi.fn().mockResolvedValue(new Response(null, { status: 204 }))
    globalThis.fetch = fetchMock as unknown as typeof fetch
    signInAs(ME, { orgId: ORG, name: 'Ada', workeasy360SignOut: true })
    renderMenu()
    await userEvent.click(screen.getByRole('button', { name: /ada/i }))
    await userEvent.click(screen.getByRole('button', { name: 'Sign out of Easy Project and Workeasy360' }))
    await waitFor(() => expect(navigateTo).toHaveBeenCalledWith('/api/auth/workeasy360/logout'))
    expect(fetchMock).toHaveBeenCalledTimes(1)
    expect(getSessionState().status).toBe('unauthenticated')
  })
})
