import { describe, expect, it, vi, beforeEach, afterEach } from 'vitest'
import { render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import { WorkspaceCalendarPage } from './WorkspaceCalendarPage'
import { IdentityProvider } from '../../context/IdentityContext'
import { setIdentity } from '../../context/identityStore'
import { projectsApi } from '../../api/projects'
import { calendarApi } from '../../api/calendar'
import { tasksApi } from '../../api/tasks'
import { milestonesApi } from '../../api/milestones'
import { longDateLabel, monthLabel } from '../projects/tabs/calendar/calendarUtils'
import type { Calendar, Milestone, Task } from '../../types/work'

vi.mock('../../api/projects', () => ({ projectsApi: { list: vi.fn() } }))
vi.mock('../../api/calendar', () => ({ calendarApi: { get: vi.fn() } }))
vi.mock('../../api/tasks', () => ({ tasksApi: { list: vi.fn(), create: vi.fn(), update: vi.fn() } }))
vi.mock('../../api/milestones', () => ({ milestonesApi: { list: vi.fn(), update: vi.fn() } }))

// Locale-independent expectations: labels are produced by the same formatter the UI uses.
const D = (iso: string) => longDateLabel(iso)
const M = (year: number, month: number) => monthLabel({ year, month })

const ME = '11111111-2222-4333-8444-555555555555'
const projects = [
  { id: 'p1', name: 'Website Redesign', status: 'ACTIVE', priority: 'HIGH', ownerId: 'u', updatedAt: '2026-09-10T00:00:00Z' },
  { id: 'p2', name: 'Mobile App', status: 'PLANNING', priority: 'MEDIUM', ownerId: 'u', updatedAt: '2026-09-09T00:00:00Z' },
] as const
const t = (over: Partial<Task>): Task => ({
  id: 'x', projectId: 'p1', name: 'x', description: null, dueDate: null, assigneeId: null, status: 'TODO', archived: false, createdAt: '', updatedAt: '', version: 0, ...over,
})
const m = (over: Partial<Milestone>): Milestone => ({
  id: 'm', projectId: 'p1', phaseId: null, name: 'M', description: null, dueDate: null, status: 'PENDING', archived: false, createdAt: '', updatedAt: '', version: 0, ...over,
})
const septById: Record<string, Calendar> = {
  p1: {
    entries: [
      { entityType: 'TASK', id: 't1', name: 'Ship release notes', date: '2026-09-19', startDate: null, endDate: null },
      { entityType: 'MILESTONE', id: 'm1', name: 'Beta', date: '2026-09-25', startDate: null, endDate: null },
      { entityType: 'PHASE', id: 'ph1', name: 'Build', date: null, startDate: '2026-09-18', endDate: '2026-09-20' },
    ],
  },
  p2: {
    entries: [{ entityType: 'TASK', id: 't2', name: 'Old task', date: '2026-09-02', startDate: null, endDate: null }],
  },
}
const tasksById: Record<string, Task[]> = {
  p1: [t({ id: 't1', name: 'Ship release notes', dueDate: '2026-09-19', assigneeId: 'user-42' })],
  p2: [t({ id: 't2', projectId: 'p2', name: 'Old task', dueDate: '2026-09-02' })],
}
const milestonesById: Record<string, Milestone[]> = { p1: [m({ id: 'm1', name: 'Beta', dueDate: '2026-09-25', status: 'AT_RISK' })], p2: [] }

function page(totalElements = 2) {
  return { content: [...projects], page: 0, size: 50, totalElements, totalPages: 1 }
}
function renderPage() {
  return render(
    <MemoryRouter>
      <IdentityProvider>
        <WorkspaceCalendarPage />
      </IdentityProvider>
    </MemoryRouter>,
  )
}
const grid = () => screen.findByRole('grid')
const side = () => screen.getByLabelText('Selected day')

describe('WorkspaceCalendarPage', () => {
  beforeEach(() => {
    vi.useFakeTimers({ toFake: ['Date'] })
    vi.setSystemTime(new Date(2026, 8, 19, 12))
    setIdentity({ userId: ME, orgId: '99999999-2222-4333-8444-555555555555' })
    vi.mocked(projectsApi.list).mockReset()
    vi.mocked(calendarApi.get).mockReset()
    vi.mocked(tasksApi.list).mockReset()
    vi.mocked(milestonesApi.list).mockReset()
    vi.mocked(projectsApi.list).mockImplementation(() => Promise.resolve(page()))
    vi.mocked(calendarApi.get).mockImplementation((id: string, from?: string) =>
      Promise.resolve(from === '2026-09-01' ? septById[id] : { entries: [] }),
    )
    vi.mocked(tasksApi.list).mockImplementation((id: string) => Promise.resolve(tasksById[id] ?? []))
    vi.mocked(milestonesApi.list).mockImplementation((id: string) => Promise.resolve(milestonesById[id] ?? []))
  })
  afterEach(() => vi.useRealTimers())

  it('requests the visible month for every loaded project and places entries with their project name', async () => {
    renderPage()
    expect(await screen.findByRole('heading', { name: 'Calendar' })).toBeInTheDocument()
    await grid()
    expect(projectsApi.list).toHaveBeenCalledWith(0, 50, 'updatedAt,DESC')
    expect(calendarApi.get).toHaveBeenCalledWith('p1', '2026-09-01', '2026-09-30')
    expect(calendarApi.get).toHaveBeenCalledWith('p2', '2026-09-01', '2026-09-30')

    expect(screen.getByRole('button', { name: 'Task: Ship release notes (Website Redesign)' })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Task: Old task (Mobile App)' })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Milestone: Beta (Website Redesign)' })).toBeInTheDocument()
    // Phases are real data but off by default so they don't crowd out due dates.
    expect(screen.queryByRole('button', { name: /Phase: Build/ })).not.toBeInTheDocument()

    // Today is selected; the side panel shows real status and the project label.
    expect(side()).toHaveTextContent(D('2026-09-19'))
    expect(within(side()).getByText('Ship release notes')).toBeInTheDocument()
    expect(within(side()).getByText('Website Redesign')).toBeInTheDocument()
    expect(within(side()).getByText('Todo')).toBeInTheDocument()
    expect(within(side()).getByText(/user-42/)).toBeInTheDocument()

    const glance = screen.getByLabelText('This month at a glance')
    expect(within(glance).getByText('Tasks due this month').previousSibling).toHaveTextContent('2')
    expect(within(glance).getByText('Past due').previousSibling).toHaveTextContent('1')
    expect(within(glance).getByText('Milestones').previousSibling).toHaveTextContent('1')
  })

  it('kind chips and the project select filter what is shown, with live counts', async () => {
    renderPage()
    await grid()
    const show = screen.getByRole('group', { name: 'Show' })
    expect(within(show).getByRole('button', { name: /Tasks/ })).toHaveTextContent('2')
    expect(within(show).getByRole('button', { name: /Milestones/ })).toHaveTextContent('1')
    expect(within(show).getByRole('button', { name: /Phases/ })).toHaveAttribute('aria-pressed', 'false')

    await userEvent.click(within(show).getByRole('button', { name: /Milestones/ }))
    expect(screen.queryByRole('button', { name: /Milestone: Beta/ })).not.toBeInTheDocument()
    await userEvent.click(within(show).getByRole('button', { name: /Phases/ }))
    expect(screen.getAllByRole('button', { name: /Phase: Build/ })).toHaveLength(3)

    await userEvent.selectOptions(screen.getByLabelText('Project'), 'p2')
    expect(screen.queryByRole('button', { name: /Ship release notes/ })).not.toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Task: Old task (Mobile App)' })).toBeInTheDocument()
    expect(within(show).getByRole('button', { name: /Tasks/ })).toHaveTextContent('1')
    expect(side()).toHaveTextContent('Nothing scheduled for this day.')
  })

  it('navigates months and refetches every project for the new window', async () => {
    renderPage()
    await grid()
    await userEvent.click(screen.getByRole('button', { name: 'Next month' }))
    expect(screen.getByText(M(2026, 9))).toBeInTheDocument()
    await waitFor(() => expect(calendarApi.get).toHaveBeenCalledWith('p1', '2026-10-01', '2026-10-31'))
    expect(calendarApi.get).toHaveBeenCalledWith('p2', '2026-10-01', '2026-10-31')
    expect(await screen.findByText('Nothing scheduled for this day.')).toBeInTheDocument()

    await userEvent.click(screen.getByRole('button', { name: 'Previous month' }))
    await userEvent.click(screen.getByRole('button', { name: 'Previous month' }))
    expect(screen.getByText(M(2026, 7))).toBeInTheDocument()
    await userEvent.click(screen.getByRole('button', { name: 'Today' }))
    expect(screen.getByText(M(2026, 8))).toBeInTheDocument()
    expect(side()).toHaveTextContent(D('2026-09-19'))
  })

  it('selecting an event opens the detail dialog with the real entity, its project and a link to the task page', async () => {
    renderPage()
    await grid()
    await userEvent.click(screen.getByRole('button', { name: 'Task: Old task (Mobile App)' }))
    const dialog = await screen.findByRole('dialog')
    expect(dialog).toHaveTextContent('Old task')
    expect(within(dialog).getByRole('link', { name: 'Mobile App' })).toHaveAttribute('href', '/projects/p2/overview')
    expect(within(dialog).getByRole('link', { name: /open task/i })).toHaveAttribute('href', '/projects/p2/tasks/t2')
    await userEvent.click(within(dialog).getByRole('button', { name: 'Close' }))
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
  })

  it('"Add task" on a day asks for a project first, then opens the task dialog pre-filled with that day', async () => {
    renderPage()
    await grid()
    await userEvent.click(screen.getByRole('button', { name: `${D('2026-09-02')}, 1 item` }))
    expect(side()).toHaveTextContent('Old task')
    await userEvent.click(within(side()).getByRole('button', { name: /Add task on/ }))
    const pick = await screen.findByRole('dialog', { name: 'Add a task in…' })
    expect(pick).toHaveTextContent(D('2026-09-02'))
    expect(within(pick).getByRole('button', { name: 'Continue' })).toBeDisabled()
    await userEvent.selectOptions(within(pick).getByLabelText(/Project/), 'p1')
    await userEvent.click(within(pick).getByRole('button', { name: 'Continue' }))
    const create = await screen.findByRole('dialog', { name: 'New Task' })
    expect(within(create).getByLabelText(/due date/i)).toHaveValue('2026-09-02')
  })

  it('skips the project picker when a project filter is already selected', async () => {
    renderPage()
    await grid()
    await userEvent.selectOptions(screen.getByLabelText('Project'), 'p2')
    await userEvent.click(screen.getByRole('button', { name: /^Add task$/ }))
    expect(await screen.findByRole('dialog', { name: 'New Task' })).toBeInTheDocument()
    expect(screen.getByLabelText(/due date/i)).toHaveValue('2026-09-19')
  })

  it('keeps the month useful when one project\'s calendar fails: other projects render plus a notice with Retry', async () => {
    vi.mocked(calendarApi.get).mockImplementation((id: string) =>
      id === 'p2' ? Promise.reject(new Error('boom')) : Promise.resolve(septById[id]),
    )
    renderPage()
    await grid()
    expect(screen.getByRole('button', { name: 'Task: Ship release notes (Website Redesign)' })).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: /Old task/ })).not.toBeInTheDocument()
    const notice = screen.getByRole('status', { name: '' })
    expect(notice).toHaveTextContent("Couldn't load the calendar for one project: Mobile App")
    expect(screen.queryByRole('alert')).not.toBeInTheDocument()

    vi.mocked(calendarApi.get).mockImplementation((id: string) => Promise.resolve(septById[id]))
    await userEvent.click(within(notice).getByRole('button', { name: /Retry/ }))
    expect(await screen.findByRole('button', { name: /Old task/ })).toBeInTheDocument()
  })

  it('says so when the organisation has more projects than the page loads', async () => {
    vi.mocked(projectsApi.list).mockImplementation(() => Promise.resolve(page(75)))
    renderPage()
    await grid()
    expect(screen.getByRole('note')).toHaveTextContent('Showing due dates from the 50 most recently updated projects (75 in total)')
  })

  it('shows an error state when the project list fails and an empty state for an org without projects', async () => {
    vi.mocked(projectsApi.list).mockImplementation(() => Promise.reject(new Error('boom')))
    const { unmount } = renderPage()
    expect(await screen.findByRole('alert')).toHaveTextContent(/something went wrong/i)
    unmount()

    vi.mocked(projectsApi.list).mockImplementation(() => Promise.resolve({ content: [], page: 0, size: 50, totalElements: 0, totalPages: 0 }))
    renderPage()
    expect(await screen.findByText('No projects yet')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: /^Add task$/ })).toBeDisabled()
  })
})
