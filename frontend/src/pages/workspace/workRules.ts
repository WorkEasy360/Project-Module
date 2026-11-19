import { useAsyncData } from '../../hooks/useAsyncData'
import { projectsApi } from '../../api/projects'
import { tasksApi } from '../../api/tasks'
import { activityApi, type ActivityEntry } from '../../api/collaboration'
import type { ProjectSummary } from '../../types/project'
import type { Task, TaskPriority, TaskStatus } from '../../types/work'

/**
 * The V3 rules for counting work, shared by Home, Tasks and Reports so every count agrees:
 * - open means not Done;
 * - overdue means past the due date and not Done (a date-only task stays due through its day);
 * - upcoming means due in the next 7 days after today;
 * - project progress comes from task completion counts, never typed in by hand.
 */

export interface WorkData {
  projects: ProjectSummary[]
  tasks: Task[]
  activity: (ActivityEntry & { projectId: string })[]
}

/** Active projects the person can see, their non-archived tasks, and (optionally) recent activity. */
export function useWorkData(activityPerProject = 0) {
  return useAsyncData(async (): Promise<WorkData> => {
    const page = await projectsApi.list(0, 100, 'updatedAt,DESC')
    const perProject = await Promise.all(
      page.content.map(async (p) => {
        const [tasks, activity] = await Promise.all([
          tasksApi.list(p.id, { archived: false }).catch(() => [] as Task[]),
          activityPerProject > 0
            ? activityApi.list(p.id, 0, activityPerProject).then((r) => r.content).catch(() => [] as ActivityEntry[])
            : Promise.resolve([] as ActivityEntry[]),
        ])
        return { tasks, activity: activity.map((a) => ({ ...a, projectId: p.id })) }
      }),
    )
    return {
      projects: page.content,
      tasks: perProject.flatMap((x) => x.tasks),
      activity: perProject.flatMap((x) => x.activity).sort((a, b) => b.occurredAt.localeCompare(a.occurredAt)),
    }
  }, [activityPerProject])
}

