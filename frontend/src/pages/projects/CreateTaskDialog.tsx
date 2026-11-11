import { useState } from 'react'
import { Modal } from '../../components/common/Modal'
import { TextField, TextareaField, FormActions } from '../../components/forms/fields'
import { ProjectPersonField } from '../../components/forms/ProjectPersonField'
import { EMPTY_PLAN, planToRequest, TaskPlanningInputs, type TaskPlan } from '../../components/task/TaskPlanningFields'
import { InlineError } from '../../components/common/ErrorState'
import { toUserMessage } from '../../api/errorMessage'
import { tasksApi } from '../../api/tasks'
import type { Task } from '../../types/work'

/**
 * Task creation kept deliberately small: a name is all that's required. Priority, dates, tags and
 * the rest sit behind "More options" so nobody is confronted with a long form. Subtasks, files and
 * dependencies are added from the task's own page once it exists.
 */
export function CreateTaskDialog({
  projectId,
  onClose,
  onCreated,
  initialDueDate = '',
}: {
  projectId: string
  onClose: () => void
  onCreated: (task: Task) => void
  /** Pre-fills the due date (used by the calendar when adding a task on a selected day). */
  initialDueDate?: string
}) {
  const [name, setName] = useState('')
  const [description, setDescription] = useState('')
  const [dueDate, setDueDate] = useState(initialDueDate)
  const [assigneeId, setAssigneeId] = useState('')
  const [plan, setPlan] = useState<TaskPlan>(EMPTY_PLAN)
  const [showMore, setShowMore] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [submitting, setSubmitting] = useState(false)

  async function handleSubmit(event: React.FormEvent) {
    event.preventDefault()
    setSubmitting(true)
    setError(null)
    try {
      const task = await tasksApi.create(projectId, {
        name,
        description: description || null,
        dueDate: dueDate || null,
        assigneeId: assigneeId || null,
        ...planToRequest(plan),
      })
      onCreated(task)
    } catch (err) {
      setError(toUserMessage(err))
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <Modal title="New Task" onClose={onClose}>
      <form onSubmit={handleSubmit} className="form-grid">
        {error && <InlineError message={error} />}
        <TextField
          id="task-name"
          label="What needs to be done?"
          value={name}
          onChange={setName}
          required
          maxLength={200}
          placeholder="e.g. Draft the launch announcement"
        />
        <div className="form-row">
          <TextField id="task-due" label="Due date" type="date" value={dueDate} onChange={setDueDate} />
          <ProjectPersonField
            id="task-assignee"
            label="Assignee"
            projectId={projectId}
            value={assigneeId}
            onChange={setAssigneeId}
          />
        </div>

        {!showMore ? (
          <button type="button" className="btn btn-ghost btn-sm disclosure" onClick={() => setShowMore(true)}>
            More options
          </button>
        ) : (
          <>
            <TaskPlanningInputs idPrefix="task" plan={plan} onChange={setPlan} />
            <TextareaField
              id="task-description"
              label="Description"
              value={description}
              onChange={setDescription}
              hint="Subtasks, checklist items and files can be added once the task exists."
            />
          </>
        )}

        <FormActions>
          <button type="button" className="btn" onClick={onClose} disabled={submitting}>
            Cancel
          </button>
          <button type="submit" className="btn btn-primary" disabled={submitting || !name.trim()}>
            {submitting ? 'Creating…' : 'Create Task'}
          </button>
        </FormActions>
      </form>
    </Modal>
  )
}
