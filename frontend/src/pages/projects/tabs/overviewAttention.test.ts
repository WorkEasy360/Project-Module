import { describe, expect, it } from 'vitest'
import { mergeAttention } from './OverviewTab'
import type { DelayedItem, Task } from '../../../types/work'

const task = (over: Partial<Task>): Task => ({
  id: 't', projectId: 'p', name: 'T', description: null, dueDate: null, assigneeId: null, status: 'TODO',
  archived: false, createdAt: '', updatedAt: '', version: 0, ...over,
})
const delayed = (over: Partial<DelayedItem>): DelayedItem => ({ entityType: 'TASK', id: 'd', name: 'D', dueDate: '2026-09-01', daysOverdue: 3, ...over })

describe('Overview "Overdue & blocked work" merge', () => {
  it('keeps the stored status and marks a blocked task that is also past due, without duplicating it', () => {
    const rows = mergeAttention(
      [task({ id: 'b', name: 'Blocked one', status: 'BLOCKED', dueDate: '2026-01-05' })],
      [delayed({ id: 'b', name: 'Blocked one', dueDate: '2026-01-05' })],
    )
    expect(rows).toHaveLength(1)
    expect(rows[0].status).toBe('BLOCKED')
    expect(rows[0].pastDue).toBe(true)
  })

  it('adds past-due TODO tasks and non-task delayed items the status filters would miss', () => {
    const rows = mergeAttention(
      [],
      [delayed({ id: 'x', name: 'Late todo', entityType: 'TASK' }), delayed({ id: 'm', name: 'Late milestone', entityType: 'MILESTONE' })],
    )
    expect(rows.map((r) => `${r.kind}:${r.status}:${r.pastDue}`)).toEqual(['Task:TODO:true', 'Milestone:null:true'])
    expect(rows[1].to).toBe('../milestones')
  })
})
