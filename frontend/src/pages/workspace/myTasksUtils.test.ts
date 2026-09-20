import { describe, expect, it } from 'vitest'
import {
  DEFAULT_FILTERS,
  addDaysIso,
  applyTaskFilters,
  daysUntil,
  dueHint,
  flattenTasks,
  hasActiveFilters,
  initialOf,
  isDueThisWeek,
  isOverdue,
  isPastDue,
  scopeTasks,
  shortId,
  showsPastDueMarker,
  sortTasks,
  summarizeTasks,
  type WorkspaceTask,
} from './myTasksUtils'
import type { ProjectSummary } from '../../types/project'
import type { Task } from '../../types/work'

const TODAY = '2026-09-19'
const ME = 'me-user'

const t = (over: Partial<Task>): Task => ({
  id: 'x', projectId: 'p1', name: 'x', description: null, dueDate: null, assigneeId: null, status: 'TODO',
  archived: false, createdAt: '', updatedAt: '', version: 0, ...over,
})
const projects: ProjectSummary[] = [
  { id: 'p1', name: 'Website Redesign', status: 'ACTIVE', priority: 'HIGH', ownerId: 'u', updatedAt: '' },
  { id: 'p2', name: 'Mobile App', status: 'PLANNING', priority: 'LOW', ownerId: 'u', updatedAt: '' },
]
const w = (task: Task, projectId = 'p1', projectName = 'Website Redesign'): WorkspaceTask => ({ task, projectId, projectName })

describe('myTasksUtils dates', () => {
  it('addDaysIso and daysUntil work across month boundaries', () => {
    expect(addDaysIso('2026-09-28', 7)).toBe('2026-10-05')
    expect(daysUntil('2026-10-01', TODAY)).toBe(12)
    expect(daysUntil('2026-09-17', TODAY)).toBe(-2)
  })

  it('dueHint gives a short relative label', () => {
    expect(dueHint(null, TODAY)).toBe('')
    expect(dueHint('2026-09-19', TODAY)).toBe('Today')
    expect(dueHint('2026-09-20', TODAY)).toBe('Tomorrow')
    expect(dueHint('2026-09-18', TODAY)).toBe('Yesterday')
    expect(dueHint('2026-09-22', TODAY)).toBe('in 3 days')
    expect(dueHint('2026-09-10', TODAY)).toBe('9 days ago')
  })
})

describe('flattenTasks', () => {
  it('joins each task with its project, skips archived tasks and projects that failed to load', () => {
    const out = flattenTasks(projects, {
      p1: [t({ id: 'a', name: 'A' }), t({ id: 'b', name: 'B', archived: true })],
      p2: null,
    })
    expect(out).toHaveLength(1)
    expect(out[0]).toMatchObject({ projectId: 'p1', projectName: 'Website Redesign', task: { id: 'a' } })
  })
})

describe('derived task state', () => {
  it('past due = due before today and not completed; never relabels the stored status', () => {
    expect(isPastDue(t({ dueDate: '2026-09-10' }), TODAY)).toBe(true)
    expect(isPastDue(t({ dueDate: '2026-09-19' }), TODAY)).toBe(false)
    expect(isPastDue(t({ dueDate: '2026-09-10', status: 'COMPLETED' }), TODAY)).toBe(false)
    expect(isPastDue(t({ dueDate: null }), TODAY)).toBe(false)
    // A BLOCKED task past its date is past due but keeps its BLOCKED status.
    const blocked = t({ dueDate: '2026-09-10', status: 'BLOCKED' })
    expect(isPastDue(blocked, TODAY)).toBe(true)
    expect(blocked.status).toBe('BLOCKED')
  })

  it('overdue = stored OVERDUE or past due, never for completed tasks', () => {
    expect(isOverdue(t({ status: 'OVERDUE' }), TODAY)).toBe(true)
    expect(isOverdue(t({ dueDate: '2026-09-01' }), TODAY)).toBe(true)
    expect(isOverdue(t({ dueDate: '2026-09-25' }), TODAY)).toBe(false)
    expect(isOverdue(t({ status: 'COMPLETED', dueDate: '2026-09-01' }), TODAY)).toBe(false)
  })

  it('due this week = today through +7 days, not completed', () => {
    expect(isDueThisWeek(t({ dueDate: '2026-09-19' }), TODAY)).toBe(true)
    expect(isDueThisWeek(t({ dueDate: '2026-09-26' }), TODAY)).toBe(true)
    expect(isDueThisWeek(t({ dueDate: '2026-09-27' }), TODAY)).toBe(false)
    expect(isDueThisWeek(t({ dueDate: '2026-09-18' }), TODAY)).toBe(false)
    expect(isDueThisWeek(t({ dueDate: '2026-09-20', status: 'COMPLETED' }), TODAY)).toBe(false)
  })

  it('the past-due marker is hidden when the stored status already says OVERDUE', () => {
    expect(showsPastDueMarker(t({ dueDate: '2026-09-10' }), TODAY)).toBe(true)
    expect(showsPastDueMarker(t({ dueDate: '2026-09-10', status: 'BLOCKED' }), TODAY)).toBe(true)
    expect(showsPastDueMarker(t({ dueDate: '2026-09-10', status: 'OVERDUE' }), TODAY)).toBe(false)
    expect(showsPastDueMarker(t({ dueDate: '2026-09-10', status: 'COMPLETED' }), TODAY)).toBe(false)
  })
})

