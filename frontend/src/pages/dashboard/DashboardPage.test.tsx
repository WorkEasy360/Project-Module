import { describe, expect, it, vi, beforeEach, afterEach } from 'vitest'
import { render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import { DashboardPage } from './DashboardPage'
import { IdentityProvider } from '../../context/IdentityContext'
import { setIdentity } from '../../context/identityStore'
import { dashboardApi } from '../../api/dashboard'
import { projectsApi } from '../../api/projects'
import { healthApi } from '../../api/health'
import { tasksApi } from '../../api/tasks'
import { milestonesApi } from '../../api/milestones'
import { calendarApi } from '../../api/calendar'
import { aiApi } from '../../api/ai'
import { ApiError } from '../../api/client'
import type { Calendar, ProjectHealth, Task } from '../../types/work'
import type { AIRecommendation } from '../../types/ai'
import { longDateLabel, monthLabel } from '../projects/tabs/calendar/calendarUtils'
import { formatDate } from '../../utils/format'

vi.mock('../../api/dashboard', () => ({ dashboardApi: { get: vi.fn() } }))
vi.mock('../../api/projects', () => ({ projectsApi: { list: vi.fn(), create: vi.fn() } }))
vi.mock('../../api/health', () => ({ healthApi: { get: vi.fn() } }))
vi.mock('../../api/tasks', () => ({ tasksApi: { list: vi.fn(), create: vi.fn() } }))
vi.mock('../../api/milestones', () => ({ milestonesApi: { list: vi.fn() } }))
vi.mock('../../api/calendar', () => ({ calendarApi: { get: vi.fn() } }))
vi.mock('../../api/ai', () => ({ aiApi: { list: vi.fn() } }))

const ME = '11111111-2222-4333-8444-555555555555'
const summary = {
  activeProjectCount: 3,
  archivedProjectCount: 1,
  byStatus: { PLANNING: 1, ACTIVE: 2, ON_HOLD: 0, COMPLETED: 0, CANCELLED: 0 },
  byPriority: { LOW: 1, MEDIUM: 1, HIGH: 1, CRITICAL: 0 },
}
const projects = [
  { id: 'p1', name: 'Website Redesign', status: 'ACTIVE', priority: 'HIGH', ownerId: 'u', updatedAt: '2026-09-10T00:00:00Z' },
  { id: 'p2', name: 'Mobile App', status: 'PLANNING', priority: 'MEDIUM', ownerId: 'u', updatedAt: '2026-09-09T00:00:00Z' },
  { id: 'p3', name: 'Data Migration', status: 'ACTIVE', priority: 'LOW', ownerId: 'u', updatedAt: '2026-09-08T00:00:00Z' },
] as const
const ok: ProjectHealth = { overdueItemCount: 0, unresolvedRiskCount: 0, unresolvedIssueCount: 0, blockedTaskCount: 0, brokenDependencyCount: 0 }
const healthById: Record<string, ProjectHealth> = { p1: { ...ok, overdueItemCount: 1 }, p2: ok, p3: { ...ok, blockedTaskCount: 1 } }
const t = (over: Partial<Task>): Task => ({
  id: 'x', projectId: 'p1', name: 'x', description: null, dueDate: null, assigneeId: null, status: 'TODO', archived: false, createdAt: '', updatedAt: '', version: 0, ...over,
})
const tasksById: Record<string, Task[]> = {
  p1: [
    t({ id: 't1', name: 'Fix authentication bug', dueDate: '2026-09-10', assigneeId: ME }),
    t({ id: 't2', name: 'Write docs', status: 'COMPLETED' }),
  ],
  p2: [t({ id: 't3', projectId: 'p2', name: 'Plan sprint', dueDate: '2026-09-22', assigneeId: ME })],
  p3: [t({ id: 't4', projectId: 'p3', name: 'Migrate schema', status: 'BLOCKED', dueDate: '2026-09-15', assigneeId: 'someone' })],
}
const entry = (entityType: string, id: string, name: string, date: string) => ({ entityType, id, name, date, startDate: null, endDate: null })
const calendarById: Record<string, Record<string, Calendar>> = {
  '2026-09-01': {
    p1: { entries: [entry('TASK', 't1', 'Fix authentication bug', '2026-09-10')] },
    p2: { entries: [entry('TASK', 't3', 'Plan sprint', '2026-09-22'), entry('MILESTONE', 'm-early', 'Design freeze', '2026-09-22')] },
    p3: { entries: [entry('TASK', 't4', 'Migrate schema', '2026-09-15'), { entityType: 'PHASE', id: 'ph', name: 'Build', date: null, startDate: '2026-09-01', endDate: '2026-09-30' }] },
  },
  '2026-10-01': {
    p1: { entries: [] },
    p2: { entries: [entry('MILESTONE', 'm1', 'Project Kickoff', '2026-10-10')] },
    p3: { entries: [] },
  },
}
const rec = (over: Partial<AIRecommendation>): AIRecommendation => ({
  id: 'r', projectId: 'p1', type: 'RISK_ANALYSIS', resourceId: null, question: null, title: null, rationale: null, payload: null,
  status: 'PENDING', requestedBy: 'u', respondedBy: null, respondedAt: null, createdAt: '2026-09-01T00:00:00Z', updatedAt: '', version: 0, ...over,
})

function renderHome() {
  return render(
    <MemoryRouter>
      <IdentityProvider>
        <DashboardPage />
      </IdentityProvider>
    </MemoryRouter>,
  )
}
describe('DashboardPage (reference layout)', () => {
  beforeEach(() => {
    vi.useFakeTimers({ toFake: ['Date'] })
    vi.setSystemTime(new Date(2026, 8, 19, 9))
    setIdentity({ userId: ME, orgId: '99999999-2222-4333-8444-555555555555' })
    vi.mocked(dashboardApi.get).mockImplementation(() => Promise.resolve(summary))
    vi.mocked(projectsApi.list).mockImplementation(() => Promise.resolve({ content: [...projects], page: 0, size: 10, totalElements: 3, totalPages: 1 }))
    vi.mocked(healthApi.get).mockImplementation((id: string) => Promise.resolve(healthById[id]))
    vi.mocked(tasksApi.list).mockImplementation((id: string) => Promise.resolve(tasksById[id]))
    vi.mocked(milestonesApi.list).mockImplementation((id: string) =>
      Promise.resolve(id === 'p2' ? [{ id: 'm1', projectId: 'p2', phaseId: null, name: 'Project Kickoff', description: null, dueDate: '2026-10-10', status: 'PENDING', archived: false, createdAt: '', updatedAt: '', version: 0 }] : []),
    )
    vi.mocked(calendarApi.get).mockImplementation((id: string, from?: string) => Promise.resolve(calendarById[from ?? '']?.[id] ?? { entries: [] }))
    vi.mocked(aiApi.list).mockImplementation((id: string) =>
      Promise.resolve(
        id === 'p1'
          ? [
              rec({ id: 'r1', title: 'Split the auth rewrite into 4 tasks', type: 'TASK_BREAKDOWN', createdAt: '2026-09-12T00:00:00Z' }),
              rec({ id: 'r2', title: 'Accepted earlier', status: 'ACCEPTED', createdAt: '2026-09-13T00:00:00Z' }),
            ]
          : id === 'p3'
            ? [rec({ id: 'r3', projectId: 'p3', title: null, type: 'RISK_ANALYSIS', createdAt: '2026-09-14T00:00:00Z' })]
            : [],
      ),
    )
    vi.mocked(projectsApi.list).mockClear()
    vi.mocked(calendarApi.get).mockClear()
  })
  afterEach(() => vi.useRealTimers())

  it('renders hero greeting, real summary values and the four stat cards without any trend figures', async () => {
    renderHome()
    expect(await screen.findByRole('heading', { name: /Good morning, 11111111!/ })).toBeInTheDocument()
    const total = await screen.findByRole('link', { name: /Total Projects/ })
    expect(total).toHaveTextContent('3')
    expect(screen.getByRole('button', { name: /Active$/ })).toHaveTextContent('2')
    expect(screen.getByRole('button', { name: /Planning/ })).toHaveTextContent('1')
    expect(screen.getByRole('button', { name: /Completed/ })).toHaveTextContent('0')
    // The backend has no trend data: no percentages or arrows on the stat cards.
    expect(screen.getByRole('region', { name: 'Summary' })).not.toHaveTextContent(/%|↑|↓/)
    expect(vi.mocked(projectsApi.list)).toHaveBeenCalledWith(0, 10, 'updatedAt,DESC')
  })

  it('Needs Attention lists real overdue/blocked tasks with project, badge, date and task links; filters and sort work', async () => {
    renderHome()
    const panel = await screen.findByRole('region', { name: /Needs Attention/ })
    await within(panel).findByText('Fix authentication bug')
    expect(within(panel).getByText('2 items')).toBeInTheDocument()
    expect(within(panel).getByRole('link', { name: /View All/ })).toHaveAttribute('href', '/tasks')
    const rows = within(panel).getAllByRole('listitem')
    expect(rows[0]).toHaveTextContent('Fix authentication bug')
    expect(rows[0]).toHaveTextContent('Website Redesign')
    // A TODO task past its due date keeps its stored status; "past due" is the derived marker.
    expect(rows[0]).toHaveTextContent('Todo')
    expect(rows[0]).toHaveTextContent('past due')
    expect(rows[0]).not.toHaveTextContent('Overdue')
    expect(within(rows[0]).getByRole('link', { name: 'Fix authentication bug' })).toHaveAttribute('href', '/projects/p1/tasks/t1')
    expect(rows[1]).toHaveTextContent('Migrate schema')
    expect(rows[1]).toHaveTextContent('Blocked')
    // Blocked AND past due: the stored status is still what the user sees, plus a secondary marker.
    expect(rows[1]).not.toHaveTextContent('Overdue')
    expect(rows[1]).toHaveTextContent('past due')
    expect(rows[0]).not.toHaveTextContent('Blocked')
    expect(within(panel).queryByText('Write docs')).not.toBeInTheDocument()
    // Tasks have no priority on this backend and there is no bulk action: no such columns.
    expect(within(panel).queryByRole('checkbox')).not.toBeInTheDocument()
    expect(panel).not.toHaveTextContent(/Priority/)

    await userEvent.selectOptions(within(panel).getByLabelText('Status'), 'BLOCKED')
    expect(within(panel).getAllByRole('listitem')).toHaveLength(1)
    await userEvent.selectOptions(within(panel).getByLabelText('Status'), '')
    await userEvent.selectOptions(within(panel).getByLabelText('Project'), 'p1')
    expect(within(panel).getAllByRole('listitem')[0]).toHaveTextContent('Fix authentication bug')
    await userEvent.selectOptions(within(panel).getByLabelText('Project'), '')
    await userEvent.type(within(panel).getByRole('searchbox'), 'zzz')
    expect(within(panel).getByText('No items match these filters.')).toBeInTheDocument()
    await userEvent.click(within(panel).getByRole('button', { name: 'Reset filters' }))
    expect(within(panel).getAllByRole('listitem')).toHaveLength(2)
    await userEvent.selectOptions(within(panel).getByLabelText('Sort attention items'), 'dueDate,DESC')
    expect(within(panel).getAllByRole('listitem')[0]).toHaveTextContent('Migrate schema')
  })

  it('mini calendar: fetches each loaded project’s month window, dots come from calendar entries, month navigation refetches', async () => {
    renderHome()
    const cal = await screen.findByRole('region', { name: 'Calendar' })
    await waitFor(() => expect(calendarApi.get).toHaveBeenCalledWith('p1', '2026-09-01', '2026-09-30'))
    expect(calendarApi.get).toHaveBeenCalledWith('p2', '2026-09-01', '2026-09-30')
    expect(calendarApi.get).toHaveBeenCalledWith('p3', '2026-09-01', '2026-09-30')
    expect(within(cal).getByRole('heading', { name: monthLabel({ year: 2026, month: 8 }) })).toBeInTheDocument()

    // Day 22 has a task and a milestone (from two different projects); day 10 one task; day 5 nothing.
    const day22 = await within(cal).findByRole('gridcell', { name: `${longDateLabel('2026-09-22')}, 1 task, 1 milestone` })
    expect(day22.querySelector('.hb-cal-dot.task')).toBeInTheDocument()
    expect(day22.querySelector('.hb-cal-dot.milestone')).toBeInTheDocument()
    const day10 = within(cal).getByRole('gridcell', { name: `${longDateLabel('2026-09-10')}, 1 task` })
    expect(day10.querySelector('.hb-cal-dot.milestone')).not.toBeInTheDocument()
    const day5 = within(cal).getByRole('gridcell', { name: longDateLabel('2026-09-05') })
    expect(day5.querySelector('.hb-cal-dot')).not.toBeInTheDocument()
    // A phase range never produces dots (the legend only promises tasks and milestones).
    expect(within(cal).getByRole('gridcell', { name: longDateLabel('2026-09-03') }).querySelector('.hb-cal-dot')).not.toBeInTheDocument()
    expect(within(cal).getByRole('gridcell', { name: `${longDateLabel('2026-09-19')}, today` })).toHaveAttribute('aria-selected', 'true')
    expect(cal).toHaveTextContent('Tasks')
    expect(cal).toHaveTextContent('Milestones')

    await userEvent.click(within(cal).getByRole('button', { name: 'Next month' }))
    await waitFor(() => expect(calendarApi.get).toHaveBeenCalledWith('p2', '2026-10-01', '2026-10-31'))
    expect(await within(cal).findByRole('gridcell', { name: `${longDateLabel('2026-10-10')}, 1 milestone` })).toBeInTheDocument()
    expect(within(cal).getByRole('heading', { name: monthLabel({ year: 2026, month: 9 }) })).toBeInTheDocument()
  })

  it('mini calendar: "Add task" asks for a project, then opens the task dialog with the clicked day preselected', async () => {
    renderHome()
    const cal = await screen.findByRole('region', { name: 'Calendar' })
    await within(cal).findByRole('gridcell', { name: `${longDateLabel('2026-09-22')}, 1 task, 1 milestone` })
    await userEvent.click(within(cal).getByRole('gridcell', { name: `${longDateLabel('2026-09-24')}` }))
    expect(within(cal).getByRole('gridcell', { name: longDateLabel('2026-09-24') })).toHaveAttribute('aria-selected', 'true')
    await userEvent.click(within(cal).getByRole('button', { name: /Add task/ }))
    const pick = await screen.findByRole('dialog', { name: 'Create a task in…' })
    expect(pick).toHaveTextContent(longDateLabel('2026-09-24'))
    await userEvent.selectOptions(within(pick).getByLabelText(/Project/), 'p2')
    await userEvent.click(within(pick).getByRole('button', { name: 'Continue' }))
    const dialog = await screen.findByRole('dialog', { name: 'New Task' })
    expect(within(dialog).getByLabelText('Due date')).toHaveValue('2026-09-24')
  })

  it('Upcoming lists dated open tasks and pending milestones across loaded projects, soonest first', async () => {
    renderHome()
    const up = await screen.findByRole('region', { name: 'Upcoming' })
    expect(within(up).getByRole('link', { name: /View All/ })).toHaveAttribute('href', '/calendar')
    const rows = await within(up).findAllByRole('listitem')
    // Past-due "Fix authentication bug" (Sep 10) and completed "Write docs" are not upcoming.
    expect(rows).toHaveLength(2)
    expect(rows[0]).toHaveTextContent('Plan sprint')
    expect(rows[0]).toHaveTextContent('Task · Mobile App')
    expect(within(rows[0]).getByRole('link', { name: 'Plan sprint' })).toHaveAttribute('href', '/projects/p2/tasks/t3')
    expect(rows[1]).toHaveTextContent('Project Kickoff')
    expect(rows[1]).toHaveTextContent('Milestone · Mobile App')
    expect(within(rows[1]).getByRole('link', { name: 'Project Kickoff' })).toHaveAttribute('href', '/projects/p2/milestones')
    expect(within(up).queryByText('Fix authentication bug')).not.toBeInTheDocument()
  })

  it('Your Projects: cards with real progress and meta, search + status filter + backend sort + view toggle, New Project dialog', async () => {
    renderHome()
    const section = await screen.findByRole('region', { name: 'Your Projects' })
    await within(section).findByRole('link', { name: 'Website Redesign' })
    const card = within(section).getByRole('link', { name: 'Website Redesign' }).closest('li')!
    expect(within(card).getByRole('progressbar')).toHaveAttribute('aria-valuenow', '50')
    expect(within(card).getByTitle('Tasks')).toHaveTextContent('2 tasks')
    expect(within(card).getByTitle('Milestones')).toHaveTextContent('0 milestones')
    expect(within(card).getByTitle('Latest task due date')).toHaveTextContent(formatDate('2026-09-10'))
    expect(card).toHaveTextContent('Active')
    // p3 has one open (blocked) task and nothing completed → 0%, never a fabricated value
    const p3 = within(section).getByRole('link', { name: 'Data Migration' }).closest('li')!
    expect(within(p3).getByRole('progressbar')).toHaveAttribute('aria-valuenow', '0')
    const p2 = within(section).getByRole('link', { name: 'Mobile App' }).closest('li')!
    expect(within(p2).getByTitle('Milestones')).toHaveTextContent('1 milestone')

    await userEvent.type(within(section).getByRole('searchbox'), 'mob')
    expect(within(section).getAllByRole('link', { name: /^(Website Redesign|Mobile App|Data Migration)$/ })).toHaveLength(1)
    expect(within(section).getByText('Showing 1 of 3 loaded · 3 active in total')).toBeInTheDocument()
    await userEvent.click(within(section).getByRole('button', { name: 'Clear search' }))
    await userEvent.selectOptions(within(section).getByLabelText('Status'), 'ACTIVE')
    expect(within(section).getAllByRole('link', { name: /^(Website Redesign|Mobile App|Data Migration)$/ })).toHaveLength(2)
    await userEvent.click(within(section).getByRole('button', { name: /Reset filters/ }))
    expect(within(section).getAllByRole('link', { name: /^(Website Redesign|Mobile App|Data Migration)$/ })).toHaveLength(3)

    await userEvent.selectOptions(within(section).getByLabelText('Sort by'), 'name,ASC')
    await waitFor(() => expect(vi.mocked(projectsApi.list)).toHaveBeenLastCalledWith(0, 10, 'name,ASC'))
    await userEvent.click(within(section).getByRole('button', { name: 'List view' }))
    expect(section.querySelector('.hb-list')).toBeInTheDocument()

    await userEvent.click(within(section).getByRole('button', { name: /New Project/ }))
    expect(await screen.findByRole('dialog', { name: 'New Project' })).toBeInTheDocument()
  })

  it('stat cards toggle the project status filter', async () => {
    renderHome()
    const section = await screen.findByRole('region', { name: 'Your Projects' })
    await within(section).findByRole('link', { name: 'Website Redesign' })
    await userEvent.click(screen.getByRole('button', { name: /Planning/ }))
    expect(within(section).getByLabelText('Status')).toHaveValue('PLANNING')
    expect(within(section).queryByRole('link', { name: 'Website Redesign' })).not.toBeInTheDocument()
  })

  it('lower widgets: donut from real status counts with an "Active" legend, activity honestly unavailable', async () => {
    renderHome()
    const status = await screen.findByRole('region', { name: 'Project Status' })
    expect(within(status).getByRole('img')).toHaveAttribute('aria-label', '2 Active (67%), 1 Planning (33%)')
    expect(within(status).getByText('Active')).toBeInTheDocument()
    expect(within(status).getByText('Planning')).toBeInTheDocument()
    expect(screen.getByRole('region', { name: 'Recent Activity' })).toHaveTextContent('Not available yet')
  })

  it('AI Recommendations: newest pending items per loaded project, linking to the project AI tab', async () => {
    renderHome()
    const ai = await screen.findByRole('region', { name: 'AI Recommendations' })
    const rows = await within(ai).findAllByRole('listitem')
    expect(rows).toHaveLength(2)
    // Newest first; an untitled recommendation falls back to its type label. Accepted ones are not pending.
    expect(rows[0]).toHaveTextContent('Risk Analysis')
    expect(rows[0]).toHaveTextContent('Data Migration')
    expect(within(rows[0]).getByRole('link', { name: 'Risk Analysis' })).toHaveAttribute('href', '/projects/p3/ai')
    expect(rows[1]).toHaveTextContent('Split the auth rewrite into 4 tasks')
    expect(rows[1]).toHaveTextContent('Task Breakdown')
    expect(within(ai).queryByText('Accepted earlier')).not.toBeInTheDocument()
    expect(within(ai).queryByRole('textbox')).not.toBeInTheDocument()
  })

  it('AI Recommendations: a 503 from the backend shows "AI provider not configured" and nothing invented', async () => {
    vi.mocked(aiApi.list).mockImplementation(() => Promise.reject(new ApiError(503, null, 'unavailable')))
    renderHome()
    const ai = await screen.findByRole('region', { name: 'AI Recommendations' })
    expect(await within(ai).findByText(/AI provider not configured/)).toBeInTheDocument()
    expect(within(ai).queryAllByRole('listitem')).toHaveLength(0)
  })

  it('shows the error state with retry when the summary API fails, and a first-project state for an empty org', async () => {
    vi.mocked(dashboardApi.get).mockImplementation(() => Promise.reject(new Error('boom')))
    const { unmount } = renderHome()
    expect(await screen.findByRole('alert')).toHaveTextContent(/something went wrong/i)
    unmount()
    vi.mocked(dashboardApi.get).mockImplementation(() => Promise.resolve({ ...summary, activeProjectCount: 0, archivedProjectCount: 0 }))
    renderHome()
    expect(await screen.findByRole('heading', { name: /set up your first project/i })).toBeInTheDocument()
  })
})
