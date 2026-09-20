import type { ProjectSummary } from '../../types/project'
import type { Task, TaskStatus } from '../../types/work'

/**
 * Pure helpers for the cross-project My Tasks page. Everything here derives from real per-project
 * task lists (`tasksApi.list`); nothing is invented. The stored task status is always what gets
 * displayed — "past due" and "overdue" are secondary, derived markers (the backend's own
 * delay-detection rule: due date before today and not completed).
 */

export interface WorkspaceTask {
  task: Task
  projectId: string
  projectName: string
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
  return todayIso(new Date(y, m - 1, d + days))
}

/** Whole days from `today` to `iso` (negative when in the past). Both are local calendar dates. */
export function daysUntil(iso: string, today: string): number {
  const [y1, m1, d1] = today.split('-').map(Number)
  const [y2, m2, d2] = iso.split('-').map(Number)
  const a = new Date(y1, m1 - 1, d1).getTime()
  const b = new Date(y2, m2 - 1, d2).getTime()
  return Math.round((b - a) / 86_400_000)
}

/** Short relative hint for a due date: "Today", "Tomorrow", "in 3 days", "2 days ago". */
export function dueHint(iso: string | null, today: string): string {
  if (!iso) return ''
  const n = daysUntil(iso, today)
  if (n === 0) return 'Today'
  if (n === 1) return 'Tomorrow'
  if (n === -1) return 'Yesterday'
  return n > 0 ? `in ${n} days` : `${-n} days ago`
}

// ---------- Flattening ----------

/** Joins each loaded project's task list with the project it came from. Archived tasks are dropped. */
export function flattenTasks(projects: ProjectSummary[], byProject: Record<string, Task[] | null>): WorkspaceTask[] {
  const out: WorkspaceTask[] = []
  for (const project of projects) {
    for (const task of byProject[project.id] ?? []) {
      if (task.archived) continue
      out.push({ task, projectId: project.id, projectName: project.name })
    }
  }
  return out
}

// ---------- Derived state ----------

/** Due date has passed and the task is not completed — independent of the stored status. */
export function isPastDue(task: Task, today: string): boolean {
  return task.status !== 'COMPLETED' && task.dueDate !== null && task.dueDate < today
}

/** Overdue by stored status OR by the delay-detection rule. Completed tasks are never overdue. */
export function isOverdue(task: Task, today: string): boolean {
  if (task.status === 'COMPLETED') return false
  return task.status === 'OVERDUE' || isPastDue(task, today)
}

/** Due within the next 7 days (today inclusive) and not completed. */
export function isDueThisWeek(task: Task, today: string): boolean {
  if (task.status === 'COMPLETED' || task.dueDate === null) return false
  return task.dueDate >= today && task.dueDate <= addDaysIso(today, 7)
}

/**
 * Whether to show the small "past due" marker next to the status badge. Not shown when the stored
 * status is already OVERDUE (that would say the same thing twice) or COMPLETED.
 */
export function showsPastDueMarker(task: Task, today: string): boolean {
  return isPastDue(task, today) && task.status !== 'OVERDUE'
}

export function isMine(task: Task, userId: string | null): boolean {
  return userId !== null && task.assigneeId === userId
}

// ---------- Filters & sort ----------

/** Derived quick views driven by the summary chips. '' = no focus. */
export type TaskFocus = '' | 'overdue' | 'week' | 'blocked'

export interface MyTasksFilters {
  query: string
  projectId: string
  status: TaskStatus | ''
  /** Only tasks whose assigneeId equals the current identity. Ignored when there is no identity. */
  mineOnly: boolean
  /** Completed tasks are hidden by default; an explicit status filter of COMPLETED always wins. */
  showCompleted: boolean
  focus: TaskFocus
}

export const DEFAULT_FILTERS: MyTasksFilters = {
  query: '',
  projectId: '',
  status: '',
  mineOnly: true,
  showCompleted: false,
  focus: '',
}

/** True when a filter that narrows the list beyond the two toggles is active. */
export function hasActiveFilters(f: MyTasksFilters): boolean {
  return f.query.trim() !== '' || f.projectId !== '' || f.status !== '' || f.focus !== ''
}

