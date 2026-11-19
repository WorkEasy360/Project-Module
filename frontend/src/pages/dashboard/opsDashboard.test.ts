import { describe, expect, it } from 'vitest'
import { greeting, longDate, periodFor, statusBreakdown, tableDate, tasksOverview, timeAgo } from './opsDashboard'
import type { Task } from '../../types/work'

function task(over: Partial<Task>): Task {
  return {
    id: Math.random().toString(36).slice(2),
    projectId: 'p1',
    name: 't',
    description: null,
    dueDate: null,
    assigneeId: null,
    status: 'TODO',
    archived: false,
    createdAt: '2026-11-02T10:00:00Z',
    updatedAt: '2026-11-02T10:00:00Z',
    version: 0,
    priority: 'MEDIUM',
    ...over,
  }
}


describe('periods', () => {
  it('the financial year runs April to March, and is compared with the year before', () => {
    expect(periodFor('fy', '2026-11-14')).toEqual({
      from: '2026-04-01',
      to: '2027-03-31',
      previous: { from: '2025-04-01', to: '2026-03-31' },
    })
    expect(periodFor('fy', '2027-02-10').from).toBe('2026-04-01')
  })

  it('quarters, months and the last 30 days', () => {
    expect(periodFor('quarter', '2026-11-14')).toMatchObject({ from: '2026-10-01', to: '2026-12-31', previous: { from: '2026-07-01', to: '2026-09-30' } })
    expect(periodFor('month', '2026-11-14')).toMatchObject({ from: '2026-11-01', to: '2026-11-30' })
    expect(periodFor('last30', '2026-11-14')).toMatchObject({ from: '2026-10-16', to: '2026-11-14' })
    expect(periodFor('all', '2026-11-14')).toEqual({ from: null, to: null, previous: null })
  })

  it('formats dates as in the design', () => {
    expect(longDate('2026-04-01')).toBe('1 Apr 2026')
    expect(tableDate('2026-11-01')).toBe('01 Nov 2026')
    expect(tableDate(null)).toBe('—')
  })
})

describe('numbers', () => {
  const today = '2026-11-14'
  const period = periodFor('fy', today)
  const tasks = [
    task({ status: 'COMPLETED', assigneeId: 'me' }),
    task({ status: 'COMPLETED' }),
    task({ status: 'IN_PROGRESS', dueDate: today, assigneeId: 'me' }),
    task({ status: 'BLOCKED', dueDate: '2026-11-01' }),
    task({ status: 'TODO', dueDate: '2026-11-18', assigneeId: 'me' }),
    task({ status: 'TODO', createdAt: '2025-12-01T10:00:00Z', updatedAt: '2025-12-01T10:00:00Z' }),
  ]


  it('groups statuses into To Do, In Progress, Blocked and Done', () => {
    const slices = statusBreakdown(tasks)
    expect(slices.map((s) => [s.label, s.count])).toEqual([
      ['To Do', 2],
      ['In Progress', 1],
      ['Blocked', 1],
      ['Done', 2],
    ])
  })


  it('charts created and completed tasks by month', () => {
    const chart = tasksOverview(tasks, period, today)
    expect(chart.map((b) => b.label)).toEqual(['Apr', 'May', 'Jun', 'Jul', 'Aug', 'Sep', 'Oct', 'Nov'])
    expect(chart[7]).toEqual({ label: 'Nov', created: 5, completed: 2 })
  })


  it('greets and says how long ago', () => {
    expect(greeting(9)).toBe('Good Morning')
    expect(greeting(19)).toBe('Good Evening')
    const now = new Date('2026-11-14T12:00:00Z')
    expect(timeAgo('2026-11-14T11:50:00Z', now)).toBe('10 min ago')
    expect(timeAgo('2026-11-14T10:00:00Z', now)).toBe('2 hours ago')
  })
})
