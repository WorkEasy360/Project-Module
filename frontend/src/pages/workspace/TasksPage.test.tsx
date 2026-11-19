import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { render, screen } from '@testing-library/react'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { TasksPage } from './TasksPage'
import { ReportsPage } from './ReportsPage'
import { IdentityProvider } from '../../context/IdentityContext'
import { PeopleProvider } from '../../context/PeopleContext'
import { setIdentity } from '../../context/identityStore'
import { projectsApi } from '../../api/projects'
import { tasksApi } from '../../api/tasks'
import { todayIso } from '../../utils/format'
import type { Task } from '../../types/work'

vi.mock('../../api/projects', () => ({ projectsApi: { list: vi.fn() } }))
vi.mock('../../api/tasks', () => ({ tasksApi: { list: vi.fn(), update: vi.fn() } }))
vi.mock('../../api/collaboration', () => ({ activityApi: { list: vi.fn() } }))
vi.mock('../../api/workspace', () => ({
  workspaceApi: {
    people: vi.fn(() =>
      Promise.resolve([
        { id: 'me', organizationId: 'o', email: 'me@x', name: 'Manoj Kumar', workspaceRole: 'ADMIN' },
        { id: 'amit', organizationId: 'o', email: 'amit@x', name: 'Amit Singh', workspaceRole: 'MEMBER' },
      ]),
    ),
  },
}))

const today = todayIso()
const now = new Date().toISOString()
const task = (over: Partial<Task>): Task => ({
  id: Math.random().toString(36).slice(2), projectId: 'p1', name: 'Task', description: null, dueDate: null, assigneeId: null,
  status: 'TODO', archived: false, createdAt: now, updatedAt: now, version: 0, priority: 'MEDIUM', ...over,
})

function renderAt(path: string) {
  return render(
    <MemoryRouter initialEntries={[path]}>
      <IdentityProvider>
        <PeopleProvider>
          <Routes>
            <Route path="/tasks" element={<TasksPage scope="mine" />} />
            <Route path="/tasks/all" element={<TasksPage scope="all" />} />
            <Route path="/reports/projects" element={<ReportsPage view="projects" />} />
            <Route path="/reports/workload" element={<ReportsPage view="workload" />} />
          </Routes>
        </PeopleProvider>
      </IdentityProvider>
    </MemoryRouter>,
  )
}

describe('V3 Tasks and Reports pages', () => {
  beforeEach(() => {
    setIdentity({ userId: 'me', orgId: 'o', token: 't', name: 'Manoj Kumar', email: 'me@x' })
    vi.mocked(projectsApi.list).mockResolvedValue({
      content: [
        { id: 'p1', name: 'Website Redesign', status: 'ACTIVE', priority: 'MEDIUM', ownerId: 'me', updatedAt: now },
        { id: 'p2', name: 'Empty Project', status: 'ACTIVE', priority: 'MEDIUM', ownerId: 'me', updatedAt: now },
      ],
      page: 0, size: 100, totalElements: 2, totalPages: 1,
    })
    vi.mocked(tasksApi.list).mockImplementation(async (projectId) =>
      projectId === 'p1'
        ? [
            task({ name: 'My late task', assigneeId: 'me', dueDate: '2020-01-01' }),
            task({ name: 'My task today', assigneeId: 'me', dueDate: today }),
            task({ name: 'Amit task', assigneeId: 'amit' }),
            task({ name: 'Unowned task' }),
            task({ name: 'Finished task', assigneeId: 'me', status: 'COMPLETED' }),
          ]
        : [],
    )
  })
  afterEach(() => {
    setIdentity(null)
    vi.clearAllMocks()
  })

  it('My Tasks shows only my open work by default', async () => {
    renderAt('/tasks')
    expect(await screen.findByText('My late task')).toBeInTheDocument()
    expect(screen.getByText('My task today')).toBeInTheDocument()
    expect(screen.queryByText('Amit task')).not.toBeInTheDocument()
    expect(screen.queryByText('Finished task')).not.toBeInTheDocument()
  })

  it('a link like /tasks?view=overdue opens exactly the overdue list', async () => {
    renderAt('/tasks?view=overdue')
    expect(await screen.findByText('My late task')).toBeInTheDocument()
    expect(screen.queryByText('My task today')).not.toBeInTheDocument()
  })

  it('All Tasks can show unassigned work for managers', async () => {
    renderAt('/tasks/all?assignee=none')
    expect(await screen.findByText('Unowned task')).toBeInTheDocument()
    expect(screen.queryByText('Amit task')).not.toBeInTheDocument()
  })

  it('Project Progress works from task counts and marks a project with no tasks', async () => {
    renderAt('/reports/projects')
    expect(await screen.findByText('20%')).toBeInTheDocument()
    expect(screen.getByText('No tasks yet')).toBeInTheDocument()
  })

  it('Team Workload lists each person with their open work', async () => {
    renderAt('/reports/workload')
    expect(await screen.findByText('Amit Singh')).toBeInTheDocument()
    expect(screen.getByText('Manoj Kumar')).toBeInTheDocument()
  })
})