describe('summarizeTasks', () => {
  const items = [
    w(t({ id: '1', assigneeId: ME, dueDate: '2026-09-10' })), // overdue (past due)
    w(t({ id: '2', assigneeId: ME, status: 'OVERDUE', dueDate: '2026-09-22' })), // overdue by status, not counted in week
    w(t({ id: '3', assigneeId: ME, dueDate: '2026-09-21' })), // due this week
    w(t({ id: '4', assigneeId: ME, status: 'BLOCKED' })), // blocked
    w(t({ id: '5', assigneeId: ME, status: 'COMPLETED', dueDate: '2026-09-01' })), // completed: ignored everywhere
    w(t({ id: '6', assigneeId: 'other', status: 'BLOCKED', dueDate: '2026-09-01' })), // someone else's
  ]

  it('counts open tasks only; overdue and due-this-week are mutually exclusive', () => {
    expect(summarizeTasks(items, ME, TODAY)).toEqual({ assignedToMe: 4, overdue: 3, dueThisWeek: 1, blocked: 2 })
  })

  it('reports 0 assigned without an identity but still counts the rest', () => {
    expect(summarizeTasks(items, null, TODAY)).toEqual({ assignedToMe: 0, overdue: 3, dueThisWeek: 1, blocked: 2 })
  })

  it('scopeTasks narrows to the identity only when both the toggle and an identity are set', () => {
    expect(scopeTasks(items, true, ME)).toHaveLength(5)
    expect(scopeTasks(items, false, ME)).toHaveLength(6)
    expect(scopeTasks(items, true, null)).toHaveLength(6)
  })
})

describe('applyTaskFilters', () => {
  const items = [
    w(t({ id: '1', name: 'Fix authentication bug', assigneeId: ME, dueDate: '2026-09-10' })),
    w(t({ id: '2', name: 'Write docs', assigneeId: ME, status: 'COMPLETED' })),
    w(t({ id: '3', name: 'Plan sprint', assigneeId: ME, dueDate: '2026-09-22' }), 'p2', 'Mobile App'),
    w(t({ id: '4', name: 'Migrate schema', assigneeId: 'other', status: 'BLOCKED', dueDate: '2026-09-15' }), 'p2', 'Mobile App'),
    w(t({ id: '5', name: 'Unassigned chore' })),
  ]
  const ids = (list: WorkspaceTask[]) => list.map((i) => i.task.id)

  it('defaults: mine only, completed hidden, sorted by due date with no-date last', () => {
    expect(ids(applyTaskFilters(items, DEFAULT_FILTERS, ME, 'dueDate,ASC', TODAY))).toEqual(['1', '3'])
  })

  it('shows everything open when the identity toggle is off', () => {
    expect(ids(applyTaskFilters(items, { ...DEFAULT_FILTERS, mineOnly: false }, ME, 'dueDate,ASC', TODAY))).toEqual(['1', '4', '3', '5'])
  })

  it('"Show completed" and an explicit COMPLETED status filter both reveal completed tasks', () => {
    expect(ids(applyTaskFilters(items, { ...DEFAULT_FILTERS, showCompleted: true }, ME, 'dueDate,ASC', TODAY))).toEqual(['1', '3', '2'])
    expect(ids(applyTaskFilters(items, { ...DEFAULT_FILTERS, status: 'COMPLETED' }, ME, 'dueDate,ASC', TODAY))).toEqual(['2'])
  })

  it('search, project, status and focus filters narrow the list', () => {
    const all = { ...DEFAULT_FILTERS, mineOnly: false }
    expect(ids(applyTaskFilters(items, { ...all, query: '  SPRINT ' }, ME, 'dueDate,ASC', TODAY))).toEqual(['3'])
    expect(ids(applyTaskFilters(items, { ...all, projectId: 'p2' }, ME, 'dueDate,ASC', TODAY))).toEqual(['4', '3'])
    expect(ids(applyTaskFilters(items, { ...all, status: 'BLOCKED' }, ME, 'dueDate,ASC', TODAY))).toEqual(['4'])
    expect(ids(applyTaskFilters(items, { ...all, focus: 'overdue' }, ME, 'dueDate,ASC', TODAY))).toEqual(['1', '4'])
    expect(ids(applyTaskFilters(items, { ...all, focus: 'week' }, ME, 'dueDate,ASC', TODAY))).toEqual(['3'])
    expect(ids(applyTaskFilters(items, { ...all, focus: 'blocked' }, ME, 'dueDate,ASC', TODAY))).toEqual(['4'])
  })

  it('sorts by due date both ways (no date last), by name and by project', () => {
    const open = items.filter((i) => i.task.status !== 'COMPLETED')
    expect(ids(sortTasks(open, 'dueDate,DESC'))).toEqual(['3', '4', '1', '5'])
    expect(ids(sortTasks(open, 'name,ASC'))).toEqual(['1', '4', '3', '5'])
    expect(ids(sortTasks(open, 'project,ASC'))).toEqual(['4', '3', '1', '5'])
  })

  it('hasActiveFilters ignores the two toggles', () => {
    expect(hasActiveFilters(DEFAULT_FILTERS)).toBe(false)
    expect(hasActiveFilters({ ...DEFAULT_FILTERS, mineOnly: false, showCompleted: true })).toBe(false)
    expect(hasActiveFilters({ ...DEFAULT_FILTERS, focus: 'week' })).toBe(true)
    expect(hasActiveFilters({ ...DEFAULT_FILTERS, query: ' x' })).toBe(true)
  })
})

describe('presentation helpers', () => {
  it('shortId and initialOf', () => {
    expect(shortId('11111111-2222-4333-8444-555555555555')).toBe('11111111…')
    expect(shortId('abc')).toBe('abc')
    expect(initialOf('zed')).toBe('Z')
    expect(initialOf(null)).toBe('–')
  })
})
