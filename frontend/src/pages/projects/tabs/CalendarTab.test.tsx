import { describe, expect, it, vi, beforeEach, afterEach } from 'vitest'
import { render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import { CalendarTab } from './CalendarTab'
import { calendarApi } from '../../../api/calendar'
import { tasksApi } from '../../../api/tasks'
import { milestonesApi } from '../../../api/milestones'
import { phasesApi } from '../../../api/phases'
import { ApiError } from '../../../api/client'
import { ProjectWorkspaceContext, type ProjectWorkspaceValue } from '../../../context/ProjectWorkspaceContext'
import type { Project } from '../../../types/project'
import { longDateLabel, monthLabel } from './calendar/calendarUtils'

// Locale-independent expectations: labels are produced by the same formatter the UI uses.
const D = (iso: string) => longDateLabel(iso)
const M = (year: number, month: number) => monthLabel({ year, month })

vi.mock('../../../api/calendar', () => ({ calendarApi: { get: vi.fn() } }))
vi.mock('../../../api/tasks', () => ({ tasksApi: { list: vi.fn(), create: vi.fn(), update: vi.fn() } }))
vi.mock('../../../api/milestones', () => ({ milestonesApi: { list: vi.fn(), create: vi.fn(), update: vi.fn() } }))
vi.mock('../../../api/phases', () => ({ phasesApi: { list: vi.fn() } }))

const project: Project = {
  id: 'p1', organizationId: 'o1', name: 'P', description: null, status: 'ACTIVE', priority: 'MEDIUM',
  startDate: null, targetEndDate: null, ownerId: 'u1', archived: false, archivedAt: null, archivedBy: null,
  createdAt: '', updatedAt: '', version: 0,
}

function renderCalendar(canEdit = true) {
  const value: ProjectWorkspaceValue = {
    project,
    reloadProject: vi.fn(),
    currentMember: { id: 'm', projectId: 'p1', userId: 'u1', role: canEdit ? 'OWNER' : 'VIEWER', joinedAt: '', version: 0 },
    can: (p) => (canEdit ? true : p === 'VIEW_PROJECT'),
  }
  return render(
    <MemoryRouter initialEntries={['/projects/p1/calendar']}>
      <ProjectWorkspaceContext.Provider value={value}>
        <CalendarTab />
      </ProjectWorkspaceContext.Provider>
    </MemoryRouter>,
  )
}

const sept = {
  entries: [
    { entityType: 'TASK', id: 't1', name: 'Ship release notes', date: '2026-09-19', startDate: null, endDate: null },
    { entityType: 'TASK', id: 't2', name: 'Old task', date: '2026-09-02', startDate: null, endDate: null },
    { entityType: 'MILESTONE', id: 'm1', name: 'Beta', date: '2026-09-25', startDate: null, endDate: null },
    { entityType: 'PHASE', id: 'ph1', name: 'Build', date: null, startDate: '2026-09-18', endDate: '2026-09-20' },
  ],
}

describe('CalendarTab', () => {
  beforeEach(() => {
    vi.useFakeTimers({ toFake: ['Date'] })
    vi.setSystemTime(new Date(2026, 8, 19, 12))
    vi.mocked(calendarApi.get).mockReset()
    vi.mocked(calendarApi.get).mockImplementation(() => Promise.resolve(sept))
    vi.mocked(tasksApi.list).mockImplementation(() =>
      Promise.resolve([
        { id: 't1', projectId: 'p1', name: 'Ship release notes', description: null, dueDate: '2026-09-19', assigneeId: 'user-42', status: 'TODO', archived: false, createdAt: '', updatedAt: '', version: 0 },
        { id: 't2', projectId: 'p1', name: 'Old task', description: null, dueDate: '2026-09-02', assigneeId: null, status: 'TODO', archived: false, createdAt: '', updatedAt: '', version: 0 },
      ]),
    )
    vi.mocked(milestonesApi.list).mockImplementation(() =>
      Promise.resolve([
        { id: 'm1', projectId: 'p1', phaseId: null, name: 'Beta', description: null, dueDate: '2026-09-25', status: 'PENDING', archived: false, createdAt: '', updatedAt: '', version: 3 },
      ]),
    )
    vi.mocked(phasesApi.list).mockImplementation(() => Promise.resolve([]))
    vi.mocked(tasksApi.update).mockReset()
    vi.mocked(milestonesApi.create).mockReset()
    vi.mocked(milestonesApi.update).mockReset()
  })
  afterEach(() => vi.useRealTimers())

  it('requests the visible month from the API and renders entries in the grid', async () => {
    renderCalendar()
    expect(await screen.findByRole('heading', { name: 'Project Calendar' })).toBeInTheDocument()
    await waitFor(() => expect(calendarApi.get).toHaveBeenCalledWith('p1', '2026-09-01', '2026-09-30'))
    expect(await screen.findByRole('grid')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: /Milestone: Beta/ })).toBeInTheDocument()
    // A phase spanning three days appears on each of them.
    expect(screen.getAllByRole('button', { name: /Phase: Build/ })).toHaveLength(3)
  })

  it('shows honest summary counts derived from the loaded month', async () => {
    renderCalendar()
    await screen.findByRole('grid')
    const summary = screen.getByLabelText('This month at a glance')
    expect(within(summary).getByText('Tasks due this month').previousSibling).toHaveTextContent('2')
    expect(within(summary).getByText('Past due').previousSibling).toHaveTextContent('1')
    expect(within(summary).getByText('Milestones').previousSibling).toHaveTextContent('1')
    expect(within(summary).getByText('Completed').previousSibling).toHaveTextContent('0')
  })

  it('selects today by default and lists its events in the side panel with real status', async () => {
    renderCalendar()
    await screen.findByRole('grid')
    const side = screen.getByLabelText('Selected day')
    expect(side).toHaveTextContent(D('2026-09-19'))
    expect(side).toHaveTextContent('Today')
    expect(within(side).getByText('Ship release notes')).toBeInTheDocument()
    expect(within(side).getByText('Todo')).toBeInTheDocument()
    expect(within(side).getByText(/user-42/)).toBeInTheDocument()
  })

  it('navigates months (prev / next / today) and refetches the new window', async () => {
    vi.mocked(calendarApi.get).mockImplementation((_p, from) =>
      Promise.resolve(from === '2026-10-01' ? { entries: [] } : sept),
    )
    renderCalendar()
    await screen.findByRole('grid')

    await userEvent.click(screen.getByRole('button', { name: 'Next month' }))
    expect(screen.getByText(M(2026, 9))).toBeInTheDocument()
    await waitFor(() => expect(calendarApi.get).toHaveBeenCalledWith('p1', '2026-10-01', '2026-10-31'))
    expect(await screen.findByText('Nothing scheduled for this day.')).toBeInTheDocument()

    await userEvent.click(screen.getByRole('button', { name: 'Previous month' }))
    expect(screen.getByText(M(2026, 8))).toBeInTheDocument()

    await userEvent.click(screen.getByRole('button', { name: 'Previous month' }))
    expect(screen.getByText(M(2026, 7))).toBeInTheDocument()
    await userEvent.click(screen.getByRole('button', { name: 'Today' }))
    expect(screen.getByText(M(2026, 8))).toBeInTheDocument()
    expect(screen.getByLabelText('Selected day')).toHaveTextContent(D('2026-09-19'))
  })

  it('switches to the agenda view and back', async () => {
    renderCalendar()
    await screen.findByRole('grid')
    await userEvent.click(screen.getByRole('button', { name: 'Agenda' }))
    expect(screen.getByRole('button', { name: 'Agenda' })).toHaveAttribute('aria-pressed', 'true')
    expect(screen.queryByRole('grid')).not.toBeInTheDocument()
    expect(screen.getByLabelText(D('2026-09-02'))).toHaveTextContent('Old task')
    await userEvent.click(screen.getByRole('button', { name: 'Month' }))
    expect(screen.getByRole('grid')).toBeInTheDocument()
  })

  it('selecting a day updates the side panel; selecting an event opens details linking to the task page', async () => {
    renderCalendar()
    await screen.findByRole('grid')
    await userEvent.click(screen.getByRole('button', { name: `${D('2026-09-02')}, 1 item` }))
    const side = screen.getByLabelText('Selected day')
    expect(side).toHaveTextContent('Old task')

    await userEvent.click(within(side).getByRole('button', { name: /Old task/ }))
    const dialog = await screen.findByRole('dialog')
    expect(dialog).toHaveTextContent('Old task')
    expect(within(dialog).getByRole('link', { name: /open task/i })).toHaveAttribute('href', '/projects/p1/tasks/t2')
  })

  it('offers "Add task" pre-filled with the selected day only to editors', async () => {
    renderCalendar()
    await screen.findByRole('grid')
    await userEvent.click(screen.getByRole('button', { name: /^Add task$/ }))
    expect(screen.getByLabelText(/due date/i)).toHaveValue('2026-09-19')
  })

  it('hides task creation for viewers', async () => {
    renderCalendar(false)
    await screen.findByRole('grid')
    expect(screen.queryByRole('button', { name: /^Add task$/ })).not.toBeInTheDocument()
  })

  it('shows an error state with retry when the calendar API fails', async () => {
    vi.mocked(calendarApi.get).mockImplementation(() =>
      Promise.reject(new Error('boom')),
    )
    renderCalendar()
    expect(await screen.findByRole('alert')).toHaveTextContent(/something went wrong/i)
    expect(screen.getByRole('button', { name: /try again/i })).toBeInTheDocument()
  })

  it('shows a helpful empty state in agenda view when nothing is scheduled', async () => {
    vi.mocked(calendarApi.get).mockImplementation(() => Promise.resolve({ entries: [] }))
    renderCalendar()
    await waitFor(() => expect(screen.queryByLabelText('Selected day')).toBeInTheDocument())
    await userEvent.click(screen.getByRole('button', { name: 'Agenda' }))
    expect(await screen.findByText('Nothing is scheduled this month.')).toBeInTheDocument()
  })

  it('"+ Add" on the selected day offers Task or Milestone; Milestone creates on that day and reloads the grid', async () => {
    vi.mocked(milestonesApi.create).mockResolvedValue({
      id: 'm2', projectId: 'p1', phaseId: null, name: 'Launch review', description: null, dueDate: '2026-09-19', status: 'PENDING', archived: false, createdAt: '', updatedAt: '', version: 0,
    })
    renderCalendar()
    await screen.findByRole('grid')
    const side = screen.getByLabelText('Selected day')
    await userEvent.click(within(side).getByRole('button', { name: /^Add$/ }))
    const menu = screen.getByRole('menu', { name: `Add on ${D('2026-09-19')}` })
    expect(within(menu).getByRole('menuitem', { name: 'Task' })).toBeInTheDocument()
    await userEvent.click(within(menu).getByRole('menuitem', { name: 'Milestone' }))

    const dialog = await screen.findByRole('dialog', { name: 'New Milestone' })
    expect(within(dialog).getByLabelText(/due date/i)).toHaveValue('2026-09-19')
    const callsBefore = vi.mocked(calendarApi.get).mock.calls.length
    await userEvent.type(within(dialog).getByLabelText(/^name/i), 'Launch review')
    await userEvent.click(within(dialog).getByRole('button', { name: 'Save' }))

    await waitFor(() =>
      expect(milestonesApi.create).toHaveBeenCalledWith('p1', { name: 'Launch review', description: null, dueDate: '2026-09-19', phaseId: null }),
    )
    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument())
    await waitFor(() => expect(vi.mocked(calendarApi.get).mock.calls.length).toBeGreaterThan(callsBefore))
  })

  it('the per-day "+" in the grid selects that day and opens the add menu; Task pre-fills that day', async () => {
    renderCalendar()
    await screen.findByRole('grid')
    await userEvent.click(screen.getByRole('button', { name: `Add on ${D('2026-09-10')}` }))
    const menu = await screen.findByRole('menu', { name: `Add on ${D('2026-09-10')}` })
    await userEvent.click(within(menu).getByRole('menuitem', { name: 'Task' }))
    expect(await screen.findByRole('dialog', { name: 'New Task' })).toBeInTheDocument()
    expect(screen.getByLabelText(/due date/i)).toHaveValue('2026-09-10')
  })

  it('editing a task due date from the detail dialog PATCHes with the version and reloads', async () => {
    vi.mocked(tasksApi.update).mockResolvedValue({
      id: 't2', projectId: 'p1', name: 'Old task', description: null, dueDate: '2026-09-05', assigneeId: null, status: 'TODO', archived: false, createdAt: '', updatedAt: '', version: 1,
    })
    renderCalendar()
    await screen.findByRole('grid')
    await userEvent.click(screen.getByRole('button', { name: `${D('2026-09-02')}, 1 item` }))
    await userEvent.click(within(screen.getByLabelText('Selected day')).getByRole('button', { name: /Old task/ }))
    const dialog = await screen.findByRole('dialog')
    const save = within(dialog).getByRole('button', { name: 'Save date' })
    expect(save).toBeDisabled()
    const callsBefore = vi.mocked(calendarApi.get).mock.calls.length

    const input = within(dialog).getByLabelText('Due date')
    await userEvent.clear(input)
    await userEvent.type(input, '2026-09-05')
    expect(save).toBeEnabled()
    await userEvent.click(save)

    await waitFor(() => expect(tasksApi.update).toHaveBeenCalledWith('t2', { dueDate: '2026-09-05', version: 0 }))
    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument())
    await waitFor(() => expect(vi.mocked(calendarApi.get).mock.calls.length).toBeGreaterThan(callsBefore))
  })

  it('editing a milestone due date uses the milestone API with its own version', async () => {
    vi.mocked(milestonesApi.update).mockResolvedValue({
      id: 'm1', projectId: 'p1', phaseId: null, name: 'Beta', description: null, dueDate: '2026-09-26', status: 'PENDING', archived: false, createdAt: '', updatedAt: '', version: 4,
    })
    renderCalendar()
    await screen.findByRole('grid')
    await userEvent.click(screen.getByRole('button', { name: /Milestone: Beta/ }))
    const dialog = await screen.findByRole('dialog')
    expect(within(dialog).queryByLabelText(/change status/i)).not.toBeInTheDocument()
    const input = within(dialog).getByLabelText('Due date')
    await userEvent.clear(input)
    await userEvent.type(input, '2026-09-26')
    await userEvent.click(within(dialog).getByRole('button', { name: 'Save date' }))
    await waitFor(() => expect(milestonesApi.update).toHaveBeenCalledWith('m1', { dueDate: '2026-09-26', version: 3 }))
    expect(tasksApi.update).not.toHaveBeenCalled()
  })

  it('changing a task status from the detail dialog goes through the shared status control and reloads', async () => {
    vi.mocked(tasksApi.update).mockResolvedValue({
      id: 't1', projectId: 'p1', name: 'Ship release notes', description: null, dueDate: '2026-09-19', assigneeId: 'user-42', status: 'COMPLETED', archived: false, createdAt: '', updatedAt: '', version: 1,
    })
    renderCalendar()
    await screen.findByRole('grid')
    await userEvent.click(within(screen.getByLabelText('Selected day')).getByRole('button', { name: /Ship release notes/ }))
    const dialog = await screen.findByRole('dialog')
    const tasksCallsBefore = vi.mocked(tasksApi.list).mock.calls.length
    await userEvent.selectOptions(within(dialog).getByLabelText('Change status of Ship release notes'), 'COMPLETED')
    await waitFor(() => expect(tasksApi.update).toHaveBeenCalledWith('t1', { status: 'COMPLETED', version: 0 }))
    await waitFor(() => expect(vi.mocked(tasksApi.list).mock.calls.length).toBeGreaterThan(tasksCallsBefore))
  })

  it('a 409 on save shows the "changed elsewhere" notice with a reload action instead of pretending', async () => {
    vi.mocked(tasksApi.update).mockRejectedValue(new ApiError(409, null, 'conflict'))
    renderCalendar()
    await screen.findByRole('grid')
    await userEvent.click(within(screen.getByLabelText('Selected day')).getByRole('button', { name: /Ship release notes/ }))
    const dialog = await screen.findByRole('dialog')
    const input = within(dialog).getByLabelText('Due date')
    await userEvent.clear(input)
    await userEvent.type(input, '2026-09-21')
    await userEvent.click(within(dialog).getByRole('button', { name: 'Save date' }))

    const notice = await within(dialog).findByRole('alert')
    expect(notice).toHaveTextContent(/changed elsewhere/i)
    const callsBefore = vi.mocked(calendarApi.get).mock.calls.length
    await userEvent.click(within(notice).getByRole('button', { name: 'Reload' }))
    await waitFor(() => expect(vi.mocked(calendarApi.get).mock.calls.length).toBeGreaterThan(callsBefore))
    // Still open — the user decides what to do after seeing fresh data.
    expect(screen.getByRole('dialog')).toBeInTheDocument()
  })

  it('viewers get a read-only detail: no add menu, no date field, no status control', async () => {
    renderCalendar(false)
    await screen.findByRole('grid')
    const side = screen.getByLabelText('Selected day')
    expect(within(side).queryByRole('button', { name: /^Add$/ })).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: /^Add on / })).not.toBeInTheDocument()
    await userEvent.click(within(side).getByRole('button', { name: /Ship release notes/ }))
    const dialog = await screen.findByRole('dialog')
    expect(within(dialog).queryByLabelText('Due date')).not.toBeInTheDocument()
    expect(within(dialog).queryByLabelText(/change status/i)).not.toBeInTheDocument()
    expect(dialog).toHaveTextContent('Due')
  })
})
