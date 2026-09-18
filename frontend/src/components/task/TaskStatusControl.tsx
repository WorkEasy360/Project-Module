import { useState } from 'react'
import { tasksApi } from '../../api/tasks'
import { toUserMessage } from '../../api/errorMessage'
import { ApiError } from '../../api/client'
import { TASK_STATUS_TRANSITIONS, type Task, type TaskStatus } from '../../types/work'
import { humanizeToken } from '../../utils/format'

/**
 * The one explicit "change status" control, shared by the task page, the task list and the
 * board card. Offers only the transitions the backend allows from the task's current status
 * (never back to TODO), sends the task's version for optimistic locking, and reports the
 * backend's own message on 409/422 rather than pretending the move happened.
 */
export function TaskStatusControl({
  task,
  onChanged,
  compact,
}: {
  task: Task
  onChanged: () => void
  compact?: boolean
}) {
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const allowed = TASK_STATUS_TRANSITIONS[task.status]

  if (allowed.length === 0) return null

  async function change(next: TaskStatus) {
    setBusy(true)
    setError(null)
    try {
      await tasksApi.update(task.id, { status: next, version: task.version })
      onChanged()
    } catch (err) {
      setError(
        err instanceof ApiError && err.isConflict
          ? 'This task changed elsewhere — reloading.'
          : toUserMessage(err),
      )
      onChanged()
    } finally {
      setBusy(false)
    }
  }

  return (
    <span className={`status-control${compact ? ' compact' : ''}`}>
      <label className="sr-only" htmlFor={`status-${task.id}`}>
        Change status of {task.name}
      </label>
      <select
        id={`status-${task.id}`}
        value=""
        disabled={busy}
        onChange={(e) => {
          if (e.target.value) change(e.target.value as TaskStatus)
        }}
      >
        <option value="">{busy ? 'Saving…' : compact ? 'Move to…' : 'Change status…'}</option>
        {allowed.map((s) => (
          <option key={s} value={s}>
            Mark as {humanizeToken(s)}
          </option>
        ))}
      </select>
      {error && (
        <span className="text-danger status-control-error" role="alert">
          {error}
        </span>
      )}
    </span>
  )
}
