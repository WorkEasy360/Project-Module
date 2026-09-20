import { describe, expect, it } from 'vitest'
import {
  EMPTY_ATTENTION_FILTERS,
  EMPTY_FILTERS,
  SORT_OPTIONS,
  applyFilters,
  calendarDayMarks,
  collectAttentionTasks,
  donutGradient,
  donutSegments,
  filterAttention,
  greetingForHour,
  hasActiveFilters,
  healthLevel,
  latestDueDate,
  myTaskCounts,
  pendingRecommendations,
  progressPercent,
  upcomingItems,
  upcomingMilestones,
  type LoadedProject,
} from './dashboardUtils'
import type { Milestone, ProjectHealth, Task } from '../../types/work'
import type { AIRecommendation } from '../../types/ai'

const health = (over: Partial<ProjectHealth> = {}): ProjectHealth => ({
  overdueItemCount: 0, unresolvedRiskCount: 0, unresolvedIssueCount: 0, blockedTaskCount: 0, brokenDependencyCount: 0, ...over,
})
const task = (over: Partial<Task>): Task => ({
  id: 't', projectId: 'p', name: 'T', description: null, dueDate: null, assigneeId: null, status: 'TODO',
  archived: false, createdAt: '', updatedAt: '', version: 0, ...over,
})
const milestone = (over: Partial<Milestone>): Milestone => ({
  id: 'm', projectId: 'p', phaseId: null, name: 'M', description: null, dueDate: null, status: 'PENDING',
  archived: false, createdAt: '', updatedAt: '', version: 0, ...over,
})
const loaded = (name: string, status: LoadedProject['project']['status'], priority: LoadedProject['project']['priority'], tasks: Task[] | null = [], ms: Milestone[] | null = [], h: ProjectHealth | null = health()): LoadedProject => ({
  project: { id: name, name, status, priority, ownerId: 'u', updatedAt: '2026-09-01T00:00:00Z' },
  health: h,
  tasks,
  milestones: ms,
})

const TODAY = '2026-09-19'

describe('project filters & sort options', () => {
  const items = [loaded('Website', 'ACTIVE', 'HIGH'), loaded('Mobile', 'PLANNING', 'MEDIUM'), loaded('Data', 'ACTIVE', 'LOW')]
  it('search / status / priority combine with AND semantics', () => {
    expect(applyFilters(items, { ...EMPTY_FILTERS, query: 'mob' }).map((i) => i.project.name)).toEqual(['Mobile'])
    expect(applyFilters(items, { ...EMPTY_FILTERS, status: 'ACTIVE' })).toHaveLength(2)
    expect(applyFilters(items, { query: 'web', status: 'ACTIVE', priority: 'HIGH' })).toHaveLength(1)
    expect(applyFilters(items, { query: 'web', status: 'PLANNING', priority: '' })).toHaveLength(0)
    expect(hasActiveFilters({ ...EMPTY_FILTERS, query: '  ' })).toBe(false)
  })
  it('only offers backend-sortable fields', () => {
    for (const o of SORT_OPTIONS) expect(o.value).toMatch(/^(updatedAt|createdAt|name|priority),(ASC|DESC)$/)
  })
})

describe('attention tasks', () => {
  const items = [
    loaded('Website', 'ACTIVE', 'HIGH', [
      task({ id: 'a', projectId: 'Website', name: 'Fix auth bug', dueDate: '2026-09-10', status: 'TODO', assigneeId: 'u1' }),
      task({ id: 'b', projectId: 'Website', name: 'Done thing', dueDate: '2026-09-01', status: 'COMPLETED' }),
      task({ id: 'c', projectId: 'Website', name: 'Blocked thing', status: 'BLOCKED', assigneeId: 'u2' }),
      task({ id: 'd', projectId: 'Website', name: 'Future', dueDate: '2026-10-01' }),
      task({ id: 'e', projectId: 'Website', name: 'Explicitly overdue', status: 'OVERDUE', dueDate: '2026-09-15' }),
    ]),
    loaded('Mobile', 'PLANNING', 'MEDIUM', [task({ id: 'f', projectId: 'Mobile', name: 'Archived', dueDate: '2026-01-01', archived: true })]),
  ]
  it('collects overdue (delay-detection rule) and blocked tasks, never completed/archived/future ones', () => {
    const out = collectAttentionTasks(items, TODAY)
    expect(out.map((o) => `${o.task.id}:${o.reason}`)).toEqual(['a:OVERDUE', 'c:BLOCKED', 'e:OVERDUE'])
    expect(out[0].projectName).toBe('Website')
  })
  it('never relabels a BLOCKED task as overdue: stored status wins, past-due is a separate flag', () => {
    const blockedAndLate = [loaded('P', 'ACTIVE', 'LOW', [task({ id: 'z', status: 'BLOCKED', dueDate: '2026-01-01' })])]
    const [only] = collectAttentionTasks(blockedAndLate, TODAY)
    expect(only.reason).toBe('BLOCKED')
    expect(only.task.status).toBe('BLOCKED')
    expect(only.pastDue).toBe(true)
    const [fresh] = collectAttentionTasks([loaded('P', 'ACTIVE', 'LOW', [task({ id: 'y', status: 'OVERDUE', dueDate: '2026-12-01' })])], TODAY)
    expect(fresh.reason).toBe('OVERDUE')
    expect(fresh.pastDue).toBe(false)
  })
  it('filters by search, reason, project and assignee and sorts by due date with undated last', () => {
    const all = collectAttentionTasks(items, TODAY)
    expect(filterAttention(all, EMPTY_ATTENTION_FILTERS, 'dueDate,ASC').map((o) => o.task.id)).toEqual(['a', 'e', 'c'])
    expect(filterAttention(all, EMPTY_ATTENTION_FILTERS, 'dueDate,DESC').map((o) => o.task.id)).toEqual(['c', 'e', 'a'])
    expect(filterAttention(all, { ...EMPTY_ATTENTION_FILTERS, reason: 'BLOCKED' }, 'dueDate,ASC')).toHaveLength(1)
    expect(filterAttention(all, { ...EMPTY_ATTENTION_FILTERS, assigneeId: 'u1' }, 'dueDate,ASC').map((o) => o.task.id)).toEqual(['a'])
    expect(filterAttention(all, { ...EMPTY_ATTENTION_FILTERS, query: 'AUTH' }, 'name,ASC')).toHaveLength(1)
    expect(filterAttention(all, { ...EMPTY_ATTENTION_FILTERS, projectId: 'Mobile' }, 'name,ASC')).toHaveLength(0)
  })
})

