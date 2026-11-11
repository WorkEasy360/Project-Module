import { TextField, SelectField } from '../forms/fields'
import {
  TASK_PRIORITIES,
  TASK_RECURRENCES,
  type Task,
  type TaskPlanningFields as PlanningRequest,
  type TaskPriority,
  type TaskRecurrence,
} from '../../types/work'
import { humanizeToken, parseHours } from '../../utils/format'

export interface TaskPlan {
  priority: TaskPriority
  startDate: string
  estimateHours: string
  recurrence: TaskRecurrence
  tags: string
}

export const EMPTY_PLAN: TaskPlan = { priority: 'MEDIUM', startDate: '', estimateHours: '', recurrence: 'NONE', tags: '' }

export function planFromTask(task: Task): TaskPlan {
  return {
    priority: task.priority ?? 'MEDIUM',
    startDate: task.startDate ?? '',
    estimateHours: task.estimateMinutes ? String(task.estimateMinutes / 60) : '',
    recurrence: task.recurrence ?? 'NONE',
    tags: (task.tags ?? []).join(', '),
  }
}

/** The request fields for a plan. An unreadable estimate is left out rather than guessed. */
export function planToRequest(plan: TaskPlan): PlanningRequest {
  const estimateMinutes = parseHours(plan.estimateHours)
  return {
    priority: plan.priority,
    startDate: plan.startDate || null,
    estimateMinutes: estimateMinutes,
    recurrence: plan.recurrence,
    tags: plan.tags.split(',').map((tag) => tag.trim()).filter(Boolean),
  }
}

const RECURRENCE_LABEL: Record<TaskRecurrence, string> = {
  NONE: 'Does not repeat',
  DAILY: 'Every day',
  WEEKLY: 'Every week',
  MONTHLY: 'Every month',
}

/** The optional details of a task: shared by creating and editing one. */
export function TaskPlanningInputs({
  idPrefix,
  plan,
  onChange,
}: {
  idPrefix: string
  plan: TaskPlan
  onChange: (plan: TaskPlan) => void
}) {
  const set = <K extends keyof TaskPlan>(key: K, value: TaskPlan[K]) => onChange({ ...plan, [key]: value })
  return (
    <>
      <div className="form-row">
        <SelectField
          id={`${idPrefix}-priority`}
          label="Priority"
          value={plan.priority}
          onChange={(value) => set('priority', value)}
          options={TASK_PRIORITIES.map((priority) => ({ value: priority, label: humanizeToken(priority) }))}
        />
        <TextField
          id={`${idPrefix}-start`}
          label="Start date"
          type="date"
          value={plan.startDate}
          onChange={(value) => set('startDate', value)}
        />
      </div>
      <div className="form-row">
        <TextField
          id={`${idPrefix}-estimate`}
          label="Estimate (hours)"
          value={plan.estimateHours}
          onChange={(value) => set('estimateHours', value)}
          placeholder="e.g. 4 or 1:30"
        />
        <SelectField
          id={`${idPrefix}-recurrence`}
          label="Repeats"
          value={plan.recurrence}
          onChange={(value) => set('recurrence', value)}
          options={TASK_RECURRENCES.map((recurrence) => ({ value: recurrence, label: RECURRENCE_LABEL[recurrence] }))}
          hint={plan.recurrence !== 'NONE' ? 'Completing it creates the next one, so it needs a due date.' : undefined}
        />
      </div>
      <TextField
        id={`${idPrefix}-tags`}
        label="Tags"
        value={plan.tags}
        onChange={(value) => set('tags', value)}
        placeholder="design, urgent"
        hint="Separate tags with commas."
      />
    </>
  )
}

export function recurrenceLabel(recurrence: TaskRecurrence): string {
  return RECURRENCE_LABEL[recurrence]
}
