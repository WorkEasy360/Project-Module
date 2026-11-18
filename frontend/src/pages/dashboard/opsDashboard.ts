/**
 * The numbers on the Operations home page, worked out from the projects and tasks the person can
 * see. Pure functions, so they are easy to test and the page only draws.
 */
import type { ProjectSummary } from '../../types/project'
import type { Task, TaskStatus } from '../../types/work'

export type PeriodKind = 'fy' | 'quarter' | 'month' | 'last30' | 'all'

export const PERIODS: { value: PeriodKind; label: string }[] = [
  { value: 'fy', label: 'This Financial Year' },
  { value: 'quarter', label: 'This Quarter' },
  { value: 'month', label: 'This Month' },
  { value: 'last30', label: 'Last 30 Days' },
  { value: 'all', label: 'All Time' },
]

export interface Period {
  /** Inclusive, YYYY-MM-DD. Null for all time. */
  from: string | null
  to: string | null
  /** The same length of time just before, for "vs last period". Null for all time. */
  previous: { from: string; to: string } | null
}

const MONTHS = ['Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun', 'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec']

export function iso(d: Date): string {
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`
}

function parse(isoDate: string): Date {
  const [y, m, d] = isoDate.slice(0, 10).split('-').map(Number)
  return new Date(y, m - 1, d)
}

function addDays(isoDate: string, days: number): string {
  const d = parse(isoDate)
  d.setDate(d.getDate() + days)
  return iso(d)
}

/** "1 Apr 2026". */
export function longDate(isoDate: string): string {
  const d = parse(isoDate)
  return `${d.getDate()} ${MONTHS[d.getMonth()]} ${d.getFullYear()}`
}

/** "01 Nov 2026", as in the projects table. */
export function tableDate(isoDate: string | null | undefined): string {
  if (!isoDate) return '—'
  const d = parse(isoDate)
  return `${String(d.getDate()).padStart(2, '0')} ${MONTHS[d.getMonth()]} ${d.getFullYear()}`
}

/** The period that contains today. The financial year runs April to March. */
export function periodFor(kind: PeriodKind, today: string): Period {
  const t = parse(today)
  const y = t.getFullYear()
  const m = t.getMonth()
  switch (kind) {
    case 'fy': {
      const start = m >= 3 ? y : y - 1
      return {
        from: `${start}-04-01`,
        to: `${start + 1}-03-31`,
        previous: { from: `${start - 1}-04-01`, to: `${start}-03-31` },
      }
    }
    case 'quarter': {
      const qStart = Math.floor(m / 3) * 3
      const from = iso(new Date(y, qStart, 1))
      const to = iso(new Date(y, qStart + 3, 0))
      return { from, to, previous: { from: iso(new Date(y, qStart - 3, 1)), to: iso(new Date(y, qStart, 0)) } }
    }
    case 'month': {
      const from = iso(new Date(y, m, 1))
      const to = iso(new Date(y, m + 1, 0))
      return { from, to, previous: { from: iso(new Date(y, m - 1, 1)), to: iso(new Date(y, m, 0)) } }
    }
    case 'last30': {
      const from = addDays(today, -29)
      return { from, to: today, previous: { from: addDays(today, -59), to: addDays(today, -30) } }
    }
    default:
      return { from: null, to: null, previous: null }
  }
}

function within(isoDateTime: string | null | undefined, from: string | null, to: string | null): boolean {
  if (!isoDateTime) return false
  const day = isoDateTime.slice(0, 10)
  return (!from || day >= from) && (!to || day <= to)
}

/** A task's day of creation, in local time. */
function createdDay(task: Task): string {
  return iso(new Date(task.createdAt))
}

/** When a completed task was completed: its last update, the closest the data records. */
function completedDay(task: Task): string {
  return iso(new Date(task.updatedAt))
}

const isDone = (t: Task) => t.status === 'COMPLETED'

export function tasksInPeriod(tasks: Task[], period: Period): Task[] {
  return period.from ? tasks.filter((t) => within(createdDay(t), period.from, period.to)) : tasks
}

export interface StatusSlice {
  key: 'todo' | 'progress' | 'blocked' | 'done'
  label: string
  count: number
  percent: number
  color: string
}

const STATUS_GROUP: Record<TaskStatus, StatusSlice['key']> = {
  TODO: 'todo',
  IN_PROGRESS: 'progress',
  BLOCKED: 'blocked',
  COMPLETED: 'done',
}

/** To Do, In Progress, Blocked and Done. Overdue tasks are still to do, so they count there. */
export function statusBreakdown(tasks: Task[]): StatusSlice[] {
  const slices: StatusSlice[] = [
    { key: 'todo', label: 'To Do', count: 0, percent: 0, color: '#22c55e' },
    { key: 'progress', label: 'In Progress', count: 0, percent: 0, color: '#3b82f6' },
    { key: 'blocked', label: 'Blocked', count: 0, percent: 0, color: '#f59e0b' },
    { key: 'done', label: 'Done', count: 0, percent: 0, color: '#8b5cf6' },
  ]
  for (const t of tasks) {
    const slice = slices.find((s) => s.key === STATUS_GROUP[t.status])
    if (slice) slice.count++
  }
  for (const s of slices) s.percent = tasks.length ? Math.round((s.count / tasks.length) * 100) : 0
  return slices
}

export function projectProgress(p: ProjectSummary): number {
  const total = p.taskCount ?? 0
  return total ? Math.round(((p.completedTaskCount ?? 0) / total) * 100) : 0
}

export interface ChartBucket {
  label: string
  completed: number
  created: number
}

/**
 * Tasks created and completed, by month (by week for periods of about a month). All time shows the
 * last twelve months. Completion is dated by a completed task's last update.
 */
export function tasksOverview(tasks: Task[], period: Period, today: string): ChartBucket[] {
  const buckets: { label: string; from: string; to: string }[] = []
  const short = period.from && period.to && parse(period.to).getTime() - parse(period.from).getTime() <= 62 * 86_400_000
  if (short && period.from && period.to) {
    let start = period.from
    let n = 1
    while (start <= period.to) {
      const end = addDays(start, 6) < period.to ? addDays(start, 6) : period.to
      buckets.push({ label: `Week ${n++}`, from: start, to: end })
      start = addDays(end, 1)
    }
  } else {
    const t = parse(today)
    const first = period.from ? parse(period.from) : new Date(t.getFullYear(), t.getMonth() - 11, 1)
    const last = period.to && parse(period.to) < t ? parse(period.to) : t
    for (let d = new Date(first.getFullYear(), first.getMonth(), 1); d <= last; d = new Date(d.getFullYear(), d.getMonth() + 1, 1)) {
      buckets.push({ label: MONTHS[d.getMonth()], from: iso(d), to: iso(new Date(d.getFullYear(), d.getMonth() + 1, 0)) })
    }
  }
  return buckets.map((b) => ({
    label: b.label,
    created: tasks.filter((t) => within(createdDay(t), b.from, b.to)).length,
    completed: tasks.filter((t) => isDone(t) && within(completedDay(t), b.from, b.to)).length,
  }))
}

/** "10 min ago", "2 hours ago", "3 days ago". */
export function timeAgo(isoDateTime: string, now: Date = new Date()): string {
  const minutes = Math.max(0, Math.round((now.getTime() - new Date(isoDateTime).getTime()) / 60_000))
  if (minutes < 1) return 'just now'
  if (minutes < 60) return `${minutes} min ago`
  const hours = Math.round(minutes / 60)
  if (hours < 24) return `${hours} hour${hours === 1 ? '' : 's'} ago`
  const days = Math.round(hours / 24)
  return `${days} day${days === 1 ? '' : 's'} ago`
}

/** "Good Evening". */
export function greeting(hour: number): string {
  if (hour < 12) return 'Good Morning'
  if (hour < 17) return 'Good Afternoon'
  return 'Good Evening'
}