describe('my tasks, progress, milestones, donut', () => {
  it('counts only the current identity’s tasks, using the same overdue rule and a 7-day window', () => {
    const items = [
      loaded('P', 'ACTIVE', 'LOW', [
        task({ id: '1', assigneeId: 'me', dueDate: '2026-09-10' }),
        task({ id: '2', assigneeId: 'me', dueDate: '2026-09-22' }),
        task({ id: '3', assigneeId: 'me', dueDate: '2026-09-27' }),
        task({ id: '4', assigneeId: 'me', status: 'COMPLETED', dueDate: '2026-09-01' }),
        task({ id: '5', assigneeId: 'someone-else', dueDate: '2026-09-01' }),
      ]),
    ]
    expect(myTaskCounts(items, 'me', TODAY)).toEqual({ overdue: 1, dueThisWeek: 1, assignedToMe: 4 })
    expect(myTaskCounts(items, null, TODAY)).toEqual({ overdue: 0, dueThisWeek: 0, assignedToMe: 0 })
  })
  it('derives progress from completed / non-archived tasks, or null when unknown/empty', () => {
    expect(progressPercent([task({ status: 'COMPLETED' }), task({}), task({ archived: true })])).toBe(50)
    expect(progressPercent([])).toBeNull()
    expect(progressPercent(null)).toBeNull()
  })
  it('lists pending/at-risk milestones due today or later, soonest first, capped', () => {
    const items = [
      loaded('A', 'ACTIVE', 'LOW', [], [
        milestone({ id: 'm1', name: 'Kickoff', dueDate: '2026-10-10' }),
        milestone({ id: 'm2', name: 'Past', dueDate: '2026-09-01' }),
        milestone({ id: 'm3', name: 'Done', dueDate: '2026-10-01', status: 'COMPLETED' }),
        milestone({ id: 'm4', name: 'Beta', dueDate: '2026-09-25', status: 'AT_RISK' }),
      ]),
    ]
    expect(upcomingMilestones(items, TODAY).map((u) => u.milestone.id)).toEqual(['m4', 'm1'])
    expect(upcomingMilestones(items, TODAY, 1)).toHaveLength(1)
  })
  it('builds donut segments and a conic-gradient only from non-zero statuses, with surface gaps between neighbours', () => {
    const segs = donutSegments({ PLANNING: 3, ACTIVE: 6, ON_HOLD: 0, COMPLETED: 2, CANCELLED: 0 })
    expect(segs.map((s) => `${s.label}:${s.count}:${s.percent}`)).toEqual(['Active:6:55', 'Planning:3:27', 'Completed:2:18'])
    expect(donutGradient(segs, '#fff')).toBe(
      'conic-gradient(#fff 0.00% 0.40%, #2563eb 0.40% 54.15%, #fff 54.15% 54.55%, #fff 54.55% 54.95%, #d97706 54.95% 81.42%, #fff 81.42% 81.82%, #fff 81.82% 82.22%, #16a34a 82.22% 99.60%, #fff 99.60% 100.00%)',
    )
    // A single segment is a full ring with no gap; the gap colour defaults to the panel token.
    expect(donutGradient(donutSegments({ PLANNING: 0, ACTIVE: 4, ON_HOLD: 0, COMPLETED: 0, CANCELLED: 0 }))).toBe('conic-gradient(#2563eb 0.00% 100.00%)')
    expect(donutGradient(segs)).toContain('var(--hb-panel) 0.00% 0.40%')
    expect(donutGradient([])).toContain('#e0e3e8')
  })
  it('health level and greeting', () => {
    expect(healthLevel(health({ overdueItemCount: 1 }))).toBe('critical')
    expect(healthLevel(health({ blockedTaskCount: 1 }))).toBe('critical')
    expect(healthLevel(health({ unresolvedIssueCount: 1 }))).toBe('warning')
    expect(healthLevel(health())).toBe('healthy')
    expect(greetingForHour(9)).toBe('Good morning')
  })
})

