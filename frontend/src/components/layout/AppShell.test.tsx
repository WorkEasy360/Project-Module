import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { render, screen, fireEvent, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import { SidebarProvider } from '../../context/SidebarContext'
import { setCollapsed } from '../../context/sidebarStore'
import { SessionProvider } from '../../context/SessionContext'
import { IdentityProvider } from '../../context/IdentityContext'
import { setIdentity } from '../../context/identityStore'
import { resetSessionForTest, signInAs } from '../../test/session'
import { WorkspaceSettingsProvider } from '../../context/WorkspaceSettingsContext'
import { OPEN_ASSISTANT_EVENT } from '../assistant/AssistantPanel'
import { Sidebar } from './Sidebar'
import { Header } from './Header'

vi.mock('../../api/workspace', () => ({
  workspaceApi: {
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

/** Stubs window.matchMedia so `useMediaQuery('(max-width: 768px)')` resolves as desired. */
function mockViewport(isMobile: boolean) {
  window.matchMedia = ((query: string) => ({
    matches: query.includes('768px') ? isMobile : false,
    media: query,
    onchange: null,
    addListener: () => {},
    removeListener: () => {},
    addEventListener: () => {},
    removeEventListener: () => {},
    dispatchEvent: () => false,
  })) as typeof window.matchMedia
}

function renderShell(path = '/dashboard') {
  return render(
    <MemoryRouter initialEntries={[path]}>
      <SessionProvider autoLoad={false}>
        <IdentityProvider>
          <WorkspaceSettingsProvider>
            <SidebarProvider>
              <Header />
              <Sidebar />
            </SidebarProvider>
          </WorkspaceSettingsProvider>
        </IdentityProvider>
      </SessionProvider>
    </MemoryRouter>,
  )
}

function sidebar(container: HTMLElement) {
  return within(container.querySelector('aside.sidebar') as HTMLElement)
}

describe('App shell — WorkEasy360 Operations sidebar and header', () => {
  beforeEach(() => {
    setCollapsed(false)
    mockViewport(false)
    signInAs('11111111-2222-4333-8444-555555555555', { name: 'Ada Lovelace' })
  })

  afterEach(() => {
    setCollapsed(false)
    setIdentity(null)
    resetSessionForTest()
    vi.restoreAllMocks()
  })

  it('the account control follows the sign-in: Workeasy360 offers its two sign-outs, an email account its own', async () => {
    mockViewport(false)
    const user = userEvent.setup()
    const first = renderShell()
    await user.click(screen.getByRole('button', { name: /ada lovelace/i }))
    expect(screen.getByRole('button', { name: 'Sign out of Easy Project' })).toBeInTheDocument()
    first.unmount()

    setIdentity({ userId: 'email-user', orgId: 'org-1', token: 'tok', name: 'Ben Email', email: 'ben@example.com' })
    renderShell()
    await user.click(screen.getByRole('button', { name: /ben email/i }))
    expect(screen.getByText('ben@example.com')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: /^sign out$/i })).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Sign out of Easy Project' })).not.toBeInTheDocument()
  })

  it('lists the sections in the design order, with Settings below a divider', () => {
    const { container } = renderShell()
    const labels = [...container.querySelectorAll('aside.sidebar .sidebar-link-label')].map((el) => el.textContent)
    expect(labels).toEqual(['Home', 'Projects', 'Tasks', 'Team', 'Automation', 'Reports', 'Helpdesk', 'Settings'])
    expect(container.querySelector('aside.sidebar .sidebar-divider')).toBeInTheDocument()
    expect(sidebar(container).getByText('Operations')).toBeInTheDocument()
  })

  it('highlights Home on the home page', () => {
    const { container } = renderShell('/dashboard')
    expect(sidebar(container).getByRole('link', { name: 'Home' })).toHaveClass('active')
    expect(sidebar(container).getByRole('button', { name: /tasks/i })).toHaveAttribute('aria-expanded', 'false')
  })

  it('opens Projects on a project page, with Active Projects highlighted', () => {
    const { container } = renderShell('/projects/abc/tasks')
    const s = sidebar(container)
    expect(s.getByRole('button', { name: /projects/i })).toHaveAttribute('aria-expanded', 'true')
    expect(s.getByRole('link', { name: 'Active Projects' })).toHaveClass('active')
    expect(s.getByRole('link', { name: 'Archived Projects' })).not.toHaveClass('active')
  })

  it('highlights Archived Projects on its page', () => {
    const { container } = renderShell('/projects/archived')
    expect(sidebar(container).getByRole('link', { name: 'Archived Projects' })).toHaveClass('active')
    expect(sidebar(container).getByRole('link', { name: 'Active Projects' })).not.toHaveClass('active')
  })

  it('a closed group opens to show its pages', async () => {
    const { container } = renderShell('/dashboard')
    const s = sidebar(container)
    expect(s.queryByRole('link', { name: 'Team Workload' })).not.toBeInTheDocument()
    await userEvent.click(s.getByRole('button', { name: /reports/i }))
    expect(s.getByRole('link', { name: 'Task Progress' })).toHaveAttribute('href', '/reports')
    expect(s.getByRole('link', { name: 'Project Progress' })).toHaveAttribute('href', '/reports/projects')
    expect(s.getByRole('link', { name: 'Team Workload' })).toHaveAttribute('href', '/reports/workload')
  })

  it('has the V3 pages under each section, and nothing removed', async () => {
    const { container } = renderShell('/dashboard')
    const s = sidebar(container)
    const open = async (name: RegExp) => userEvent.click(s.getByRole('button', { name }))
    await open(/^tasks/i)
    expect(s.getByRole('link', { name: 'My Tasks' })).toHaveAttribute('href', '/tasks')
    expect(s.getByRole('link', { name: 'All Tasks' })).toHaveAttribute('href', '/tasks/all')
    await open(/^team/i)
    for (const name of ['Members', 'Teams', 'Invitations']) expect(s.getByRole('link', { name })).toBeInTheDocument()
    await open(/^automation/i)
    expect(s.getByRole('link', { name: 'Rules' })).toHaveAttribute('href', '/automations')
    expect(s.getByRole('link', { name: 'Run History' })).toHaveAttribute('href', '/automations/runs')
    await open(/^helpdesk/i)
    for (const name of ['Raise Ticket', 'My Tickets', 'Help Articles']) expect(s.getByRole('link', { name })).toBeInTheDocument()
    await open(/^settings/i)
    for (const name of ['Workspace & Access', 'Fields & Tags', 'Notifications', 'Integrations', 'Subscription & Usage']) {
      expect(s.getByRole('link', { name })).toBeInTheDocument()
    }
    for (const gone of ['Templates', 'Timesheet', 'Calendar']) expect(s.queryByText(gone)).not.toBeInTheDocument()
  })

  it('shows the workspace name in the header and the plan in the sidebar', async () => {
    const { container } = renderShell()
    expect(await screen.findByRole('button', { name: /acme enterprises pvt\. ltd\./i })).toBeInTheDocument()
    expect(sidebar(container).getByText('Advanced')).toBeInTheDocument()
    expect(sidebar(container).getByText('Valid till 31 Dec 2026')).toBeInTheDocument()
    expect(sidebar(container).getByRole('link', { name: /manage plan/i })).toHaveAttribute('href', 'https://workeasy360.com/plans')
  })

  it('the AI Assistant button opens the assistant, and help goes to the Helpdesk', async () => {
    const opened = vi.fn()
    window.addEventListener(OPEN_ASSISTANT_EVENT, opened)
    renderShell()
    await userEvent.click(screen.getByRole('button', { name: /ai assistant/i }))
    expect(opened).toHaveBeenCalled()
    window.removeEventListener(OPEN_ASSISTANT_EVENT, opened)
    expect(screen.getByRole('link', { name: /help and support/i })).toHaveAttribute('href', '/helpdesk')
  })

  it('search opens from the header icon', async () => {
    renderShell()
    expect(screen.queryByRole('searchbox')).not.toBeInTheDocument()
    await userEvent.click(screen.getByRole('button', { name: 'Search' }))
    expect(screen.getByRole('searchbox', { name: /search projects and tasks/i })).toBeInTheDocument()
  })

  it('when collapsed, shows icons only', () => {
    setCollapsed(true)
    const { container } = renderShell()
    expect(container.querySelector('aside.sidebar')).toHaveClass('collapsed')
    expect(container.querySelectorAll('aside.sidebar .sidebar-link-label')).toHaveLength(0)
  })

  it('the < button shrinks the navigation to icons, and > expands it again', () => {
    setCollapsed(false)
    const { container } = renderShell()
    const aside = container.querySelector('aside.sidebar')
    fireEvent.click(screen.getByRole('button', { name: 'Shrink navigation' }))
    expect(aside).toHaveClass('collapsed')
    fireEvent.click(screen.getByRole('button', { name: 'Expand navigation' }))
    expect(aside).not.toHaveClass('collapsed')
  })

  it('mobile: the menu button opens the drawer, and choosing a page closes it', () => {
    mockViewport(true)
    const { container } = renderShell()
    const aside = container.querySelector('aside.sidebar')
    expect(aside).not.toHaveClass('open')
    fireEvent.click(screen.getByRole('button', { name: /open navigation/i }))
    expect(aside).toHaveClass('open')
    expect(container.querySelector('.sidebar-backdrop')).toBeInTheDocument()
    fireEvent.click(sidebar(container).getByRole('link', { name: 'Home' }))
    expect(aside).not.toHaveClass('open')
  })
})
