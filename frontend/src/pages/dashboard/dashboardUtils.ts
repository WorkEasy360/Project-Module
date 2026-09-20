import type { ProjectPriority, ProjectStatus, ProjectSummary } from '../../types/project'
import type { Calendar, Milestone, ProjectHealth, Task } from '../../types/work'
import type { AIRecommendation } from '../../types/ai'

/** One loaded project with everything the dashboard fetched for it (each may fail independently). */
export interface LoadedProject {
  project: ProjectSummary
  health: ProjectHealth | null
  tasks: Task[] | null
  milestones: Milestone[] | null
}

/** Kept for callers that only need project + health. */
export type ProjectWithHealth = Pick<LoadedProject, 'project' | 'health'>

// ---------- Sorting (server-side, Spring Pageable `sort=field,DIR` over real Project fields) ----------

export const SORT_OPTIONS = [
  { value: 'updatedAt,DESC', label: 'Last updated' },
  { value: 'updatedAt,ASC', label: 'Least recently updated' },
  { value: 'name,ASC', label: 'Name: A to Z' },
  { value: 'name,DESC', label: 'Name: Z to A' },
  { value: 'createdAt,DESC', label: 'Created: newest first' },
  { value: 'createdAt,ASC', label: 'Created: oldest first' },
  { value: 'priority,DESC', label: 'Priority: highest first' },
] as const

export type SortValue = (typeof SORT_OPTIONS)[number]['value']

// ---------- Project filters (client-side over the loaded page) ----------

export interface ProjectFilters {
  query: string
  status: ProjectStatus | ''
  priority: ProjectPriority | ''
}

export const EMPTY_FILTERS: ProjectFilters = { query: '', status: '', priority: '' }

export function hasActiveFilters(f: ProjectFilters): boolean {
  return f.query.trim() !== '' || f.status !== '' || f.priority !== ''
}

export function applyFilters<T extends ProjectWithHealth>(items: T[], f: ProjectFilters): T[] {
  const q = f.query.trim().toLowerCase()
  return items.filter(({ project }) => {
    if (q && !project.name.toLowerCase().includes(q)) return false
    if (f.status && project.status !== f.status) return false
    if (f.priority && project.priority !== f.priority) return false
    return true
  })
}

// ---------- Health ----------

export function attentionCount(h: ProjectHealth): number {
  return h.overdueItemCount + h.blockedTaskCount + h.unresolvedRiskCount + h.unresolvedIssueCount + h.brokenDependencyCount
}

export type HealthLevel = 'critical' | 'warning' | 'healthy' | 'unknown'

export function healthLevel(h: ProjectHealth | null): HealthLevel {
  if (!h) return 'unknown'
  // Red for overdue, blocked and broken links; amber for open risks/issues — the same semantic
  // colours the status badges use, so "blocked" is never amber here and red elsewhere.
  if (h.overdueItemCount > 0 || h.blockedTaskCount > 0 || h.brokenDependencyCount > 0) return 'critical'
  if (h.unresolvedRiskCount > 0 || h.unresolvedIssueCount > 0) return 'warning'
  return 'healthy'
}

export const HEALTH_LABEL: Record<HealthLevel, string> = {
  critical: 'Needs attention',
  warning: 'Watch',
  healthy: 'On track',
  unknown: 'Health unavailable',
}

// ---------- Dates ----------

export function todayIso(now: Date = new Date()): string {
  const y = now.getFullYear()
  const m = String(now.getMonth() + 1).padStart(2, '0')
  const d = String(now.getDate()).padStart(2, '0')
  return `${y}-${m}-${d}`
}

export function addDaysIso(iso: string, days: number): string {
  const [y, m, d] = iso.split('-').map(Number)
  const date = new Date(y, m - 1, d + days)
  return todayIso(date)
}

// ---------- Tasks needing attention (derived from real per-project task lists) ----------

