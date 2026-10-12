import { describe, expect, it, vi, beforeEach, afterEach } from 'vitest'
import { render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import { navigateTo } from '../../api/session'
import { SettingsPage } from './SettingsPage'
import { SessionProvider } from '../../context/SessionContext'
import { getSessionState } from '../../context/sessionStore'
import { resetSessionForTest, signInAs } from '../../test/session'
import { SidebarProvider } from '../../context/SidebarContext'
import { getThemePreference, setThemePreference } from '../../context/themeStore'
import { getCollapsed, setCollapsed } from '../../context/sidebarStore'

vi.mock('../../api/session', async (importOriginal) => ({
  ...(await importOriginal<typeof import('../../api/session')>()),
  navigateTo: vi.fn(),
}))

const ME = '11111111-2222-4333-8444-555555555555'
const ORG = '99999999-2222-4333-8444-555555555555'

function renderSettings() {
  return render(
    <MemoryRouter>
      <SessionProvider autoLoad={false}>
        <SidebarProvider>
          <SettingsPage />
        </SidebarProvider>
      </SessionProvider>
    </MemoryRouter>,
  )
}

const originalFetch = globalThis.fetch

function mockLogoutFetch() {
  const fetchMock = vi.fn().mockResolvedValue(new Response(null, { status: 204 }))
  globalThis.fetch = fetchMock as unknown as typeof fetch
  return fetchMock
}

describe('SettingsPage', () => {
  beforeEach(() => {
    signInAs(ME, { orgId: ORG, email: 'ada@example.com', name: 'Ada Lovelace', workeasy360SignOut: true })
    setThemePreference('system')
    setCollapsed(false)
    document.cookie = 'XSRF-TOKEN=tok%2F1; path=/'
  })
  afterEach(() => {
    setThemePreference('system')
    setCollapsed(false)
    globalThis.fetch = originalFetch
    vi.mocked(navigateTo).mockClear()
    document.cookie = 'XSRF-TOKEN=; expires=Thu, 01 Jan 1970 00:00:00 GMT; path=/'
    resetSessionForTest()
  })

  it('Account shows the signed-in person and read-only ids with copy buttons; nothing is editable', async () => {
    const writeText = vi.fn(() => Promise.resolve())
    Object.defineProperty(navigator, 'clipboard', { value: { writeText }, configurable: true })
    renderSettings()
    const section = screen.getByRole('region', { name: 'Account' })
    expect(within(section).getByText('Ada Lovelace')).toBeInTheDocument()
    expect(within(section).getByText('ada@example.com')).toBeInTheDocument()
    expect(within(section).getByText(ME)).toBeInTheDocument()
    expect(within(section).getByText(ORG)).toBeInTheDocument()
    expect(within(section).queryByRole('textbox')).not.toBeInTheDocument()
    expect(section).not.toHaveTextContent('X-User-Id')
    await userEvent.click(within(section).getByRole('button', { name: 'Copy user ID' }))
    expect(writeText).toHaveBeenCalledWith(ME)
    expect(await within(section).findByText('Copied')).toBeInTheDocument()
    expect(screen.queryByRole('checkbox', { name: /notification/i })).not.toBeInTheDocument()
  })

  it('"Sign out of Easy Project" posts the logout with the CSRF token and ends the session locally', async () => {
    const fetchMock = mockLogoutFetch()
    renderSettings()
    const section = screen.getByRole('region', { name: 'Account' })
    await userEvent.click(within(section).getByRole('button', { name: 'Sign out of Easy Project' }))
    await waitFor(() => expect(getSessionState().status).toBe('unauthenticated'))
    const [url, init] = fetchMock.mock.calls[0] as [string, RequestInit]
    expect(url).toBe('/api/auth/logout')
    expect(init.method).toBe('POST')
    expect((init.headers as Record<string, string>)['X-XSRF-TOKEN']).toBe('tok/1')
    expect(fetchMock).toHaveBeenCalledTimes(1)
  })

  it('"Sign out of Easy Project and Workeasy360" logs out, then navigates to the Workeasy360 sign-out', async () => {
    const fetchMock = mockLogoutFetch()
    renderSettings()
    const section = screen.getByRole('region', { name: 'Account' })
    await userEvent.click(within(section).getByRole('button', { name: 'Sign out of Easy Project and Workeasy360' }))
    await waitFor(() => expect(navigateTo).toHaveBeenCalledWith('/api/auth/workeasy360/logout'))
    expect((fetchMock.mock.calls[0] as [string, RequestInit])[1].method).toBe('POST')
    expect(getSessionState().status).toBe('unauthenticated')
  })

  it('offers only the Easy Project sign-out when Workeasy360 sign-out is not available', () => {
    signInAs(ME, { orgId: ORG, workeasy360SignOut: false })
    renderSettings()
    const section = screen.getByRole('region', { name: 'Account' })
    expect(within(section).getByRole('button', { name: 'Sign out of Easy Project' })).toBeInTheDocument()
    expect(within(section).queryByRole('button', { name: /and Workeasy360/ })).not.toBeInTheDocument()
  })

  it('stays signed in and shows the problem when the logout request fails', async () => {
    globalThis.fetch = vi.fn().mockRejectedValue(new TypeError('offline')) as unknown as typeof fetch
    renderSettings()
    const section = screen.getByRole('region', { name: 'Account' })
    await userEvent.click(within(section).getByRole('button', { name: 'Sign out of Easy Project' }))
    expect(await within(section).findByRole('alert')).toHaveTextContent(/could not reach the server/i)
    expect(getSessionState().status).toBe('authenticated')
  })

  it('Appearance is a System / Light / Dark radio group wired to the theme store', async () => {
    renderSettings()
    const section = screen.getByRole('region', { name: 'Appearance' })
    expect(within(section).getByRole('radio', { name: 'System' })).toBeChecked()
    await userEvent.click(within(section).getByRole('radio', { name: 'Dark' }))
    expect(getThemePreference()).toBe('dark')
    expect(document.documentElement.getAttribute('data-theme')).toBe('dark')
    expect(within(section).getByRole('radio', { name: 'Dark' })).toBeChecked()
    expect(section).toHaveTextContent('Currently showing the dark theme')
    await userEvent.click(within(section).getByRole('radio', { name: 'Light' }))
    expect(getThemePreference()).toBe('light')
    expect(document.documentElement.getAttribute('data-theme')).toBe('light')
  })

  it('Navigation toggles the persisted sidebar collapse', async () => {
    renderSettings()
    const box = within(screen.getByRole('region', { name: 'Navigation' })).getByRole('checkbox')
    expect(box).not.toBeChecked()
    await userEvent.click(box)
    expect(getCollapsed()).toBe(true)
    expect(box).toBeChecked()
    await userEvent.click(box)
    expect(getCollapsed()).toBe(false)
  })

  it('Features lists the registry read-only with release badges; About shows the API base URL', () => {
    renderSettings()
    const features = screen.getByRole('region', { name: 'Features' })
    expect(within(features).getByRole('link', { name: 'My Team' })).toHaveAttribute('href', '/team')
    expect(within(features).getByRole('link', { name: 'Reports' })).toHaveAttribute('href', '/reports')
    expect(within(features).getByRole('heading', { name: 'Workspace · Daily work' })).toBeInTheDocument()
    expect(within(features).getByRole('heading', { name: 'In each project · Project control' })).toBeInTheDocument()
    // Project-scoped features need a project id, so they are not links here.
    expect(within(features).queryByRole('link', { name: 'Activity' })).not.toBeInTheDocument()
    const activity = within(features).getByText('Activity').closest('li')!
    expect(activity).toHaveTextContent('Coming Soon')
    const ai = within(features).getByText('AI Suggestions').closest('li')!
    expect(ai).toHaveTextContent('Beta')
    expect(within(features).getAllByText('Available').length).toBeGreaterThan(10)

    const about = screen.getByRole('region', { name: 'About' })
    expect(about).toHaveTextContent('Same origin as this page')
  })
})
