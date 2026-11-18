import { describe, expect, it, vi, beforeEach, afterEach } from 'vitest'
import { render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import { SettingsPage, tagCounts, type SettingsSection } from './SettingsPage'
import { IdentityProvider } from '../../context/IdentityContext'
import { WorkspaceSettingsProvider } from '../../context/WorkspaceSettingsContext'
import { getIdentity, setIdentity } from '../../context/identityStore'
import { getThemePreference, setThemePreference } from '../../context/themeStore'
import { projectsApi } from '../../api/projects'

const ME = '11111111-2222-4333-8444-555555555555'
const ORG = '99999999-2222-4333-8444-555555555555'

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
      <IdentityProvider>
        <WorkspaceSettingsProvider>
          <SettingsPage section={section} />
        </WorkspaceSettingsProvider>
      </IdentityProvider>
    </MemoryRouter>,
  )
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
  afterEach(() => setThemePreference('system'))

  it('Workspace & Access shows the workspace, your profile without internal ids, and signs out', async () => {
    renderSettings('workspace')
    expect(screen.getByRole('heading', { name: 'Workspace & Access' })).toBeInTheDocument()
    expect(await screen.findByText('Acme Enterprises Pvt. Ltd.')).toBeInTheDocument()
    const profile = screen.getByRole('region', { name: 'Your profile' })
    expect(within(profile).getByText('Ada')).toBeInTheDocument()
    expect(within(profile).getByText('ada@example.com')).toBeInTheDocument()
    expect(within(profile).queryByText(ME)).not.toBeInTheDocument()
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
