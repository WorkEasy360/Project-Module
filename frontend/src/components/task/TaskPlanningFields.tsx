import { SelectField } from '../forms/fields'
import { TASK_PRIORITIES, type Task, type TaskPlanningFields as PlanningRequest, type TaskPriority } from '../../types/work'
import { humanizeToken } from '../../utils/format'

/** The optional task details beyond title, project, assignee, due date and description: its priority. */
export interface TaskPlan {
  priority: TaskPriority
}

/** V3 default priority: Normal. */
export const EMPTY_PLAN: TaskPlan = { priority: 'MEDIUM' }

export function planFromTask(task: Task): TaskPlan {
  return { priority: TASK_PRIORITIES.includes(task.priority) ? task.priority : 'MEDIUM' }
}

export function planToRequest(plan: TaskPlan): PlanningRequest {
  return { priority: plan.priority }
}

/** The priority choice, shared by creating and editing a task: Low, Normal or High. */
export function TaskPlanningInputs({
  idPrefix,
  plan,
  onChange,
}: {
  idPrefix: string
  plan: TaskPlan
  onChange: (plan: TaskPlan) => void
}) {
  return (
    <SelectField
      id={`${idPrefix}-priority`}
      label="Priority"
      value={plan.priority}
      onChange={(value) => onChange({ ...plan, priority: value as TaskPriority })}
      options={TASK_PRIORITIES.map((priority) => ({ value: priority, label: humanizeToken(priority) }))}
    />
  )
}
