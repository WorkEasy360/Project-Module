import { describe, expect, it } from 'vitest'
import {
  addMonths,
  buildMonthGrid,
  enrichEntries,
  entriesByDay,
  entryTone,
  monthRange,
  shiftIso,
  summarize,
  type EnrichedEntry,
} from './calendarUtils'
import type { Milestone, Task } from '../../../../types/work'

const task = (over: Partial<Task>): Task => ({
  id: 't', projectId: 'p', name: 'T', description: null, dueDate: null, assigneeId: null, status: 'TODO',
  archived: false, createdAt: '', updatedAt: '', version: 0, ...over,
})
const milestone = (over: Partial<Milestone>): Milestone => ({
  id: 'm', projectId: 'p', phaseId: null, name: 'M', description: null, dueDate: null, status: 'PENDING',
  archived: false, createdAt: '', updatedAt: '', version: 0, ...over,
})

describe('calendarUtils', () => {
  it('monthRange covers the whole month inclusively and addMonths wraps years', () => {
    expect(monthRange({ year: 2026, month: 1 })).toEqual({ from: '2026-02-01', to: '2026-02-28' })
    expect(addMonths({ year: 2026, month: 11 }, 1)).toEqual({ year: 2027, month: 0 })
    expect(addMonths({ year: 2026, month: 0 }, -1)).toEqual({ year: 2025, month: 11 })
    expect(shiftIso('2026-02-28', 1)).toBe('2026-03-01')
  })

  it('buildMonthGrid always yields 6 Monday-first weeks with adjacent-month padding and marks today', () => {
    const cells = buildMonthGrid({ year: 2026, month: 8 }, '2026-09-19') // Sept 2026 starts on a Tuesday
    expect(cells).toHaveLength(42)
    expect(cells[0].iso).toBe('2026-08-31')
    expect(cells[0].inMonth).toBe(false)
    expect(cells[1]).toMatchObject({ iso: '2026-09-01', inMonth: true })
    expect(cells.find((c) => c.iso === '2026-09-19')?.isToday).toBe(true)
    expect(cells.filter((c) => c.inMonth)).toHaveLength(30)
  })

  it('enrichEntries joins real task status/assignee and milestone status, never inventing them', () => {
    const enriched = enrichEntries(
      [
        { entityType: 'TASK', id: 't1', name: 'A', date: '2026-09-10', startDate: null, endDate: null },
        { entityType: 'MILESTONE', id: 'm1', name: 'B', date: '2026-09-12', startDate: null, endDate: null },
        { entityType: 'TASK', id: 'unknown', name: 'C', date: '2026-09-13', startDate: null, endDate: null },
      ],
      [task({ id: 't1', status: 'COMPLETED', assigneeId: 'u-9' })],
      [milestone({ id: 'm1', status: 'AT_RISK' })],
    )
    expect(enriched[0]).toMatchObject({ status: 'COMPLETED', assigneeId: 'u-9' })
    expect(enriched[1]).toMatchObject({ status: 'AT_RISK' })
    expect(enriched[2]).toMatchObject({ status: null, assigneeId: null })
  })

  it('entriesByDay places point events on their date and expands phases across the visible window only', () => {
    const entries: EnrichedEntry[] = [
      { entityType: 'TASK', id: 't1', name: 'A', date: '2026-09-10', startDate: null, endDate: null, status: 'TODO', assigneeId: null },
      { entityType: 'PHASE', id: 'p1', name: 'Build', date: null, startDate: '2026-08-30', endDate: '2026-09-02', status: null, assigneeId: null },
    ]
    const byDay = entriesByDay(entries, '2026-09-01', '2026-09-30')
    expect(byDay.get('2026-09-10')?.map((e) => e.id)).toEqual(['t1'])
    expect(byDay.get('2026-09-01')?.map((e) => e.id)).toEqual(['p1'])
    expect(byDay.get('2026-09-02')?.map((e) => e.id)).toEqual(['p1'])
    expect(byDay.has('2026-08-31')).toBe(false)
    expect(byDay.has('2026-09-03')).toBe(false)
  })

  it('entryTone follows the semantic color rules', () => {
    const base = { name: 'x', startDate: null, endDate: null, assigneeId: null }
    expect(entryTone({ ...base, entityType: 'TASK', id: '1', date: '2026-09-20', status: 'COMPLETED' }, '2026-09-19')).toBe('success')
    expect(entryTone({ ...base, entityType: 'TASK', id: '1', date: '2026-09-20', status: 'BLOCKED' }, '2026-09-19')).toBe('danger')
    expect(entryTone({ ...base, entityType: 'TASK', id: '1', date: '2026-09-10', status: 'TODO' }, '2026-09-19')).toBe('danger')
    expect(entryTone({ ...base, entityType: 'TASK', id: '1', date: '2026-09-25', status: 'TODO' }, '2026-09-19')).toBe('info')
    expect(entryTone({ ...base, entityType: 'MILESTONE', id: '1', date: '2026-09-25', status: 'PENDING' }, '2026-09-19')).toBe('warning')
    expect(entryTone({ ...base, entityType: 'PHASE', id: '1', date: null, status: null }, '2026-09-19')).toBe('neutral')
  })

  it('summarize counts only loaded entries and uses the delay-detection rule for past due', () => {
    const entries: EnrichedEntry[] = [
      { entityType: 'TASK', id: '1', name: 'a', date: '2026-09-01', startDate: null, endDate: null, status: 'TODO', assigneeId: null },
      { entityType: 'TASK', id: '2', name: 'b', date: '2026-09-01', startDate: null, endDate: null, status: 'COMPLETED', assigneeId: null },
      { entityType: 'TASK', id: '3', name: 'c', date: '2026-09-30', startDate: null, endDate: null, status: 'TODO', assigneeId: null },
      { entityType: 'MILESTONE', id: '4', name: 'd', date: '2026-09-15', startDate: null, endDate: null, status: 'PENDING', assigneeId: null },
    ]
    expect(summarize(entries, '2026-09-19')).toEqual({ tasks: 3, pastDue: 1, milestones: 1, completed: 1 })
  })
})
