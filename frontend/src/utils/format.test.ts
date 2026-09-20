import { describe, expect, it } from 'vitest'
import { formatDate, humanizeToken } from './format'

describe('formatDate', () => {
  it('treats a date-only value as a local calendar date (never shifts a day across timezones)', () => {
    // Same day the backend stored, regardless of the machine timezone.
    const local = new Date(2026, 8, 10).toLocaleDateString(undefined, { year: 'numeric', month: 'short', day: 'numeric' })
    expect(formatDate('2026-09-10')).toBe(local)
    expect(formatDate('2026-09-10')).toMatch(/10/)
  })

  it('still formats full timestamps', () => {
    expect(formatDate('2026-09-10T12:00:00Z')).toMatch(/2026/)
  })

  it('renders a dash for missing values', () => {
    expect(formatDate(null)).toBe('—')
    expect(formatDate(undefined)).toBe('—')
  })
})

describe('humanizeToken', () => {
  it('maps enum tokens to readable labels without changing their meaning', () => {
    expect(humanizeToken('ON_HOLD')).toBe('On Hold')
    expect(humanizeToken('COMPLETED')).toBe('Completed')
    expect(humanizeToken('task.overdue')).toBe('Task Overdue')
  })
})
