import { describe, expect, it, vi, beforeEach } from 'vitest'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import { ANSWERS, HelpdeskPage, supportMailto, type HelpdeskSection } from './HelpdeskPage'
import { IdentityProvider } from '../../context/IdentityContext'
import { WorkspaceSettingsProvider } from '../../context/WorkspaceSettingsContext'
import { setIdentity } from '../../context/identityStore'
import { projectsApi } from '../../api/projects'
import { renderToStaticMarkup } from 'react-dom/server'

vi.mock('../../api/projects', () => ({ projectsApi: { list: vi.fn() } }))
vi.mock('../../api/workspace', async (importOriginal) => ({
  ...(await importOriginal<typeof import('../../api/workspace')>()),
  workspaceApi: {
    settings: vi.fn(() =>
      Promise.resolve({
        name: 'Acme',
        canEdit: true,
        plan: { name: 'Advanced', validUntil: null, manageUrl: 'https://workeasy360.com' },
        supportEmail: 'help@workeasy360.com',
      }),
    ),
    rename: vi.fn(),
  },
}))

function renderHelp(section: HelpdeskSection) {
  return render(
    <MemoryRouter>
      <IdentityProvider>
        <WorkspaceSettingsProvider>
          <HelpdeskPage section={section} />
        </WorkspaceSettingsProvider>
      </IdentityProvider>
    </MemoryRouter>,
  )
}

describe('HelpdeskPage', () => {
  beforeEach(() => {
    setIdentity({ userId: '11111111-2222-4333-8444-555555555555', orgId: '99999999-2222-4333-8444-555555555555', email: 'manoj@acme.example' })
    vi.mocked(projectsApi.list).mockReset().mockResolvedValue({
      content: [{ id: 'p1', name: 'Website Redesign', status: 'ACTIVE', priority: 'MEDIUM', ownerId: 'u', updatedAt: '2026-11-01T00:00:00Z' }],
      page: 0,
      size: 50,
      totalElements: 1,
      totalPages: 1,
    })
  })

  it('Raise Ticket never pretends to create a ticket: it emails support with product and workspace filled in', async () => {
    renderHelp('raise')
    expect(screen.getAllByText('Helpdesk not linked yet').length).toBeGreaterThan(0)
    expect(screen.getByRole('button', { name: /Email this to support/ })).toBeDisabled()
    await userEvent.type(screen.getByRole('textbox', { name: /Subject/ }), 'Cannot assign')
    await userEvent.type(screen.getByRole('textbox', { name: /What happened/ }), 'The list is empty')
    await screen.findByRole('option', { name: 'Website Redesign' })
    const send = screen.getByRole('link', { name: /Email this to support/ })
    const href = decodeURIComponent(send.getAttribute('href') ?? '')
    expect(href).toContain('mailto:help@workeasy360.com')
    expect(href).toContain('[Operations] Cannot assign')
    expect(href).toContain('Product: WorkEasy360 Operations')
    expect(href).toContain('Workspace: Acme')
    expect(screen.queryByText(/Ticket ID:/)).not.toBeInTheDocument()
  })

  it('builds the support email', () => {
    const href = supportMailto('s@x.com', { subject: 'Hi', description: 'Body', reference: 'Project "A"', workspace: 'W', email: 'me@x.com' })
    expect(decodeURIComponent(href)).toBe('mailto:s@x.com?subject=[Operations] Hi&body=Product: WorkEasy360 Operations\nWorkspace: W\nFrom: me@x.com\nReference: Project "A"\n\nBody')
  })

  it('My Tickets explains tickets appear once the Helpdesk is linked', () => {
    renderHelp('tickets')
    expect(screen.getByRole('heading', { name: 'No tickets to show' })).toBeInTheDocument()
  })

  it('Help Articles cover the V3 flows and can be searched', async () => {
    renderHelp('articles')
    expect(screen.getByRole('button', { name: /How do I invite members/ })).toBeInTheDocument()
    await userEvent.type(screen.getByRole('searchbox', { name: 'Search help articles' }), 'board')
    expect(screen.getByRole('button', { name: /track progress on the board/ })).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: /invite members/ })).not.toBeInTheDocument()
  })

  it('articles never mention removed features', () => {
    const text = ANSWERS.map((a) => `${a.q} ${a.keywords} ${renderToStaticMarkup(<MemoryRouter>{a.a}</MemoryRouter>)}`).join(' ').toLowerCase()
    for (const word of ['template', 'timesheet', 'calendar', 'gantt', 'phase', 'milestone', 'risk', 'issue', 'decision', 'dependenc', 'custom field', 'health', 'repeat', 'recurr', 'subtask', 'follower', 'log time', 'timer', 'chat channel', 'planning', 'cancelled']) {
      expect(text, word).not.toContain(word)
    }
  })
})