export type MyTasksSort = 'dueDate,ASC' | 'dueDate,DESC' | 'name,ASC' | 'project,ASC'

export const MY_TASKS_SORTS: { value: MyTasksSort; label: string }[] = [
  { value: 'dueDate,ASC', label: 'Due date: earliest' },
  { value: 'dueDate,DESC', label: 'Due date: latest' },
  { value: 'name,ASC', label: 'Name: A to Z' },
  { value: 'project,ASC', label: 'Project' },
]

/** The "Assigned to me" scope; everything else (chips, table) is derived from this list. */
export function scopeTasks(items: WorkspaceTask[], mineOnly: boolean, userId: string | null): WorkspaceTask[] {
  if (!mineOnly || userId === null) return items
  return items.filter(({ task }) => task.assigneeId === userId)
}

export interface MyTasksSummary {
  /** Open (not completed) tasks assigned to the current identity; 0 without an identity. */
  assignedToMe: number
  overdue: number
  dueThisWeek: number
  blocked: number
}

/** Counts over the scoped list. Overdue and due-this-week are mutually exclusive; blocked is by stored status. */
export function summarizeTasks(items: WorkspaceTask[], userId: string | null, today: string): MyTasksSummary {
  const s: MyTasksSummary = { assignedToMe: 0, overdue: 0, dueThisWeek: 0, blocked: 0 }
  for (const { task } of items) {
    if (task.status === 'COMPLETED') continue
    if (isMine(task, userId)) s.assignedToMe++
    if (task.status === 'BLOCKED') s.blocked++
    if (isOverdue(task, today)) s.overdue++
    else if (isDueThisWeek(task, today)) s.dueThisWeek++
  }
  return s
}

function matchesFocus(task: Task, focus: TaskFocus, today: string): boolean {
  switch (focus) {
    case 'overdue':
      return isOverdue(task, today)
    case 'week':
      return !isOverdue(task, today) && isDueThisWeek(task, today)
    case 'blocked':
      return task.status === 'BLOCKED'
    default:
      return true
  }
}

function compareDue(a: WorkspaceTask, b: WorkspaceTask): number {
  // Tasks without a due date sort last in either direction.
  const da = a.task.dueDate
  const db = b.task.dueDate
  if (da === db) return a.task.name.localeCompare(b.task.name)
  if (da === null) return 1
  if (db === null) return -1
  return da < db ? -1 : 1
}

export function sortTasks(items: WorkspaceTask[], sort: MyTasksSort): WorkspaceTask[] {
  const copy = [...items]
  switch (sort) {
    case 'dueDate,ASC':
      return copy.sort(compareDue)
    case 'dueDate,DESC':
      return copy.sort((a, b) => {
        // Keep "no due date" last even when descending.
        if (a.task.dueDate === null || b.task.dueDate === null) return compareDue(a, b)
        return compareDue(b, a)
      })
    case 'name,ASC':
      return copy.sort((a, b) => a.task.name.localeCompare(b.task.name))
    case 'project,ASC':
      return copy.sort((a, b) => a.projectName.localeCompare(b.projectName) || compareDue(a, b))
  }
}

/** Applies every filter (including the identity scope) and the sort to the flattened list. */
export function applyTaskFilters(
  items: WorkspaceTask[],
  f: MyTasksFilters,
  userId: string | null,
  sort: MyTasksSort,
  today: string,
): WorkspaceTask[] {
  const q = f.query.trim().toLowerCase()
  const filtered = scopeTasks(items, f.mineOnly, userId).filter(({ task, projectId }) => {
    if (q && !task.name.toLowerCase().includes(q)) return false
    if (f.projectId && projectId !== f.projectId) return false
    if (f.status && task.status !== f.status) return false
    if (!f.showCompleted && f.status !== 'COMPLETED' && task.status === 'COMPLETED') return false
    return matchesFocus(task, f.focus, today)
  })
  return sortTasks(filtered, sort)
}

// ---------- Presentation helpers ----------

/** Opaque ids are long; the first 8 characters are what the rest of the app shows. */
export function shortId(id: string): string {
  return id.length > 8 ? `${id.slice(0, 8)}…` : id
}

export function initialOf(id: string | null): string {
  return id ? id.charAt(0).toUpperCase() : '–'
}
