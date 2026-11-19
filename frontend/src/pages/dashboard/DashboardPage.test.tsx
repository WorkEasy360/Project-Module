import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { render, screen, within } from '@testing-library/react'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { DashboardPage } from './DashboardPage'
import { IdentityProvider } from '../../context/IdentityContext'
import { PeopleProvider } from '../../context/PeopleContext'
import { setIdentity } from '../../context/identityStore'
import { resetSessionForTest, signInAs } from '../../test/session'
import { projectsApi } from '../../api/projects'
import { tasksApi } from '../../api/tasks'
import { activityApi } from '../../api/collaboration'
import { todayIso } from '../../utils/format'
import type { Task } from '../../types/work'

vi.mock('../../api/projects', () => ({ projectsApi: { list: vi.fn(), create: vi.fn() } }))
vi.mock('../../api/tasks', () => ({ tasksApi: { list: vi.fn(), create: vi.fn() } }))
vi.mock('../../api/collaboration', () => ({ activityApi: { list: vi.fn() } }))
vi.mock('../../api/workspace', () => ({
  workspaceApi: {
    people: vi.fn(() =>
      Promise.resolve([
        { id: 'me', organizationId: 'o', email: 'manoj@acme.example', name: 'Manoj Kumar', workspaceRole: 'ADMIN' },
        { id: 'amit', organizationId: 'o', email: 'amit@acme.example', name: 'Amit Singh', workspaceRole: 'MEMBER' },
      ]),
    ),
  },
}))

const today = todayIso()
const now = new Date().toISOString()

function task(over: Partial<Task>): Task {
  return {
    id: Math.random().toString(36).slice(2),
    projectId: 'p1',
    name: 'Task',
    description: null,
    dueDate: null,
    assigneeId: null,
    status: 'TODO',
    archived: false,
    createdAt: now,
    updatedAt: now,
    version: 0,
    priority: 'MEDIUM',
    ...over,
  }
}

function renderPage() {
  return render(
    <MemoryRouter initialEntries={['/dashboard']}>
      <IdentityProvider>
        <PeopleProvider>
          <Routes>
            <Route path="/dashboard" element={<DashboardPage />} />
          </Routes>
        </PeopleProvider>
      </IdentityProvider>
    </MemoryRouter>,
  )
}

describe('DashboardPage (V3 Home)', () => {
  beforeEach(() => {
    setIdentity({ userId: 'me', orgId: 'o', token: 't', name: 'Manoj Kumar', email: 'manoj@acme.example' })
    vi.mocked(projectsApi.list).mockResolvedValue({
      content: [
        { id: 'p1', name: 'Website Redesign', status: 'ACTIVE', priority: 'HIGH', ownerId: 'me', updatedAt: now, createdAt: now },
        { id: 'p2', name: 'Marketing Campaign', status: 'ON_HOLD', priority: 'LOW', ownerId: 'me', updatedAt: now, createdAt: now },
      ],
      page: 0,
      size: 100,
      totalElements: 2,
      totalPages: 1,
    })
    vi.mocked(tasksApi.list).mockImplementation(async (projectId) =>
      projectId === 'p1'
        ? [
            task({ name: 'Done one', status: 'COMPLETED', assigneeId: 'me' }),
            task({ name: 'Amit done', status: 'COMPLETED', assigneeId: 'amit' }),
            task({ name: 'Write copy', status: 'IN_PROGRESS', dueDate: today, assigneeId: 'me' }),
            task({ name: 'Fix banner', status: 'BLOCKED', dueDate: '2020-01-01', assigneeId: 'amit' }),
            task({ name: 'Nobody yet', status: 'TODO' }),
          ]
        : [],
    )
    vi.mocked(activityApi.list).mockImplementation(async (projectId) => ({
      content: projectId === 'p1' ? [{ id: 'a1', type: 'TASK_COMPLETED', actorType: 'HUMAN', actorId: 'me', summary: 'Task "Update Product Page" marked as Done', occurredAt: now }] : [],
      page: 0,
      size: 8,
      totalElements: 1,
      totalPages: 1,
    }))
  })
  afterEach(() => {
    setIdentity(null)
    resetSessionForTest()
    vi.clearAllMocks()
  })

  it('greets the person and puts their own work first', async () => {
    renderPage()
    expect(screen.getByRole('heading', { level: 1 })).toHaveTextContent(/Good (Morning|Afternoon|Evening), Manoj!/)
    const summary = screen.getByRole('region', { name: 'My work summary' })
    const tile = (label: string) => within(summary).getByText(label).closest('a')!
    await vi.waitFor(() => expect(tile('My Open Tasks').querySelector('.ops-tile-value')).toHaveTextContent('1'))
    expect(tile('Due Today').querySelector('.ops-tile-value')).toHaveTextContent('1')
    expect(tile('Overdue').querySelector('.ops-tile-value')).toHaveTextContent('0')
  })

  it('greets a Workeasy360 account that shares only an email by the part before @', () => {
    setIdentity(null)
    signInAs('me', { email: 'manoj.k@acme.example' })
    renderPage()
    expect(screen.getByRole('heading', { level: 1 })).toHaveTextContent(/Good (Morning|Afternoon|Evening), manoj\.k!/)
    expect(screen.getByRole('heading', { level: 1 })).not.toHaveTextContent('acme.example')
  })

  it('opens the matching filtered list from every count', async () => {
    renderPage()
    const summary = screen.getByRole('region', { name: 'My work summary' })
    const href = (label: string) => within(summary).getByText(label).closest('a')!.getAttribute('href')
    expect(href('My Open Tasks')).toBe('/tasks')
    expect(href('Due Today')).toBe('/tasks?view=today')
    expect(href('Overdue')).toBe('/tasks?view=overdue')
    expect(href('Upcoming')).toBe('/tasks?view=upcoming')
    // An administrator also sees the team overview within their scope.
    expect(await within(summary).findByText('Team Overdue')).toBeInTheDocument()
    expect(href('Team Overdue')).toBe('/tasks/all?view=overdue')
    expect(href('Unassigned')).toBe('/tasks/all?assignee=none')
    await vi.waitFor(() => expect(within(summary).getByText('Team Overdue').closest('a')!.querySelector('.ops-tile-value')).toHaveTextContent('1'))
    expect(within(summary).getByText('Unassigned').closest('a')!.querySelector('.ops-tile-value')).toHaveTextContent('1')
  })

  it('works out project progress from tasks and marks projects with no tasks', async () => {
    renderPage()
    expect(await screen.findByText('40%')).toBeInTheDocument()
    expect(screen.getByText('No tasks yet')).toBeInTheDocument()
  })

  it('keeps charts off Home and offers the three V3 quick actions', async () => {
    renderPage()
    await screen.findByText('Write copy')
    expect(screen.queryByText('Tasks by Status')).not.toBeInTheDocument()
    expect(screen.queryByText(/Tasks Overview/)).not.toBeInTheDocument()
    expect(screen.queryByRole('combobox', { name: 'Period' })).not.toBeInTheDocument()
    for (const label of ['Add Task', 'Create Project', 'Invite Member']) {
      expect(screen.getByRole('button', { name: label })).toBeInTheDocument()
    }
    expect(screen.queryByRole('button', { name: /Automation Rule/ })).not.toBeInTheDocument()
  })
})