export function addDays(iso: string, days: number): string {
  const d = new Date(`${iso}T00:00:00`)
  d.setDate(d.getDate() + days)
  const pad = (n: number) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`
}

export const isOpen = (t: Task) => t.status !== 'COMPLETED'
export const isOverdue = (t: Task, today: string) => isOpen(t) && t.dueDate !== null && t.dueDate < today
export const isDueToday = (t: Task, today: string) => isOpen(t) && t.dueDate === today
export const isUpcoming = (t: Task, today: string) =>
  isOpen(t) && t.dueDate !== null && t.dueDate > today && t.dueDate <= addDays(today, 7)

/** The counts of the signed-in person's own work (Home > My Work Summary). */
export function myWork(tasks: Task[], meId: string | null, today: string) {
  const mine = meId ? tasks.filter((t) => t.assigneeId === meId) : []
  return {
    open: mine.filter(isOpen).length,
    dueToday: mine.filter((t) => isDueToday(t, today)).length,
    overdue: mine.filter((t) => isOverdue(t, today)).length,
    upcoming: mine.filter((t) => isUpcoming(t, today)).length,
    completed: mine.filter((t) => !isOpen(t)).length,
    next: mine
      .filter(isOpen)
      .sort((a, b) => (a.dueDate ?? '9999-12-31').localeCompare(b.dueDate ?? '9999-12-31'))
      .slice(0, 5),
  }
}

/**
 * What a manager oversees: organization admins see every project they can open; anyone else sees
 * the projects they own. Null when the person manages nothing (Home then hides the team overview).
 */
export function teamOverview(projects: ProjectSummary[], tasks: Task[], meId: string | null, isAdmin: boolean, today: string) {
  const managed = projects.filter((p) => isAdmin || (meId !== null && p.ownerId === meId))
  if (managed.length === 0) return null
  const ids = new Set(managed.map((p) => p.id))
  const scoped = tasks.filter((t) => ids.has(t.projectId))
  return {
    activeProjects: managed.filter((p) => p.status === 'ACTIVE').length,
    overdue: scoped.filter((t) => isOverdue(t, today)).length,
    unassigned: scoped.filter((t) => isOpen(t) && !t.assigneeId).length,
  }
}

/** A project's progress from its task completion counts; percent is null when it has no tasks. */
export function projectProgress(projectId: string, tasks: Task[], today: string) {
  const own = tasks.filter((t) => t.projectId === projectId)
  const done = own.filter((t) => !isOpen(t)).length
  return {
    total: own.length,
    done,
    open: own.length - done,
    overdue: own.filter((t) => isOverdue(t, today)).length,
    percent: own.length === 0 ? null : Math.round((done / own.length) * 100),
  }
}

/** Open, overdue and done tasks for each assignee, busiest first (Reports > Team Workload). */
export function workloadByPerson(tasks: Task[], today: string) {
  const rows = new Map<string, { userId: string; open: number; overdue: number; done: number; dueSoon: number }>()
  for (const t of tasks) {
    if (!t.assigneeId) continue
    const row = rows.get(t.assigneeId) ?? { userId: t.assigneeId, open: 0, overdue: 0, done: 0, dueSoon: 0 }
    if (isOpen(t)) row.open += 1
    else row.done += 1
    if (isOverdue(t, today)) row.overdue += 1
    if (isDueToday(t, today) || isUpcoming(t, today)) row.dueSoon += 1
    rows.set(t.assigneeId, row)
  }
  return [...rows.values()].sort((a, b) => b.open - a.open || b.overdue - a.overdue)
}

export function countBy<K extends string>(tasks: Task[], key: (t: Task) => K, keys: readonly K[]): Record<K, number> {
  const out = Object.fromEntries(keys.map((k) => [k, 0])) as Record<K, number>
  for (const t of tasks) {
    const k = key(t)
    if (k in out) out[k] += 1
  }
  return out
}

/** The filters of the Tasks pages, read from and written to the address, so every count can link to its list. */
export interface TaskFilters {
  view: '' | 'today' | 'upcoming' | 'overdue' | 'completed'
  project: string
  assignee: string // a user id, 'none' for unassigned, or '' for anyone
  status: '' | TaskStatus
  priority: '' | TaskPriority
  query: string
}

export function readTaskFilters(params: URLSearchParams): TaskFilters {
  const pick = <T extends string>(name: string, allowed: readonly T[]): T | '' => {
    const v = params.get(name) ?? ''
    return (allowed as readonly string[]).includes(v) ? (v as T) : ''
  }
  return {
    view: pick('view', ['today', 'upcoming', 'overdue', 'completed'] as const),
    project: params.get('project') ?? '',
    assignee: params.get('assignee') ?? '',
    status: pick('status', ['TODO', 'IN_PROGRESS', 'BLOCKED', 'COMPLETED'] as const),
    priority: pick('priority', ['LOW', 'MEDIUM', 'HIGH'] as const),
    query: params.get('q') ?? '',
  }
}

/** Applies the filters. With no view and no status chosen, only open work is shown (V3 default). */
export function filterTasks(tasks: Task[], f: TaskFilters, today: string, projectName: (id: string) => string): Task[] {
  const q = f.query.trim().toLowerCase()
  return tasks
    .filter((t) => {
      if (f.view === 'today' && !isDueToday(t, today)) return false
      if (f.view === 'upcoming' && !isUpcoming(t, today)) return false
      if (f.view === 'overdue' && !isOverdue(t, today)) return false
      if (f.view === 'completed' && isOpen(t)) return false
      if (!f.view && !f.status && !isOpen(t)) return false
      if (f.status && t.status !== f.status) return false
      if (f.priority && t.priority !== f.priority) return false
      if (f.project && t.projectId !== f.project) return false
      if (f.assignee === 'none' && t.assigneeId) return false
      if (f.assignee && f.assignee !== 'none' && t.assigneeId !== f.assignee) return false
      if (q && !t.name.toLowerCase().includes(q) && !projectName(t.projectId).toLowerCase().includes(q)) return false
      return true
    })
    .sort((a, b) => (a.dueDate ?? '9999-12-31').localeCompare(b.dueDate ?? '9999-12-31') || a.name.localeCompare(b.name))
}
