import { describe, expect, it, vi, beforeEach, afterEach } from 'vitest'
import { render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import { MyTasksPage } from './MyTasksPage'
import { IdentityProvider } from '../../context/IdentityContext'
import { setIdentity } from '../../context/identityStore'
import { projectsApi } from '../../api/projects'
import { tasksApi } from '../../api/tasks'
import type { Task } from '../../types/work'

vi.mock('../../api/projects', () => ({ projectsApi: { list: vi.fn() } }))
vi.mock('../../api/tasks', () => ({ tasksApi: { list: vi.fn(), update: vi.fn() } }))

const ME = '11111111-2222-4333-8444-555555555555'
const projects = [
  { id: 'p1', name: 'Website Redesign', status: 'ACTIVE', priority: 'HIGH', ownerId: 'u', updatedAt: '2026-09-10T00:00:00Z' },
  { id: 'p2', name: 'Mobile App', status: 'PLANNING', priority: 'MEDIUM', ownerId: 'u', updatedAt: '2026-09-09T00:00:00Z' },
] as const
const t = (over: Partial<Task>): Task => ({
  id: 'x', projectId: 'p1', name: 'x', description: null, dueDate: null, assigneeId: null, status: 'TODO', archived: false, createdAt: '', updatedAt: '', version: 0, ...over,
})
const tasksById: Record<string, Task[]> = {
  p1: [
    t({ id: 't1', name: 'Fix authentication bug', dueDate: '2026-09-10', assigneeId: ME }),
    t({ id: 't2', name: 'Write docs', status: 'COMPLETED', assigneeId: ME }),
    t({ id: 't5', name: 'Design review', dueDate: '2026-09-19', assigneeId: ME, version: 3 }),
    t({ id: 't7', name: 'Archived thing', assigneeId: ME, archived: true }),
  ],
  p2: [
    t({ id: 't3', projectId: 'p2', name: 'Plan sprint', dueDate: '2026-09-22', assigneeId: ME }),
    t({ id: 't4', projectId: 'p2', name: 'Migrate schema', status: 'BLOCKED', dueDate: '2026-09-15', assigneeId: 'someone-else-id' }),
    t({ id: 't6', projectId: 'p2', name: 'Unassigned chore' }),
  ],
}

function page(totalElements = 2) {
  return { content: [...projects], page: 0, size: 50, totalElements, totalPages: 1 }
}
function renderPage() {
  return render(
    <MemoryRouter>
      <IdentityProvider>
        <MyTasksPage />
      </IdentityProvider>
    </MemoryRouter>,
  )
}
const rows = () => screen.getAllByRole('row').filter((r) => r.classList.contains('mt-row'))
const summary = () => screen.getByLabelText('Task summary')
const chip = (label: string) => within(summary()).getByText(label).previousSibling

describe('MyTasksPage', () => {
  beforeEach(() => {
    vi.useFakeTimers({ toFake: ['Date'] })
    vi.setSystemTime(new Date(2026, 8, 19, 9))
    setIdentity({ userId: ME, orgId: '99999999-2222-4333-8444-555555555555' })
    vi.mocked(projectsApi.list).mockReset()
    vi.mocked(tasksApi.list).mockReset()
    vi.mocked(tasksApi.update).mockReset()
    vi.mocked(projectsApi.list).mockImplementation(() => Promise.resolve(page()))
    vi.mocked(tasksApi.list).mockImplementation((id: string) => Promise.resolve(tasksById[id] ?? []))
  })
  afterEach(() => vi.useRealTimers())

  it('loads every project\'s tasks and shows only mine (open) by default, with honest chips and a past-due marker', async () => {
    renderPage()
    expect(await screen.findByRole('link', { name: 'Fix authentication bug' })).toHaveAttribute('href', '/projects/p1/tasks/t1')
    expect(projectsApi.list).toHaveBeenCalledWith(0, 50, 'updatedAt,DESC')
    expect(tasksApi.list).toHaveBeenCalledWith('p1', { archived: false })
    expect(tasksApi.list).toHaveBeenCalledWith('p2', { archived: false })

    expect(rows()).toHaveLength(3)
    expect(screen.queryByText('Write docs')).not.toBeInTheDocument() // completed hidden
    expect(screen.queryByText('Migrate schema')).not.toBeInTheDocument() // not mine
    expect(screen.queryByText('Archived thing')).not.toBeInTheDocument()
    expect(screen.getByText('3 tasks assigned to you across 2 projects')).toBeInTheDocument()

    // Sorted by due date ascending: past-due first, with the stored status kept and a derived marker.
    const first = rows()[0]
    expect(first).toHaveTextContent('Fix authentication bug')
    expect(within(first).getByText('Todo', { selector: '.badge' })).toBeInTheDocument()
    expect(within(first).getByText('past due')).toBeInTheDocument()
    expect(within(first).queryByText('Overdue', { selector: '.badge' })).not.toBeInTheDocument()
    expect(within(first).getByRole('link', { name: /Website Redesign/ })).toHaveAttribute('href', '/projects/p1/overview')
    expect(within(first).getByText('You')).toBeInTheDocument()
    expect(rows()[1]).toHaveTextContent('Today')

    expect(chip('Assigned to me')).toHaveTextContent('3')
    expect(chip('Overdue')).toHaveTextContent('1')
    expect(chip('Due this week')).toHaveTextContent('2')
    expect(chip('Blocked')).toHaveTextContent('0')
  })

  it('toggles: "Assigned to me" off shows everyone\'s open tasks; "Show completed" reveals completed ones', async () => {
    renderPage()
    await screen.findByRole('link', { name: 'Fix authentication bug' })
    await userEvent.click(screen.getByRole('checkbox', { name: 'Assigned to me' }))
    expect(rows()).toHaveLength(5)
    expect(chip('Blocked')).toHaveTextContent('1')
    const blocked = rows().find((r) => r.textContent?.includes('Migrate schema'))!
    // Blocked AND past its date: stored status shown, plus the secondary marker — never relabelled.
    expect(within(blocked).getByText('Blocked', { selector: '.badge' })).toBeInTheDocument()
    expect(within(blocked).getByText('past due')).toBeInTheDocument()
    expect(within(blocked).queryByText('Overdue', { selector: '.badge' })).not.toBeInTheDocument()
    expect(blocked).toHaveTextContent('someone-…')
    const chore = rows().find((r) => r.textContent?.includes('Unassigned chore'))!
    expect(chore).toHaveTextContent('Unassigned')
    expect(chore).toHaveTextContent('No due date')

    await userEvent.click(screen.getByRole('checkbox', { name: 'Show completed' }))
    expect(rows()).toHaveLength(6)
    expect(screen.getByText('Write docs')).toBeInTheDocument()
  })

  it('search, project and status filters work and can be reset; chips act as quick focus filters', async () => {
    renderPage()
    await screen.findByRole('link', { name: 'Fix authentication bug' })
    const bar = screen.getByRole('search', { name: 'Filter tasks' })

    await userEvent.type(within(bar).getByRole('searchbox'), 'sprint')
    expect(rows()).toHaveLength(1)
    expect(rows()[0]).toHaveTextContent('Plan sprint')
    await userEvent.click(within(bar).getByRole('button', { name: 'Clear search' }))
    expect(rows()).toHaveLength(3)

    await userEvent.selectOptions(within(bar).getByLabelText('Project'), 'p1')
    expect(rows()).toHaveLength(2)
    await userEvent.selectOptions(within(bar).getByLabelText('Status'), 'BLOCKED')
    expect(screen.getByText('No tasks match these filters')).toBeInTheDocument()
    // One reset lives in the active-filter bar and one in the empty state; either clears everything.
    await userEvent.click(screen.getAllByRole('button', { name: /Reset filters/ })[0])
    expect(rows()).toHaveLength(3)

    const overdue = within(summary()).getByRole('button', { name: /Overdue/ })
    await userEvent.click(overdue)
    expect(overdue).toHaveAttribute('aria-pressed', 'true')
    expect(rows()).toHaveLength(1)
    expect(rows()[0]).toHaveTextContent('Fix authentication bug')
    await userEvent.click(overdue)
    expect(rows()).toHaveLength(3)
  })

  it('sorts by due date (both ways), name and project', async () => {
    renderPage()
    await screen.findByRole('link', { name: 'Fix authentication bug' })
    const sort = screen.getByLabelText('Sort tasks')
    await userEvent.selectOptions(sort, 'dueDate,DESC')
    expect(rows()[0]).toHaveTextContent('Plan sprint')
    await userEvent.selectOptions(sort, 'name,ASC')
    expect(rows()[0]).toHaveTextContent('Design review')
    await userEvent.selectOptions(sort, 'project,ASC')
    expect(rows()[0]).toHaveTextContent('Mobile App')
    await userEvent.selectOptions(sort, 'dueDate,ASC')
    expect(rows()[0]).toHaveTextContent('Fix authentication bug')
  })

  it('changes a task\'s status inline through the real PATCH and reloads the lists afterwards', async () => {
    vi.mocked(tasksApi.update).mockImplementation(() => Promise.resolve(t({ id: 't5', status: 'COMPLETED', version: 4 })))
    renderPage()
    await screen.findByRole('link', { name: 'Design review' })
    const before = vi.mocked(tasksApi.list).mock.calls.length
    await userEvent.selectOptions(screen.getByRole('combobox', { name: 'Change status of Design review' }), 'COMPLETED')
    expect(tasksApi.update).toHaveBeenCalledWith('t5', { status: 'COMPLETED', version: 3 })
    await waitFor(() => expect(vi.mocked(tasksApi.list).mock.calls.length).toBeGreaterThan(before))
  })

  it('keeps the page useful when one project\'s tasks fail: shows the rest plus a notice with Retry', async () => {
    vi.mocked(tasksApi.list).mockImplementation((id: string) =>
      id === 'p2' ? Promise.reject(new Error('boom')) : Promise.resolve(tasksById[id]),
    )
    renderPage()
    await screen.findByRole('link', { name: 'Fix authentication bug' })
    expect(screen.queryByText('Plan sprint')).not.toBeInTheDocument()
    const notice = screen.getByRole('status', { name: '' })
    expect(notice).toHaveTextContent("Couldn't load tasks for one project: Mobile App")
    expect(screen.queryByRole('alert')).not.toBeInTheDocument()

    vi.mocked(tasksApi.list).mockImplementation((id: string) => Promise.resolve(tasksById[id]))
    await userEvent.click(within(notice).getByRole('button', { name: /Retry/ }))
    expect(await screen.findByRole('link', { name: 'Plan sprint' })).toBeInTheDocument()
    await waitFor(() => expect(screen.queryByText(/Couldn't load tasks/)).not.toBeInTheDocument())
  })

  it('says so when the organisation has more projects than the page loads', async () => {
    vi.mocked(projectsApi.list).mockImplementation(() => Promise.resolve(page(120)))
    renderPage()
    await screen.findByRole('link', { name: 'Fix authentication bug' })
    expect(screen.getByRole('note')).toHaveTextContent('Showing tasks from the 50 most recently updated projects (120 in total)')
  })

  it('without an identity the "Assigned to me" toggle is disabled and every open task is listed', async () => {
    setIdentity(null)
    renderPage()
    await screen.findByRole('link', { name: 'Fix authentication bug' })
    expect(screen.getByRole('checkbox', { name: 'Assigned to me' })).toBeDisabled()
    expect(rows()).toHaveLength(5)
    expect(chip('Assigned to me')).toHaveTextContent('0')
  })

  it('shows an error state with retry when the project list fails, and an empty state for an org without projects', async () => {
    vi.mocked(projectsApi.list).mockImplementation(() => Promise.reject(new Error('boom')))
    const { unmount } = renderPage()
    expect(await screen.findByRole('alert')).toHaveTextContent(/something went wrong/i)
    expect(screen.getByRole('button', { name: /try again/i })).toBeInTheDocument()
    unmount()

    vi.mocked(projectsApi.list).mockImplementation(() => Promise.resolve({ content: [], page: 0, size: 50, totalElements: 0, totalPages: 0 }))
    renderPage()
    expect(await screen.findByText('No projects yet')).toBeInTheDocument()
    expect(screen.getByRole('link', { name: /Go to projects/ })).toHaveAttribute('href', '/projects')
  })
})