export interface AttentionTask {
  task: Task
  projectName: string
  /**
   * Why the task is listed. The stored status is always what gets displayed (a BLOCKED task is
   * never relabelled "Overdue"); `reason` is BLOCKED or OVERDUE by stored status, or OVERDUE when a
   * TODO task is past its due date (the delay-detection rule).
   */
  reason: 'OVERDUE' | 'BLOCKED'
  /** Derived, independent of status: due date is before today. Shown as a secondary marker. */
  pastDue: boolean
}

export function collectAttentionTasks(items: LoadedProject[], today: string): AttentionTask[] {
  const out: AttentionTask[] = []
  for (const { project, tasks } of items) {
    for (const task of tasks ?? []) {
      if (task.archived || task.status === 'COMPLETED') continue
      const pastDue = task.dueDate !== null && task.dueDate < today
      if (task.status === 'BLOCKED') {
        out.push({ task, projectName: project.name, reason: 'BLOCKED', pastDue })
      } else if (task.status === 'OVERDUE' || pastDue) {
        out.push({ task, projectName: project.name, reason: 'OVERDUE', pastDue })
      }
    }
  }
  return out
}

export interface AttentionFilters {
  query: string
  reason: 'OVERDUE' | 'BLOCKED' | ''
  projectId: string
  assigneeId: string
}

export const EMPTY_ATTENTION_FILTERS: AttentionFilters = { query: '', reason: '', projectId: '', assigneeId: '' }

export type AttentionSort = 'dueDate,ASC' | 'dueDate,DESC' | 'name,ASC'

export const ATTENTION_SORTS: { value: AttentionSort; label: string }[] = [
  { value: 'dueDate,ASC', label: 'Due date: earliest' },
  { value: 'dueDate,DESC', label: 'Due date: latest' },
  { value: 'name,ASC', label: 'Title: A to Z' },
]

export function filterAttention(list: AttentionTask[], f: AttentionFilters, sort: AttentionSort): AttentionTask[] {
  const q = f.query.trim().toLowerCase()
  const filtered = list.filter(({ task, reason }) => {
    if (q && !task.name.toLowerCase().includes(q)) return false
    if (f.reason && reason !== f.reason) return false
    if (f.projectId && task.projectId !== f.projectId) return false
    if (f.assigneeId && task.assigneeId !== f.assigneeId) return false
    return true
  })
  const byDue = (a: AttentionTask, b: AttentionTask) => {
    // Tasks without a due date sort last in either direction.
    if (a.task.dueDate === b.task.dueDate) return a.task.name.localeCompare(b.task.name)
    if (a.task.dueDate === null) return 1
    if (b.task.dueDate === null) return -1
    return a.task.dueDate < b.task.dueDate ? -1 : 1
  }
  return [...filtered].sort((a, b) => {
    if (sort === 'name,ASC') return a.task.name.localeCompare(b.task.name)
    return sort === 'dueDate,ASC' ? byDue(a, b) : byDue(b, a)
  })
}

// ---------- My Tasks (derived; scoped to the loaded projects and the current identity) ----------

export interface MyTaskCounts {
  overdue: number
  dueThisWeek: number
  assignedToMe: number
}

export function myTaskCounts(items: LoadedProject[], userId: string | null, today: string): MyTaskCounts {
  const weekEnd = addDaysIso(today, 7)
  const counts: MyTaskCounts = { overdue: 0, dueThisWeek: 0, assignedToMe: 0 }
  if (!userId) return counts
  for (const { tasks } of items) {
    for (const t of tasks ?? []) {
      if (t.archived || t.assigneeId !== userId) continue
      counts.assignedToMe++
      if (t.status === 'COMPLETED') continue
      if (t.status === 'OVERDUE' || (t.dueDate !== null && t.dueDate < today)) counts.overdue++
      else if (t.dueDate !== null && t.dueDate >= today && t.dueDate <= weekEnd) counts.dueThisWeek++
    }
  }
  return counts
}

// ---------- Progress (completed / all non-archived tasks) ----------

