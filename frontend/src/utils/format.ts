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
  if (value === 'TODO') return 'To Do'
  return value
    .split(/[._]/)
    .map((part) => part.charAt(0).toUpperCase() + part.slice(1).toLowerCase())
    .join(' ')
}

export function truncate(value: string, maxLength: number): string {
  return value.length > maxLength ? `${value.slice(0, maxLength - 1)}…` : value
}

/** Up to two letters for an avatar: first and last initials, or the start of a single word. */
export function initials(name: string): string {
  const words = name.trim().split(/\s+/)
  const letters = words.length > 1 ? words[0][0] + words[words.length - 1][0] : words[0].slice(0, 2)
  return letters.toUpperCase()
}

/** A date as yyyy-mm-dd in the person's own time zone. */
export function toIsoDate(date: Date): string {
  const pad = (n: number) => String(n).padStart(2, '0')
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}`
}

export function todayIso(): string {
  return toIsoDate(new Date())
}

/** 90 becomes "1h 30m". */
export function formatMinutes(minutes: number): string {
  const hours = Math.floor(minutes / 60)
  const rest = minutes % 60
  if (hours === 0) return `${rest}m`
  return rest === 0 ? `${hours}h` : `${hours}h ${rest}m`
}

/** Reads "1.5" or "1:30" as minutes, or null if it cannot be read or is not above zero. */
export function parseHours(text: string): number | null {
  const value = text.trim()
  const clock = /^(\d+):([0-5]?\d)$/.exec(value)
  const minutes = clock ? Number(clock[1]) * 60 + Number(clock[2]) : /^\d*\.?\d+$/.test(value) ? Math.round(Number(value) * 60) : 0
  return minutes > 0 ? minutes : null
}

export function formatFileSize(bytes: number): string {
  if (bytes < 1024) return `${bytes} B`
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(0)} KB`
  return `${(bytes / (1024 * 1024)).toFixed(1)} MB`
}
