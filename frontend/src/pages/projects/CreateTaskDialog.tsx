import { useState } from 'react'
import { Modal } from '../../components/common/Modal'
import { TextField, TextareaField, FormActions } from '../../components/forms/fields'
import { InlineError } from '../../components/common/ErrorState'
import { toUserMessage } from '../../api/errorMessage'
import { tasksApi } from '../../api/tasks'
import type { Task } from '../../types/work'

/**
 * Task creation kept deliberately small: a name is all that's required. Everything the backend
 * accepts beyond that (description, due date, assignee) sits behind "More options" so a
 * first-time user isn't confronted with a form. Subtasks, checklist items and dependencies are
 * added from the task's own page after creation — that's how the backend models them (separate
 * nested resources), not fields on the task itself.
 */
export function CreateTaskDialog({
  projectId,
  onClose,
  onCreated,
}: {
  projectId: string
  onClose: () => void
  onCreated: (task: Task) => void
}) {
  const [name, setName] = useState('')
  const [description, setDescription] = useState('')
  const [dueDate, setDueDate] = useState('')
  const [assigneeId, setAssigneeId] = useState('')
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
          <TextField
            id="task-assignee"
            label="Assignee (user ID)"
            value={assigneeId}
            onChange={setAssigneeId}
            hint="Optional — leave blank for unassigned."
          />
        </div>

        {!showMore ? (
          <button type="button" className="btn btn-ghost btn-sm disclosure" onClick={() => setShowMore(true)}>
            More options
          </button>
        ) : (
          <TextareaField
            id="task-description"
            label="Description"
            value={description}
            onChange={setDescription}
            hint="Subtasks, checklist items and dependencies can be added once the task exists."
          />
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