export function progressPercent(tasks: Task[] | null): number | null {
  if (!tasks) return null
  const live = tasks.filter((t) => !t.archived)
  if (live.length === 0) return null
  return Math.round((live.filter((t) => t.status === 'COMPLETED').length / live.length) * 100)
}

// ---------- Upcoming milestones ----------

export interface UpcomingMilestone {
  milestone: Milestone
  projectId: string
  projectName: string
}

export function upcomingMilestones(items: LoadedProject[], today: string, limit = 5): UpcomingMilestone[] {
  const out: UpcomingMilestone[] = []
  for (const { project, milestones } of items) {
    for (const m of milestones ?? []) {
      if (m.archived || m.status === 'COMPLETED' || !m.dueDate || m.dueDate < today) continue
      out.push({ milestone: m, projectId: project.id, projectName: project.name })
    }
  }
  return out.sort((a, b) => (a.milestone.dueDate! < b.milestone.dueDate! ? -1 : 1)).slice(0, limit)
}

// ---------- Status donut ----------

export interface DonutSegment {
  status: ProjectStatus
  label: string
  count: number
  percent: number
  color: string
}

/**
 * Donut colours, validated for colour-vision deficiency and >= 3:1 contrast on both the light and
 * dark panel surfaces (the previous blue/purple pair was indistinguishable under deuteranopia).
 * Cancelled is deliberately a low-chroma gray so an inert status reads as inert.
 */
export const STATUS_COLORS: Record<ProjectStatus, string> = {
  ACTIVE: '#2563eb',
  ON_HOLD: '#db2777',
  PLANNING: '#d97706',
  COMPLETED: '#16a34a',
  CANCELLED: '#64748b',
}

const STATUS_LABELS: Record<ProjectStatus, string> = {
  ACTIVE: 'Active',
  ON_HOLD: 'On Hold',
  PLANNING: 'Planning',
  COMPLETED: 'Completed',
  CANCELLED: 'Cancelled',
}

export function donutSegments(byStatus: Record<ProjectStatus, number>): DonutSegment[] {
  const total = Object.values(byStatus).reduce((a, b) => a + b, 0)
  return (Object.keys(STATUS_LABELS) as ProjectStatus[])
    .map((status) => ({
      status,
      label: STATUS_LABELS[status],
      count: byStatus[status] ?? 0,
      percent: total === 0 ? 0 : Math.round(((byStatus[status] ?? 0) / total) * 100),
      color: STATUS_COLORS[status],
    }))
    .filter((s) => s.count > 0)
}

/**
 * CSS conic-gradient stops for the donut, in legend order. With two or more segments a thin
 * panel-coloured gap (`gapColor`) separates neighbours so adjacent hues never touch — the
 * secondary encoding that keeps the chart readable for colour-blind readers.
 */
export function donutGradient(segments: DonutSegment[], gapColor = 'var(--hb-panel)'): string {
  const total = segments.reduce((a, s) => a + s.count, 0)
  if (total === 0) return 'conic-gradient(#e0e3e8 0 100%)'
  const gap = segments.length > 1 ? 0.8 : 0
  let acc = 0
  const stops = segments.map((s) => {
    const start = (acc / total) * 100
    acc += s.count
    const end = (acc / total) * 100
    const from = Math.min(start + gap / 2, end).toFixed(2)
    const to = Math.max(end - gap / 2, start).toFixed(2)
    return gap ? `${gapColor} ${start.toFixed(2)}% ${from}%, ${s.color} ${from}% ${to}%, ${gapColor} ${to}% ${end.toFixed(2)}%` : `${s.color} ${start.toFixed(2)}% ${end.toFixed(2)}%`
  })
  return `conic-gradient(${stops.join(', ')})`
}

export function greetingForHour(hour: number): string {
  if (hour < 12) return 'Good morning'
  if (hour < 18) return 'Good afternoon'
  return 'Good evening'
}

/** First character of an opaque id, for the small avatar circles. */
export function initialOf(id: string | null): string {
  return id ? id.charAt(0).toUpperCase() : '–'
}

// ---------- Upcoming (dated tasks + milestones across the loaded projects) ----------

