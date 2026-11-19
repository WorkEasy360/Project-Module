import { describe, expect, it, vi, beforeEach } from 'vitest'
import { render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import { AutomationPage } from './AutomationPage'
import { IdentityProvider } from '../../context/IdentityContext'
import { setIdentity } from '../../context/identityStore'
import { projectsApi } from '../../api/projects'
import { automationsApi } from '../../api/automations'
import { membersApi } from '../../api/members'
import type { ProjectAutomation } from '../../types/automation'

vi.mock('../../api/projects', () => ({ projectsApi: { list: vi.fn() } }))
vi.mock('../../api/members', () => ({ membersApi: { list: vi.fn() } }))
vi.mock('../../api/automations', () => ({
  automationsApi: { list: vi.fn(), create: vi.fn(), update: vi.fn(), archive: vi.fn(), runs: vi.fn() },
}))

const ME = '11111111-2222-4333-8444-555555555555'
const rule = (over: Partial<ProjectAutomation>): ProjectAutomation => ({
  id: 'r1',
  projectId: 'p1',
  name: 'Tell owner when done',
  description: null,
  triggerEvent: 'task.completed',
  actionType: 'NOTIFY',
  actionRecipientId: ME,
  actionMessage: 'Done!',
  enabled: true,
  archived: false,
  createdAt: '2026-11-01T00:00:00Z',
  updatedAt: '2026-11-01T00:00:00Z',
  version: 3,
  ...over,
})

function renderPage(view: 'rules' | 'runs') {
  return render(
    <MemoryRouter>
      <IdentityProvider>
        <AutomationPage view={view} />
      </IdentityProvider>
    </MemoryRouter>,
  )
}

describe('AutomationPage', () => {
  beforeEach(() => {
    setIdentity({ userId: ME, orgId: '99999999-2222-4333-8444-555555555555' })
    vi.mocked(projectsApi.list).mockReset().mockResolvedValue({
      content: [{ id: 'p1', name: 'Website Redesign', status: 'ACTIVE', priority: 'MEDIUM', ownerId: ME, updatedAt: '2026-11-01T00:00:00Z' }],
      page: 0,
      size: 50,
      totalElements: 1,
      totalPages: 1,
    })
    vi.mocked(automationsApi.list).mockReset().mockResolvedValue([
      rule({}),
      rule({ id: 'r2', name: 'Assigned ping', triggerEvent: 'task.assigned', enabled: false }),
    ])
    vi.mocked(automationsApi.update).mockReset().mockResolvedValue(rule({}))
    vi.mocked(automationsApi.create).mockReset().mockResolvedValue(rule({ id: 'r3', version: 0 }))
    vi.mocked(automationsApi.runs).mockReset().mockImplementation((id: string) =>
      Promise.resolve(
        id === 'r1'
          ? [
              { id: 'run1', automationId: 'r1', projectId: 'p1', triggerEvent: 'task.completed', status: 'SUCCEEDED', errorMessage: null, executedAt: '2026-11-10T09:00:00Z' },
              { id: 'run2', automationId: 'r1', projectId: 'p1', triggerEvent: 'task.completed', status: 'FAILED', errorMessage: 'The person to notify has left the project.', executedAt: '2026-11-12T09:00:00Z' },
            ]
          : [],
      ),
    )
    vi.mocked(membersApi.list).mockReset().mockResolvedValue([
      { id: 'm1', projectId: 'p1', userId: ME, role: 'OWNER', joinedAt: '2026-11-01T00:00:00Z', version: 0 },
    ])
  })

  it('lists rules across projects in plain words, and turns a rule on', async () => {
    renderPage('rules')
    const row = (await screen.findByText('Assigned ping')).closest('tr')!
    expect(within(row).getByText('A task is assigned')).toBeInTheDocument()
    expect(within(row).getByRole('link', { name: 'Website Redesign' })).toBeInTheDocument()
    const sw = within(row).getByRole('switch')
    expect(sw).toHaveAttribute('aria-checked', 'false')
    await userEvent.click(sw)
    expect(automationsApi.update).toHaveBeenCalledWith('r2', { enabled: true, version: 3 })
  })

  it('creates a rule with the guided form: project, when, notify, message', async () => {
    renderPage('rules')
    await screen.findByText('Assigned ping')
    await userEvent.click(screen.getByRole('button', { name: /New Rule/ }))
    const dialog = screen.getByRole('dialog')
    await userEvent.type(within(dialog).getByLabelText(/Rule name/), 'Overdue alert')
    await userEvent.selectOptions(within(dialog).getByLabelText(/This happens/), 'task.blocked')
    expect(within(dialog).queryByRole('option', { name: /milestone|risk|phase|dependency/i })).not.toBeInTheDocument()
    await userEvent.selectOptions(await within(dialog).findByLabelText('Notify'), ME)
    await userEvent.type(within(dialog).getByLabelText(/Message/), 'Please check')
    await userEvent.click(within(dialog).getByRole('button', { name: 'Create Rule' }))
    expect(automationsApi.create).toHaveBeenCalledWith('p1', {
      name: 'Overdue alert',
      triggerEvent: 'task.blocked',
      actionType: 'NOTIFY',
      actionRecipientId: ME,
      actionMessage: 'Please check',
    })
  })

  it('Run History shows every run newest first with the readable reason for a failure', async () => {
    renderPage('runs')
    const failed = (await screen.findByText('The person to notify has left the project.')).closest('tr')!
    expect(within(failed).getByText('Failed')).toBeInTheDocument()
    const rows = screen.getAllByRole('row').slice(1)
    expect(rows[0]).toHaveTextContent('Failed')
    expect(rows[1]).toHaveTextContent('Succeeded')
    await userEvent.selectOptions(screen.getByRole('combobox', { name: 'Result' }), 'SUCCEEDED')
    expect(screen.queryByText('The person to notify has left the project.')).not.toBeInTheDocument()
  })
})
