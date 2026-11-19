import { describe, expect, it } from 'vitest'
import { AUTOMATION_TRIGGER_EVENTS } from './automation'

describe('AUTOMATION_TRIGGER_EVENTS', () => {
  it('matches the 8 V3 events the backend raises', () => {
    expect([...AUTOMATION_TRIGGER_EVENTS].sort()).toEqual([
      'project.archived', 'project.created', 'project.updated',
      'task.assigned', 'task.blocked', 'task.completed', 'task.created', 'task.updated',
    ])
  })

  it('has no duplicate values', () => {
    expect(new Set(AUTOMATION_TRIGGER_EVENTS).size).toBe(AUTOMATION_TRIGGER_EVENTS.length)
  })

  it('never includes an AI or automation event (self-triggering is not supported)', () => {
    for (const event of AUTOMATION_TRIGGER_EVENTS) {
      expect(event.startsWith('ai.')).toBe(false)
      expect(event.startsWith('automation.')).toBe(false)
    }
  })
})