describe('upcoming, calendar marks, card meta, AI', () => {
  it('merges dated open tasks and pending/at-risk milestones due today or later, soonest first, capped', () => {
    const items = [
      loaded('A', 'ACTIVE', 'LOW', [
        task({ id: 't1', projectId: 'A', name: 'Later', dueDate: '2026-09-30' }),
        task({ id: 't2', projectId: 'A', name: 'Today', dueDate: TODAY }),
        task({ id: 't3', projectId: 'A', name: 'Past', dueDate: '2026-09-01' }),
        task({ id: 't4', projectId: 'A', name: 'Done', dueDate: '2026-09-25', status: 'COMPLETED' }),
        task({ id: 't5', projectId: 'A', name: 'Undated' }),
        task({ id: 't6', projectId: 'A', name: 'Archived', dueDate: '2026-09-21', archived: true }),
      ], [
        milestone({ id: 'm1', name: 'Beta', dueDate: '2026-09-25', status: 'AT_RISK' }),
        milestone({ id: 'm2', name: 'Old', dueDate: '2026-09-02' }),
        milestone({ id: 'm3', name: 'Shipped', dueDate: '2026-10-01', status: 'COMPLETED' }),
      ]),
    ]
    const out = upcomingItems(items, TODAY)
    expect(out.map((u) => `${u.kind}:${u.id}:${u.date}`)).toEqual(['TASK:t2:2026-09-19', 'MILESTONE:m1:2026-09-25', 'TASK:t1:2026-09-30'])
    expect(out[0].href).toBe('/projects/A/tasks/t2')
    expect(out[1]).toMatchObject({ href: '/projects/A/milestones', atRisk: true, projectName: 'A' })
    expect(upcomingItems(items, TODAY, 1)).toHaveLength(1)
  })

  it('counts task and milestone calendar entries per day inside the window; phases and failed projects add nothing', () => {
    const e = (entityType: string, id: string, date: string | null, range?: [string, string]) => ({
      entityType, id, name: id, date, startDate: range?.[0] ?? null, endDate: range?.[1] ?? null,
    })
    const marks = calendarDayMarks(
      [
        { entries: [e('TASK', 'a', '2026-09-10'), e('TASK', 'b', '2026-09-10'), e('MILESTONE', 'c', '2026-09-10'), e('TASK', 'out', '2026-10-01')] },
        null,
        { entries: [e('PHASE', 'p', null, ['2026-09-01', '2026-09-30']), e('MILESTONE', 'd', '2026-09-22')] },
      ],
      '2026-09-01',
      '2026-09-30',
    )
    expect(marks.get('2026-09-10')).toEqual({ tasks: 2, milestones: 1 })
    expect(marks.get('2026-09-22')).toEqual({ tasks: 0, milestones: 1 })
    expect(marks.get('2026-10-01')).toBeUndefined()
    expect(marks.size).toBe(2)
  })

  it('latest due date ignores archived and undated tasks', () => {
    expect(latestDueDate([task({ dueDate: '2026-09-10' }), task({ dueDate: '2026-11-01', archived: true }), task({ dueDate: '2026-10-05' }), task({})])).toBe('2026-10-05')
    expect(latestDueDate([task({})])).toBeNull()
    expect(latestDueDate(null)).toBeNull()
  })

  it('collects the newest PENDING recommendations across projects, skipping failed ones', () => {
    const rec = (over: Partial<AIRecommendation>): AIRecommendation => ({
      id: 'r', projectId: 'p', type: 'RISK_ANALYSIS', resourceId: null, question: null, title: null, rationale: null, payload: null,
      status: 'PENDING', requestedBy: 'u', respondedBy: null, respondedAt: null, createdAt: '2026-09-01T00:00:00Z', updatedAt: '', version: 0, ...over,
    })
    const out = pendingRecommendations([
      { projectId: 'a', projectName: 'A', unavailable: false, recommendations: [rec({ id: '1', createdAt: '2026-09-01T00:00:00Z' }), rec({ id: '2', status: 'ACCEPTED', createdAt: '2026-09-09T00:00:00Z' })] },
      { projectId: 'b', projectName: 'B', unavailable: true, recommendations: null },
      { projectId: 'c', projectName: 'C', unavailable: false, recommendations: [rec({ id: '3', createdAt: '2026-09-05T00:00:00Z' })] },
    ])
    expect(out.map((o) => `${o.recommendation.id}@${o.projectName}`)).toEqual(['3@C', '1@A'])
    expect(pendingRecommendations([{ projectId: 'c', projectName: 'C', unavailable: false, recommendations: [rec({ id: '3' }), rec({ id: '4' })] }], 1)).toHaveLength(1)
  })
})
