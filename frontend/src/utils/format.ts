const DATE_ONLY = /^(\d{4})-(\d{2})-(\d{2})$/

/**
 * Formats a backend date. Date-only values (LocalDate, "YYYY-MM-DD") are calendar dates with no
 * timezone, so they are parsed as *local* dates — `new Date("2026-09-10")` would treat them as
 * UTC midnight and show the previous day for users west of UTC.
 */
export function formatDate(value: string | null | undefined): string {
  if (!value) return '—'
  try {
    const m = DATE_ONLY.exec(value)
    const date = m ? new Date(Number(m[1]), Number(m[2]) - 1, Number(m[3])) : new Date(value)
    return date.toLocaleDateString(undefined, {
      year: 'numeric',
      month: 'short',
      day: 'numeric',
    })
  } catch {
    return value
  }
}

export function formatDateTime(value: string | null | undefined): string {
  if (!value) return '—'
  try {
    return new Date(value).toLocaleString(undefined, {
      year: 'numeric',
      month: 'short',
      day: 'numeric',
      hour: '2-digit',
      minute: '2-digit',
    })
  } catch {
    return value
  }
}

/** "TASK_BREAKDOWN" -> "Task Breakdown"; "task.overdue" -> "Task Overdue". */
export function humanizeToken(value: string): string {
  return value
    .split(/[._]/)
    .map((part) => part.charAt(0).toUpperCase() + part.slice(1).toLowerCase())
    .join(' ')
}

export function truncate(value: string, maxLength: number): string {
  return value.length > maxLength ? `${value.slice(0, maxLength - 1)}…` : value
}
