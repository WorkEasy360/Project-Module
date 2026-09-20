import type { CalendarEntry, Milestone, Task } from '../../../../types/work'

/** Local-date ISO string (YYYY-MM-DD) — the backend uses LocalDate, so no timezone math. */
export function toIso(d: Date): string {
  const y = d.getFullYear()
  const m = String(d.getMonth() + 1).padStart(2, '0')
  const day = String(d.getDate()).padStart(2, '0')
  return `${y}-${m}-${day}`
}

export function todayIso(): string {
  return toIso(new Date())
}

export function parseIso(iso: string): Date {
  const [y, m, d] = iso.split('-').map(Number)
  return new Date(y, m - 1, d)
}

export function shiftIso(iso: string, days: number): string {
  const d = parseIso(iso)
  d.setDate(d.getDate() + days)
  return toIso(d)
}

export interface YearMonth {
  year: number
  /** 0-based, like Date#getMonth(). */
  month: number
}

export function yearMonthOf(iso: string): YearMonth {
  const d = parseIso(iso)
  return { year: d.getFullYear(), month: d.getMonth() }
}

export function addMonths({ year, month }: YearMonth, delta: number): YearMonth {
  const d = new Date(year, month + delta, 1)
  return { year: d.getFullYear(), month: d.getMonth() }
}

/** Inclusive first/last day of the month as ISO strings — the `from`/`to` sent to the API. */
export function monthRange({ year, month }: YearMonth): { from: string; to: string } {
  return { from: toIso(new Date(year, month, 1)), to: toIso(new Date(year, month + 1, 0)) }
}

export function monthLabel({ year, month }: YearMonth): string {
  return new Date(year, month, 1).toLocaleDateString(undefined, { month: 'long', year: 'numeric' })
}

export function longDateLabel(iso: string): string {
  return parseIso(iso).toLocaleDateString(undefined, {
    weekday: 'long',
    year: 'numeric',
    month: 'long',
    day: 'numeric',
  })
}

export const WEEKDAYS = ['Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat', 'Sun']

export interface DayCell {
  iso: string
  day: number
  inMonth: boolean
  isToday: boolean
}

/** 6 weeks × 7 days starting on Monday, with leading/trailing days from adjacent months. */
export function buildMonthGrid(ym: YearMonth, today: string = todayIso()): DayCell[] {
  const first = new Date(ym.year, ym.month, 1)
  const mondayOffset = (first.getDay() + 6) % 7
  const start = new Date(ym.year, ym.month, 1 - mondayOffset)
  const cells: DayCell[] = []
  for (let i = 0; i < 42; i++) {
    const d = new Date(start.getFullYear(), start.getMonth(), start.getDate() + i)
    const iso = toIso(d)
    cells.push({ iso, day: d.getDate(), inMonth: d.getMonth() === ym.month, isToday: iso === today })
  }
  return cells
}

/** A calendar entry joined with the status/assignee the plain calendar API doesn't carry. */
export interface EnrichedEntry extends CalendarEntry {
  status: string | null
  assigneeId: string | null
  /** Set by cross-project views (workspace calendar) so chips, cards and the detail dialog can say which project an entry belongs to. Absent inside a single project. */
  projectId?: string
  projectName?: string
}

/** Minimal project identity stamped onto entries by cross-project views. */
export interface EntryProject {
  id: string
  name: string
}

export function enrichEntries(
  entries: CalendarEntry[],
  tasks: Task[] | null,
  milestones: Milestone[] | null,
  project?: EntryProject,
): EnrichedEntry[] {
  const taskById = new Map((tasks ?? []).map((t) => [t.id, t]))
  const milestoneById = new Map((milestones ?? []).map((m) => [m.id, m]))
  const stamp = project ? { projectId: project.id, projectName: project.name } : {}
  return entries.map((entry) => {
    if (entry.entityType === 'TASK') {
      const task = taskById.get(entry.id)
      return { ...entry, ...stamp, status: task?.status ?? null, assigneeId: task?.assigneeId ?? null }
    }
    if (entry.entityType === 'MILESTONE') {
      return { ...entry, ...stamp, status: milestoneById.get(entry.id)?.status ?? null, assigneeId: null }
    }
    return { ...entry, ...stamp, status: null, assigneeId: null }
  })
}

/**
 * Groups entries by day. Point entries (task/milestone) land on their `date`; ranged entries
 * (phases) are expanded onto every day of their range, clipped to [from, to] so a long phase
 * doesn't produce cells outside the visible window.
 */
export function entriesByDay(entries: EnrichedEntry[], from: string, to: string): Map<string, EnrichedEntry[]> {
  const map = new Map<string, EnrichedEntry[]>()
  const push = (iso: string, entry: EnrichedEntry) => {
    const list = map.get(iso)
    if (list) list.push(entry)
    else map.set(iso, [entry])
  }
  for (const entry of entries) {
    if (entry.date) {
      push(entry.date, entry)
    } else if (entry.startDate && entry.endDate) {
      let cursor = entry.startDate < from ? from : entry.startDate
      const last = entry.endDate > to ? to : entry.endDate
      let guard = 0
      while (cursor <= last && guard++ < 400) {
        push(cursor, entry)
        cursor = shiftIso(cursor, 1)
      }
    }
  }
  return map
}

export function entryKey(entry: EnrichedEntry): string {
  return entry.projectId ? `${entry.projectId}-${entry.entityType}-${entry.id}` : `${entry.entityType}-${entry.id}`
}

export type EntryTone = 'success' | 'info' | 'warning' | 'danger' | 'neutral'

/** Semantic color: green done, blue active, amber attention, red overdue/blocked, gray unknown. */
export function entryTone(entry: EnrichedEntry, today: string = todayIso()): EntryTone {
  if (entry.entityType === 'TASK') {
    if (entry.status === 'COMPLETED') return 'success'
    if (entry.status === 'OVERDUE' || entry.status === 'BLOCKED') return 'danger'
    if (entry.date && entry.date < today) return 'danger'
    return 'info'
  }
  if (entry.entityType === 'MILESTONE') {
    if (entry.status === 'COMPLETED') return 'success'
    if (entry.status === 'AT_RISK') return 'danger'
    return 'warning'
  }
  return 'neutral'
}

export interface CalendarSummary {
  tasks: number
  pastDue: number
  milestones: number
  completed: number
}

/**
 * Counts over the loaded window only. "Past due" mirrors the backend's delay-detection rule:
 * due date before today and not completed.
 */
export function summarize(entries: EnrichedEntry[], today: string = todayIso()): CalendarSummary {
  let tasks = 0
  let pastDue = 0
  let milestones = 0
  let completed = 0
  for (const e of entries) {
    if (e.entityType === 'TASK') {
      tasks++
      if (e.status === 'COMPLETED') completed++
      else if (e.date && e.date < today) pastDue++
    } else if (e.entityType === 'MILESTONE') {
      milestones++
    }
  }
  return { tasks, pastDue, milestones, completed }
}
