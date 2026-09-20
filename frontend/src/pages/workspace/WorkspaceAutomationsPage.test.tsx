import { describe, expect, it, vi, beforeEach } from 'vitest'
import { render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import { WorkspaceAutomationsPage } from './WorkspaceAutomationsPage'
import { IdentityProvider } from '../../context/IdentityContext'
import { setIdentity } from '../../context/identityStore'
import { projectsApi } from '../../api/projects'
import { automationsApi } from '../../api/automations'
import type { AutomationRun, ProjectAutomation } from '../../types/automation'

vi.mock('../../api/projects', () => ({ projectsApi: { list: vi.fn() } }))
vi.mock('../../api/automations', () => ({ automationsApi: { list: vi.fn(), runs: vi.fn() } }))

const projects = [
  { id: 'p1', name: 'Website Redesign', status: 'ACTIVE', priority: 'HIGH', ownerId: 'u', updatedAt: '2026-09-10T00:00:00Z' },
  { id: 'p2', name: 'Mobile App', status: 'PLANNING', priority: 'MEDIUM', ownerId: 'u', updatedAt: '2026-09-09T00:00:00Z' },
  { id: 'p3', name: 'Data Migration', status: 'ON_HOLD', priority: 'LOW', ownerId: 'u', updatedAt: '2026-09-08T00:00:00Z' },
] as const
const a = (over: Partial<ProjectAutomation>): ProjectAutomation => ({
  id: 'x', projectId: 'p1', name: 'x', description: null, triggerEvent: 'task.overdue', actionType: 'NOTIFY',
  actionRecipientId: null, actionChannelReference: null, actionMessage: 'Heads up', enabled: true, archived: false,
  createdAt: '2026-09-01T00:00:00Z', updatedAt: '2026-09-02T00:00:00Z', version: 0, ...over,
})
const rulesById: Record<string, ProjectAutomation[]> = {
  p1: [
    a({ id: 'a1', name: 'Notify on overdue', description: 'Pings the owner', actionRecipientId: 'user-42' }),
    a({ id: 'a2', name: 'Chat on complete', triggerEvent: 'task.completed', actionType: 'CHAT_MESSAGE', actionChannelReference: '#ops', enabled: false }),
  ],
  p2: [a({ id: 'a3', projectId: 'p2', name: 'Risk alert', triggerEvent: 'risk.created' })],
  p3: [],
}
const runsA1: AutomationRun[] = [
  { id: 'r2', automationId: 'a1', projectId: 'p1', triggerEvent: 'task.overdue', status: 'FAILED', errorMessage: 'Recipient unreachable', executedAt: '2026-09-17T09:00:00Z' },
  { id: 'r1', automationId: 'a1', projectId: 'p1', triggerEvent: 'task.overdue', status: 'SUCCEEDED', errorMessage: null, executedAt: '2026-09-18T10:00:00Z' },
]

function page(totalElements = 3) {
  return { content: [...projects], page: 0, size: 50, totalElements, totalPages: 1 }
}
function renderAutomations() {
  return render(
    <MemoryRouter>
      <IdentityProvider>
        <WorkspaceAutomationsPage />
      </IdentityProvider>
    </MemoryRouter>,
  )
}
function rowFor(name: string) {
  return screen.getByText(name).closest('tr')!
}
function ruleRows() {
  return within(screen.getByRole('table'))
    .getAllByRole('row')
    .filter((r) => r.querySelector('td.ws-name'))
}

describe('WorkspaceAutomationsPage', () => {
  beforeEach(() => {
    setIdentity({ userId: '11111111-2222-4333-8444-555555555555', orgId: '99999999-2222-4333-8444-555555555555' })
    vi.mocked(projectsApi.list).mockReset()
    vi.mocked(automationsApi.list).mockReset()
    vi.mocked(automationsApi.runs).mockReset()
    vi.mocked(projectsApi.list).mockImplementation(() => Promise.resolve(page()))
    vi.mocked(automationsApi.list).mockImplementation((id: string) => Promise.resolve(rulesById[id]))
    vi.mocked(automationsApi.runs).mockImplementation((id: string) =>
      id === 'a1' ? Promise.resolve(runsA1) : Promise.reject(new Error('boom')),
    )
  })

  it('lists every rule across projects with humanized trigger, action, state and project link — without fetching runs', async () => {
    renderAutomations()
    await screen.findByRole('table')
    expect(ruleRows()).toHaveLength(3)
    const row = rowFor('Notify on overdue')
    expect(within(row).getByRole('link', { name: 'Website Redesign' })).toHaveAttribute('href', '/projects/p1/automations')
    expect(row).toHaveTextContent('Task Overdue')
    expect(row).toHaveTextContent('Notify')
    expect(row).toHaveTextContent('Enabled')
    expect(within(row).getByRole('button', { name: 'Load runs' })).toBeInTheDocument()
    const disabled = rowFor('Chat on complete')
    expect(disabled).toHaveTextContent('Chat Message')
    expect(disabled).toHaveTextContent('Disabled')
    const chips = screen.getByLabelText('Automation summary')
    expect(chips).toHaveTextContent('3 rules')
    expect(chips).toHaveTextContent('2 enabled')
    expect(chips).toHaveTextContent('2 projects with rules')
    expect(vi.mocked(automationsApi.list)).toHaveBeenCalledTimes(3)
    expect(vi.mocked(automationsApi.runs)).not.toHaveBeenCalled()
  })

  it('loads run history lazily when a row is expanded, showing the latest run and links to the run history', async () => {
    renderAutomations()
    await screen.findByRole('table')
    await userEvent.click(within(rowFor('Notify on overdue')).getByRole('button', { name: 'Details' }))
    expect(vi.mocked(automationsApi.runs)).toHaveBeenCalledTimes(1)
    expect(vi.mocked(automationsApi.runs)).toHaveBeenCalledWith('a1')
    const row = rowFor('Notify on overdue')
    // Latest by executedAt is the SUCCEEDED run, not the first array element.
    await within(row).findByText('Succeeded')
    expect(within(row).getByRole('link', { name: /2026|Sep/ })).toHaveAttribute('href', '/projects/p1/automations/a1/runs')
    const details = document.getElementById('auto-details-a1')!
    expect(details).toHaveTextContent('Pings the owner')
    expect(details).toHaveTextContent('user-42')
    expect(details).toHaveTextContent('Recipient unreachable')
    expect(within(details).getByRole('link', { name: /Run history/ })).toHaveAttribute('href', '/projects/p1/automations/a1/runs')
    expect(within(details).getByRole('link', { name: /Edit on the project/ })).toHaveAttribute('href', '/projects/p1/automations')
    // Collapsing and re-expanding does not refetch.
    await userEvent.click(within(row).getByRole('button', { name: 'Hide' }))
    await userEvent.click(within(row).getByRole('button', { name: 'Details' }))
    expect(vi.mocked(automationsApi.runs)).toHaveBeenCalledTimes(1)

    // A failing runs call is reported on that row only, with a retry.
    await userEvent.click(within(rowFor('Risk alert')).getByRole('button', { name: 'Load runs' }))
    await within(rowFor('Risk alert')).findByText('Failed to load')
    expect(within(rowFor('Risk alert')).getByRole('button', { name: 'Retry' })).toBeInTheDocument()
    expect(rowFor('Notify on overdue')).toHaveTextContent('Succeeded')
  })

  it('filters by project, trigger and enabled state', async () => {
    renderAutomations()
    await screen.findByRole('table')
    await userEvent.selectOptions(screen.getByLabelText('Project'), 'p1')
    expect(ruleRows()).toHaveLength(2)
    expect(screen.getByText('Showing 2 of 3')).toBeInTheDocument()
    await userEvent.selectOptions(screen.getByLabelText('State'), 'enabled')
    expect(ruleRows()).toHaveLength(1)
    expect(ruleRows()[0]).toHaveTextContent('Notify on overdue')
    await userEvent.click(screen.getByRole('button', { name: 'Reset filters' }))
    await userEvent.selectOptions(screen.getByLabelText('Trigger'), 'risk.created')
    expect(ruleRows()).toHaveLength(1)
    expect(ruleRows()[0]).toHaveTextContent('Risk alert')
    await userEvent.selectOptions(screen.getByLabelText('Trigger'), 'project.archived')
    expect(screen.getByText('No rules match these filters.')).toBeInTheDocument()
  })

  it('keeps the table when one project fails, with Retry, and notes the page limit', async () => {
    vi.mocked(projectsApi.list).mockImplementation(() => Promise.resolve(page(60)))
    vi.mocked(automationsApi.list).mockImplementation((id: string) =>
      id === 'p2' ? Promise.reject(new Error('boom')) : Promise.resolve(rulesById[id]),
    )
    renderAutomations()
    await screen.findByRole('table')
    expect(ruleRows()).toHaveLength(2)
    const alert = screen.getByRole('alert')
    expect(within(alert).getByRole('link', { name: 'Mobile App' })).toHaveAttribute('href', '/projects/p2')
    expect(screen.getByRole('note')).toHaveTextContent('out of 60')
    vi.mocked(automationsApi.list).mockImplementation((id: string) => Promise.resolve(rulesById[id]))
    await userEvent.click(within(alert).getByRole('button', { name: 'Retry' }))
    await waitFor(() => expect(ruleRows()).toHaveLength(3))
  })

  it('shows an empty state when no project has rules', async () => {
    vi.mocked(automationsApi.list).mockImplementation(() => Promise.resolve([]))
    renderAutomations()
    expect(await screen.findByText('No automation rules yet')).toBeInTheDocument()
    expect(screen.queryByRole('table')).not.toBeInTheDocument()
  })
})
