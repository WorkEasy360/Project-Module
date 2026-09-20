import { useState } from 'react'
import { Link } from 'react-router-dom'
import { Modal } from '../../../../components/common/Modal'
import { Badge } from '../../../../components/common/Badge'
import { Icon } from '../../../../components/common/Icon'
import { InlineError } from '../../../../components/common/ErrorState'
import { TaskStatusControl } from '../../../../components/task/TaskStatusControl'
import { tasksApi } from '../../../../api/tasks'
import { milestonesApi } from '../../../../api/milestones'
import { ApiError } from '../../../../api/client'
import { toUserMessage } from '../../../../api/errorMessage'
import { formatDate, humanizeToken } from '../../../../utils/format'
import type { Milestone, Task } from '../../../../types/work'
import { entryTone, type EnrichedEntry } from './calendarUtils'

/**
 * Event detail. Read-only by default (the calendar API itself is read-only). When the caller
 * passes `editable` together with the underlying `task` / `milestone` record and an `onChanged`
 * callback, the due date can be changed (and, for tasks, the status) through the real PATCH
 * endpoints with optimistic locking — the record's `version` is sent and a 409 offers a reload.
 * Tasks also get an "Open task" link; milestones and phases link to their list screens.
 */
export function EventDetailDialog({
  entry,
  projectId,
  onClose,
  editable = false,
  task = null,
  milestone = null,
  onChanged,
}: {
  entry: EnrichedEntry
  projectId: string
  onClose: () => void
  /** Show the edit controls (caller checks EDIT_PROJECT and that the project isn't archived). */
  editable?: boolean
  /** The full task record for TASK entries (needed for `version` and the status control). */
  task?: Task | null
  /** The full milestone record for MILESTONE entries (needed for `version`). */
  milestone?: Milestone | null
  /** Called after any successful change so the caller reloads from the database. */
  onChanged?: () => void
}) {
  const tone = entryTone(entry)
  const base = `/projects/${projectId}`
  const target =
    entry.entityType === 'TASK'
      ? { to: `${base}/tasks/${entry.id}`, label: 'Open task' }
      : entry.entityType === 'MILESTONE'
        ? { to: `${base}/milestones`, label: 'Open milestones' }
        : { to: `${base}/phases`, label: 'Open phases' }

  const record = entry.entityType === 'TASK' ? task : entry.entityType === 'MILESTONE' ? milestone : null
  const canEdit = editable && Boolean(onChanged) && record !== null

  const [dueDate, setDueDate] = useState(entry.date ?? '')
  const [syncedDate, setSyncedDate] = useState(entry.date ?? '')
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [conflict, setConflict] = useState(false)

  // Keep the field in sync when the caller reloads the entry (e.g. after a conflict reload):
  // React's "adjust state while rendering" pattern, so there is no extra effect pass.
  if ((entry.date ?? '') !== syncedDate) {
    setSyncedDate(entry.date ?? '')
    setDueDate(entry.date ?? '')
  }

  const dirty = dueDate !== (entry.date ?? '')

  async function saveDueDate(event: React.FormEvent) {
    event.preventDefault()
    if (!record || !onChanged) return
    setSaving(true)
    setError(null)
    setConflict(false)
    try {
      const body = { dueDate: dueDate || null, version: record.version }
      if (entry.entityType === 'TASK') await tasksApi.update(record.id, body)
      else await milestonesApi.update(record.id, body)
      onChanged()
      onClose()
    } catch (err) {
      if (err instanceof ApiError && err.isConflict) setConflict(true)
      else setError(toUserMessage(err))
    } finally {
      setSaving(false)
    }
  }

  return (
    <Modal title={entry.name} onClose={onClose}>
      <div className={`cal-detail tone-${tone}`}>
        <div className="cal-detail-row">
          <Badge tone="neutral">{humanizeToken(entry.entityType)}</Badge>
          {entry.status && <Badge tone={tone}>{humanizeToken(entry.status)}</Badge>}
        </div>
        <dl className="kv">
          {entry.projectName && (
            <div>
              <dt>Project</dt>
              <dd>
                <Link to={`${base}/overview`}>{entry.projectName}</Link>
              </dd>
            </div>
          )}
          {entry.entityType === 'PHASE' ? (
            <>
              <div>
                <dt>Starts</dt>
                <dd>{formatDate(entry.startDate)}</dd>
              </div>
              <div>
                <dt>Ends</dt>
                <dd>{formatDate(entry.endDate)}</dd>
              </div>
            </>
          ) : (
            !canEdit && (
              <div>
                <dt>Due</dt>
                <dd>{formatDate(entry.date)}</dd>
              </div>
            )
          )}
          {entry.entityType === 'TASK' && (
            <div>
              <dt>Assignee</dt>
              <dd className="mono">{entry.assigneeId ?? 'Unassigned'}</dd>
            </div>
          )}
        </dl>

        {canEdit && (
          <div className="cal-detail-edit">
            {conflict && (
              <div className="banner banner-warning" role="alert">
                This item was changed elsewhere — reload it before editing again.{' '}
                <button
                  type="button"
                  className="btn btn-sm"
                  onClick={() => {
                    setConflict(false)
                    onChanged?.()
                  }}
                >
                  Reload
                </button>
              </div>
            )}
            {error && <InlineError message={error} />}
            <form className="cal-detail-field" onSubmit={saveDueDate}>
              <label htmlFor={`cal-due-${entry.id}`}>Due date</label>
              <div className="cal-detail-inline">
                <input
                  id={`cal-due-${entry.id}`}
                  type="date"
                  value={dueDate}
                  disabled={saving}
                  onChange={(e) => setDueDate(e.target.value)}
                />
                <button type="submit" className="btn btn-sm btn-primary" disabled={saving || !dirty}>
                  {saving ? 'Saving…' : 'Save date'}
                </button>
              </div>
            </form>
            {entry.entityType === 'TASK' && task && (
              <div className="cal-detail-field">
                <span className="cal-detail-label">Status</span>
                <TaskStatusControl task={task} onChanged={onChanged!} />
                {task.status === 'COMPLETED' && <span className="text-faint">Completed — no further transitions.</span>}
              </div>
            )}
          </div>
        )}

        <div className="form-actions">
          <button type="button" className="btn" onClick={onClose}>
            Close
          </button>
          <Link className="btn btn-primary" to={target.to}>
            {target.label} <Icon name="chevronRight" size={14} />
          </Link>
        </div>
      </div>
    </Modal>
  )
}
