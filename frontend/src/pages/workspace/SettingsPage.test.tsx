import { describe, expect, it, vi, beforeEach, afterEach } from 'vitest'
import { render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import { navigateTo } from '../../api/session'
import { SettingsPage, tagCounts, type SettingsSection } from './SettingsPage'
import { IdentityProvider } from '../../context/IdentityContext'
import { SessionProvider } from '../../context/SessionContext'
import { getSessionState } from '../../context/sessionStore'
import { resetSessionForTest, signInAs } from '../../test/session'
import { WorkspaceSettingsProvider } from '../../context/WorkspaceSettingsContext'
import { getIdentity, setIdentity } from '../../context/identityStore'
import { getThemePreference, setThemePreference } from '../../context/themeStore'
import { projectsApi } from '../../api/projects'

const ME = '11111111-2222-4333-8444-555555555555'
const ORG = '99999999-2222-4333-8444-555555555555'

vi.mock('../../api/session', async (importOriginal) => ({
  ...(await importOriginal<typeof import('../../api/session')>()),
  navigateTo: vi.fn(),
}))
vi.mock('../../api/auth', () => ({ authApi: { logout: vi.fn(() => Promise.resolve()) } }))
vi.mock('../../api/projects', () => ({ projectsApi: { list: vi.fn() } }))
vi.mock('../../api/workspace', async (importOriginal) => ({
  ...(await importOriginal<typeof import('../../api/workspace')>()),
  workspaceApi: {
    people: vi.fn(() => Promise.resolve([])),
    settings: vi.fn(() =>
      Promise.resolve({
        name: 'Acme Enterprises Pvt. Ltd.',
        canEdit: true,
        plan: { name: 'Advanced', validUntil: '31 Dec 2026', manageUrl: 'https://workeasy360.com/plans' },
        supportEmail: 'support@workeasy360.com',
      }),
    ),
    rename: vi.fn(),
  },
}))

function renderSettings(section: SettingsSection) {
  return render(
    <MemoryRouter>
      <SessionProvider autoLoad={false}>
        <IdentityProvider>
          <WorkspaceSettingsProvider>
            <SettingsPage section={section} />
          </WorkspaceSettingsProvider>
        </IdentityProvider>
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
    setIdentity({ userId: ME, orgId: ORG, token: 'tok', name: 'Ada', email: 'ada@example.com' })
    setThemePreference('light')
    vi.mocked(projectsApi.list).mockReset().mockResolvedValue({
      content: [
        { id: 'p1', name: 'A', status: 'ACTIVE', priority: 'MEDIUM', ownerId: ME, updatedAt: '2026-11-01T00:00:00Z', tags: ['Sales', 'Ops'] },
        { id: 'p2', name: 'B', status: 'ACTIVE', priority: 'MEDIUM', ownerId: ME, updatedAt: '2026-11-01T00:00:00Z', tags: ['Sales'] },
      ],
      page: 0,
      size: 100,
      totalElements: 2,
      totalPages: 1,
    })
  })
  afterEach(() => {
    setIdentity(null)
    setThemePreference('system')
    resetSessionForTest()
  })

  it('Workspace & Access shows the workspace, your profile without internal ids, and signs out', async () => {
    renderSettings('workspace')
    expect(screen.getByRole('heading', { name: 'Workspace & Access' })).toBeInTheDocument()
    expect(await screen.findByText('Acme Enterprises Pvt. Ltd.')).toBeInTheDocument()
    const profile = screen.getByRole('region', { name: 'Your profile' })
    expect(within(profile).getByText('Ada')).toBeInTheDocument()
    expect(within(profile).getByText('ada@example.com')).toBeInTheDocument()
    expect(within(profile).queryByText(ME)).not.toBeInTheDocument()
    expect(within(profile).getByRole('button', { name: 'Edit name' })).toBeInTheDocument()
    expect(within(profile).getByRole('button', { name: 'Change password' })).toBeInTheDocument()
    expect(within(profile).queryByRole('button', { name: /Workeasy360/ })).not.toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Team → Members' })).toHaveAttribute('href', '/team')
    await userEvent.click(within(profile).getByRole('button', { name: /Sign out/ }))
    expect(getIdentity()).toBeNull()
  })

  it('Appearance switches the theme', async () => {
    renderSettings('workspace')
    const appearance = screen.getByRole('region', { name: 'Appearance' })
    expect(within(appearance).getByRole('radio', { name: /Light/ })).toHaveAttribute('aria-checked', 'true')
    await userEvent.click(within(appearance).getByRole('radio', { name: /Dark/ }))
    expect(getThemePreference()).toBe('dark')
  })

  it('Fields & Tags lists the V3 statuses and priorities and the project tags in use', async () => {
    renderSettings('fields')
    const defs = screen.getByRole('region', { name: 'Statuses and priorities' })
    for (const label of ['To Do', 'In Progress', 'Blocked', 'Done', 'Low', 'Normal', 'High', 'On Hold', 'Archived']) {
      expect(within(defs).getByText(label)).toBeInTheDocument()
    }
    expect(within(defs).queryByText('Overdue')).not.toBeInTheDocument()
    const tags = screen.getByRole('region', { name: 'Project tags in use' })
    expect(await within(tags).findByText('Sales')).toBeInTheDocument()
    expect(within(tags).getByText('Ops')).toBeInTheDocument()
  })

  it('counts tags, most used first', () => {
    expect(tagCounts([['b', 'a'], ['a'], undefined])).toEqual([
      { tag: 'a', count: 2 },
      { tag: 'b', count: 1 },
    ])
  })

  it('Notifications lists what is sent and has no fake toggles', () => {
    renderSettings('notifications')
    expect(screen.getByText('A task is assigned to you')).toBeInTheDocument()
    expect(screen.getByText('Someone mentions you')).toBeInTheDocument()
    expect(screen.queryByRole('checkbox')).not.toBeInTheDocument()
    expect(screen.queryByRole('switch')).not.toBeInTheDocument()
  })

  it('Integrations shows the unreleased connectors as Coming Soon, not as working', () => {
    renderSettings('integrations')
    for (const name of ['Google Sheets', 'Pabbly', 'Zoom', 'Zapier']) {
      const tile = screen.getByText(name).closest('li')!
      expect(tile).toHaveAttribute('aria-disabled', 'true')
      expect(within(tile).getByText('Coming Soon')).toBeInTheDocument()
      expect(within(tile).queryByRole('button')).not.toBeInTheDocument()
    }
  })

  it('Subscription & Usage shows the plan and opens the platform wallets', async () => {
    renderSettings('subscription')
    expect(await screen.findByText('Advanced')).toBeInTheDocument()
    expect(screen.getByText('31 Dec 2026')).toBeInTheDocument()
    expect(screen.getByRole('link', { name: /Manage Plan/ })).toHaveAttribute('href', 'https://workeasy360.com/plans')
    expect(screen.getAllByRole('link', { name: /Open on WorkEasy360/ })).toHaveLength(3)
    expect(screen.getByText('AI Credits')).toBeInTheDocument()
  })
})

