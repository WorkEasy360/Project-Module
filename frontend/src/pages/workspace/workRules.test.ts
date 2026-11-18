import { describe, expect, it } from 'vitest'
import { filterTasks, myWork, projectProgress, readTaskFilters, teamOverview, workloadByPerson } from './workRules'
import type { ProjectSummary } from '../../types/project'
import type { Task } from '../../types/work'

const today = '2026-11-14'

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

function project(over: Partial<ProjectSummary>): ProjectSummary {
  return { id: 'p1', name: 'P', status: 'ACTIVE', priority: 'MEDIUM', ownerId: 'boss', updatedAt: '2026-11-10T00:00:00Z', ...over }
}

const tasks = [
  task({ name: 'Due today', assigneeId: 'me', dueDate: today }),
  task({ name: 'Late', assigneeId: 'me', dueDate: '2026-11-13', status: 'IN_PROGRESS' }),
  task({ name: 'Late but done', assigneeId: 'me', dueDate: '2026-11-01', status: 'COMPLETED' }),
  task({ name: 'Next week', assigneeId: 'me', dueDate: '2026-11-20' }),
  task({ name: 'Far away', assigneeId: 'me', dueDate: '2026-12-30' }),
  task({ name: 'Nobody', projectId: 'p2', dueDate: '2026-11-10' }),
]

describe('V3 work rules', () => {
  it('overdue is past due and not Done; a task due today is not overdue yet', () => {
    expect(myWork(tasks, 'me', today)).toMatchObject({ open: 4, dueToday: 1, overdue: 1, upcoming: 1, completed: 1 })
  })

  it('managers see overdue and unassigned work only for projects they own; admins for all', () => {
    const projects = [project({ id: 'p1', ownerId: 'me' }), project({ id: 'p2', ownerId: 'boss' })]
    expect(teamOverview(projects, tasks, 'me', false, today)).toEqual({ activeProjects: 1, overdue: 1, unassigned: 0 })
    expect(teamOverview(projects, tasks, 'me', true, today)).toEqual({ activeProjects: 2, overdue: 2, unassigned: 1 })
    expect(teamOverview(projects, tasks, 'someone', false, today)).toBeNull()
  })

  it('project progress comes from task completion, and a project with no tasks has none', () => {
    expect(projectProgress('p1', tasks, today)).toMatchObject({ total: 5, done: 1, percent: 20, overdue: 1 })
    expect(projectProgress('empty', tasks, today).percent).toBeNull()
  })

  it('workload counts each person’s open, overdue and done work', () => {
    expect(workloadByPerson(tasks, today)).toEqual([{ userId: 'me', open: 4, overdue: 1, done: 1, dueSoon: 2 }])
  })

  it('filters default to open work and read from the address', () => {
    const name = () => 'P'
    expect(filterTasks(tasks, readTaskFilters(new URLSearchParams('')), today, name)).toHaveLength(5)
    expect(filterTasks(tasks, readTaskFilters(new URLSearchParams('view=overdue')), today, name).map((t) => t.name)).toEqual(['Nobody', 'Late'])
    expect(filterTasks(tasks, readTaskFilters(new URLSearchParams('assignee=none')), today, name).map((t) => t.name)).toEqual(['Nobody'])
    expect(filterTasks(tasks, readTaskFilters(new URLSearchParams('view=completed')), today, name).map((t) => t.name)).toEqual(['Late but done'])
    expect(readTaskFilters(new URLSearchParams('status=OVERDUE&priority=CRITICAL'))).toMatchObject({ status: '', priority: '' })
  })
})