export interface UpcomingItem {
  kind: 'TASK' | 'MILESTONE'
  id: string
  name: string
  /** YYYY-MM-DD */
  date: string
  projectId: string
  projectName: string
  href: string
  /** A milestone the backend has marked AT_RISK. */
  atRisk: boolean
}

/**
 * Open tasks and pending/at-risk milestones due today or later, soonest first. Tasks link to
 * their page; milestones to the project's milestones tab (there is no milestone detail page).
 */
export function upcomingItems(items: LoadedProject[], today: string, limit = 6): UpcomingItem[] {
  const out: UpcomingItem[] = []
  for (const { project, tasks, milestones } of items) {
    for (const t of tasks ?? []) {
      if (t.archived || t.status === 'COMPLETED' || !t.dueDate || t.dueDate < today) continue
      out.push({ kind: 'TASK', id: t.id, name: t.name, date: t.dueDate, projectId: project.id, projectName: project.name, href: `/projects/${project.id}/tasks/${t.id}`, atRisk: false })
    }
    for (const m of milestones ?? []) {
      if (m.archived || m.status === 'COMPLETED' || !m.dueDate || m.dueDate < today) continue
      out.push({ kind: 'MILESTONE', id: m.id, name: m.name, date: m.dueDate, projectId: project.id, projectName: project.name, href: `/projects/${project.id}/milestones`, atRisk: m.status === 'AT_RISK' })
    }
  }
  return out
    .sort((a, b) => (a.date === b.date ? a.name.localeCompare(b.name) : a.date < b.date ? -1 : 1))
    .slice(0, limit)
}

// ---------- Mini calendar (aggregated /calendar windows of the loaded projects) ----------

export interface DayMarks {
  tasks: number
  milestones: number
}

/**
 * Counts task and milestone calendar entries per day inside [from, to]. Phases (date ranges) are
 * not marked: the mini calendar only shows point items, as its legend says. A null calendar
 * (one project's request failed) contributes nothing rather than breaking the month.
 */
export function calendarDayMarks(calendars: (Calendar | null)[], from: string, to: string): Map<string, DayMarks> {
  const map = new Map<string, DayMarks>()
  for (const cal of calendars) {
    for (const e of cal?.entries ?? []) {
      if (!e.date || e.date < from || e.date > to) continue
      if (e.entityType !== 'TASK' && e.entityType !== 'MILESTONE') continue
      const marks = map.get(e.date) ?? { tasks: 0, milestones: 0 }
      if (e.entityType === 'TASK') marks.tasks++
      else marks.milestones++
      map.set(e.date, marks)
    }
  }
  return map
}

// ---------- Project card meta ----------

/** The latest due date among a project's non-archived tasks, or null when none has one. */
export function latestDueDate(tasks: Task[] | null): string | null {
  let latest: string | null = null
  for (const t of tasks ?? []) {
    if (t.archived || !t.dueDate) continue
    if (latest === null || t.dueDate > latest) latest = t.dueDate
  }
  return latest
}

// ---------- AI recommendations (per-project /ai/recommendations) ----------

export interface ProjectRecommendations {
  projectId: string
  projectName: string
  /** null when the request failed. */
  recommendations: AIRecommendation[] | null
  /** The backend answered 503: no AI provider is configured. */
  unavailable: boolean
}

export interface PendingRecommendation {
  recommendation: AIRecommendation
  projectId: string
  projectName: string
}

/** The newest PENDING recommendations across the loaded projects. */
export function pendingRecommendations(results: ProjectRecommendations[], limit = 4): PendingRecommendation[] {
  const out: PendingRecommendation[] = []
  for (const r of results) {
    for (const rec of r.recommendations ?? []) {
      if (rec.status === 'PENDING') out.push({ recommendation: rec, projectId: r.projectId, projectName: r.projectName })
    }
  }
  return out.sort((a, b) => (a.recommendation.createdAt < b.recommendation.createdAt ? 1 : -1)).slice(0, limit)
}