describe('SettingsPage with a Workeasy360 sign-in', () => {
  beforeEach(() => {
    setIdentity(null)
    signInAs(ME, { orgId: ORG, email: 'ada@example.com', name: 'Ada Lovelace', workeasy360SignOut: true })
    setThemePreference('system')
    document.cookie = 'XSRF-TOKEN=tok%2F1; path=/'
  })
  afterEach(() => {
    setThemePreference('system')
    globalThis.fetch = originalFetch
    vi.mocked(navigateTo).mockClear()
    document.cookie = 'XSRF-TOKEN=; expires=Thu, 01 Jan 1970 00:00:00 GMT; path=/'
    resetSessionForTest()
  })

  it('Your profile shows the signed-in person and read-only ids with copy buttons; nothing is editable', async () => {
    const writeText = vi.fn(() => Promise.resolve())
    Object.defineProperty(navigator, 'clipboard', { value: { writeText }, configurable: true })
    renderSettings('workspace')
    const section = screen.getByRole('region', { name: 'Your profile' })
    expect(within(section).getByText('Ada Lovelace')).toBeInTheDocument()
    expect(within(section).getByText('ada@example.com')).toBeInTheDocument()
    expect(within(section).getByText(ME)).toBeInTheDocument()
    expect(within(section).getByText(ORG)).toBeInTheDocument()
    expect(within(section).queryByRole('textbox')).not.toBeInTheDocument()
    // The name, password and photo belong to the Workeasy360 account: nothing here calls the email-account endpoints.
    expect(within(section).queryByRole('button', { name: 'Edit name' })).not.toBeInTheDocument()
    expect(within(section).queryByRole('button', { name: 'Change password' })).not.toBeInTheDocument()
    expect(within(section).queryByRole('button', { name: /photo/i })).not.toBeInTheDocument()
    expect(section).not.toHaveTextContent('X-User-Id')
    await userEvent.click(within(section).getByRole('button', { name: 'Copy user ID' }))
    expect(writeText).toHaveBeenCalledWith(ME)
    expect(await within(section).findByText('Copied')).toBeInTheDocument()
    expect(screen.queryByRole('checkbox', { name: /notification/i })).not.toBeInTheDocument()
  })

  it('"Sign out of Easy Project" posts the logout with the CSRF token and ends the session locally', async () => {
    const fetchMock = mockLogoutFetch()
    renderSettings('workspace')
    const section = screen.getByRole('region', { name: 'Your profile' })
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
    renderSettings('workspace')
    const section = screen.getByRole('region', { name: 'Your profile' })
    await userEvent.click(within(section).getByRole('button', { name: 'Sign out of Easy Project and Workeasy360' }))
    await waitFor(() => expect(navigateTo).toHaveBeenCalledWith('/api/auth/workeasy360/logout'))
    expect((fetchMock.mock.calls[0] as [string, RequestInit])[1].method).toBe('POST')
    expect(getSessionState().status).toBe('unauthenticated')
  })

  it('offers only the Easy Project sign-out when Workeasy360 sign-out is not available', () => {
    signInAs(ME, { orgId: ORG, workeasy360SignOut: false })
    renderSettings('workspace')
    const section = screen.getByRole('region', { name: 'Your profile' })
    expect(within(section).getByRole('button', { name: 'Sign out of Easy Project' })).toBeInTheDocument()
    expect(within(section).queryByRole('button', { name: /and Workeasy360/ })).not.toBeInTheDocument()
  })

  it('stays signed in and shows the problem when the logout request fails', async () => {
    globalThis.fetch = vi.fn().mockRejectedValue(new TypeError('offline')) as unknown as typeof fetch
    renderSettings('workspace')
    const section = screen.getByRole('region', { name: 'Your profile' })
    await userEvent.click(within(section).getByRole('button', { name: 'Sign out of Easy Project' }))
    expect(await within(section).findByRole('alert')).toHaveTextContent(/could not reach the server/i)
    expect(getSessionState().status).toBe('authenticated')
  })

  it('an email session in the same browser takes precedence over the Workeasy360 one', () => {
    setIdentity({ userId: 'email-user', orgId: ORG, token: 'tok', name: 'Ben', email: 'ben@example.com' })
    renderSettings('workspace')
    const section = screen.getByRole('region', { name: 'Your profile' })
    expect(within(section).getByText('Ben')).toBeInTheDocument()
    expect(within(section).getByRole('button', { name: 'Change password' })).toBeInTheDocument()
    setIdentity(null)
  })
})
