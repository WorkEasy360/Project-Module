import { useRef, useState } from 'react'
import { attachmentsApi, taskCommentsApi, timeApi, type Attachment, type TimeEntry } from '../../../../api/collaboration'
import { commentsApi } from '../../../../api/comments'
import { toUserMessage } from '../../../../api/errorMessage'
import { ErrorState, InlineError } from '../../../../components/common/ErrorState'
import { Icon } from '../../../../components/common/Icon'
import { LoadingState } from '../../../../components/common/LoadingState'
import { CheckboxField, TextField } from '../../../../components/forms/fields'
import { useProjectPeople } from '../../../../components/forms/ProjectPersonField'
import { MentionComposer } from '../../../../components/task/MentionComposer'
import { useIdentity } from '../../../../context/IdentityContext'
import { usePeople } from '../../../../context/PeopleContext'
import { useAsyncData } from '../../../../hooks/useAsyncData'
import type { Task } from '../../../../types/work'
import { formatDate, formatDateTime, formatFileSize, formatMinutes, initials, parseHours, todayIso } from '../../../../utils/format'

export function TaskCommentsPanel({ task, onChanged }: { task: Task; onChanged?: () => void }) {
  const { nameOf } = usePeople()
  const { identity } = useIdentity()
  const people = useProjectPeople(task.projectId)
  const { data, loading, error, reload } = useAsyncData(() => taskCommentsApi.list(task.id), [task.id])

  async function remove(id: string) {
    await commentsApi.remove(id)
    reload()
  }

  return (
    <div className="task-panel">
      <MentionComposer
        id="task-comment"
        label="Add a comment"
        people={people.filter((person) => person.value !== identity?.userId)}
        onSubmit={async (body, mentions) => {
          await taskCommentsApi.create(task.id, { body, mentions })
          reload()
          onChanged?.()
        }}
      />
      {loading && <LoadingState />}
      {error && <ErrorState message={error} onRetry={reload} />}
      {data && data.length === 0 && <p className="task-empty">No comments yet.</p>}
      {data && data.length > 0 && (
        <ul className="task-comments">
          {data.map((comment) => (
            <li key={comment.id} className="task-comment">
              <span className="task-avatar" aria-hidden="true">
                {initials(nameOf(comment.authorId))}
              </span>
              <div className="task-comment-main">
                <div className="task-comment-meta">
                  <strong>{nameOf(comment.authorId)}</strong>
                  <span className="text-faint">{formatDateTime(comment.createdAt)}</span>
                  {comment.authorId === identity?.userId && (
                    <button type="button" className="btn-link" onClick={() => remove(comment.id)}>
                      Delete
                    </button>
                  )}
                </div>
                <p>{comment.body}</p>
              </div>
            </li>
          ))}
        </ul>
      )}
    </div>
  )
}

export function TaskFilesPanel({ task, canEdit }: { task: Task; canEdit: boolean }) {
  const { nameOf } = usePeople()
  const { identity } = useIdentity()
  const { data, loading, error, reload } = useAsyncData(() => attachmentsApi.list(task.id), [task.id])
  const [uploading, setUploading] = useState(false)
  const [actionError, setActionError] = useState<string | null>(null)
  const inputRef = useRef<HTMLInputElement>(null)

  async function upload(files: FileList | null) {
    if (!files || files.length === 0) return
    setUploading(true)
    setActionError(null)
    try {
      for (const file of Array.from(files)) {
        await attachmentsApi.upload(task.id, file)
      }
      reload()
    } catch (err) {
      setActionError(toUserMessage(err))
    } finally {
      setUploading(false)
      if (inputRef.current) inputRef.current.value = ''
    }
  }

  async function run(action: () => Promise<unknown>) {
    setActionError(null)
    try {
      await action()
    } catch (err) {
      setActionError(toUserMessage(err))
    }
  }

  return (
    <div className="task-panel">
      {actionError && <InlineError message={actionError} />}
      {canEdit && (
        <div className="task-panel-actions">
          <input ref={inputRef} type="file" multiple hidden aria-label="Choose files" onChange={(e) => upload(e.target.files)} />
          <button type="button" className="btn btn-sm" disabled={uploading} onClick={() => inputRef.current?.click()}>
            <Icon name="paperclip" size={14} /> {uploading ? 'Uploading…' : 'Add files'}
          </button>
          <span className="text-faint">Up to 25 MB each</span>
        </div>
      )}
      {loading && <LoadingState />}
      {error && <ErrorState message={error} onRetry={reload} />}
      {data && data.length === 0 && <p className="task-empty">No files yet.</p>}
      {data && data.length > 0 && (
        <ul className="task-rows">
          {data.map((file: Attachment) => (
            <li key={file.id} className="task-row">
              <Icon name="paperclip" size={15} />
              <button type="button" className="btn-link task-row-main" onClick={() => run(() => attachmentsApi.download(file))}>
                {file.fileName}
              </button>
              <span className="text-faint">
                {formatFileSize(file.sizeBytes)} · {nameOf(file.uploadedBy)}
              </span>
              {(file.uploadedBy === identity?.userId || canEdit) && (
                <button
                  type="button"
                  className="btn-link"
                  onClick={() => run(async () => { await attachmentsApi.remove(file.id); reload() })}
                >
                  Remove
                </button>
              )}
            </li>
          ))}
        </ul>
      )}
    </div>
  )
}

export function TaskTimePanel({
  task,
  canEdit,
  onChanged,
}: {
  task: Task
  canEdit: boolean
  onChanged?: () => void
}) {
  const { nameOf } = usePeople()
  const { identity } = useIdentity()
  const { data, loading, error, reload } = useAsyncData(() => timeApi.listForTask(task.id), [task.id])
  const [hours, setHours] = useState('')
  const [workDate, setWorkDate] = useState(todayIso())
  const [note, setNote] = useState('')
  const [billable, setBillable] = useState(false)
  const [formError, setFormError] = useState<string | null>(null)

  const total = (data ?? []).reduce((sum, entry) => sum + entry.minutes, 0)

  async function handleLog(event: React.FormEvent) {
    event.preventDefault()
    const minutes = parseHours(hours)
    if (minutes === null) {
      setFormError('Enter the time as hours, such as 1.5 or 1:30.')
      return
    }
    setFormError(null)
    try {
      await timeApi.log(task.id, { workDate, minutes, note: note || undefined, billable })
      setHours('')
      setNote('')
      reload()
      onChanged?.()
    } catch (err) {
      setFormError(toUserMessage(err))
    }
  }

  async function remove(entry: TimeEntry) {
    await timeApi.remove(entry.id)
    reload()
    onChanged?.()
  }

  return (
    <div className="task-panel">
      {canEdit && (
        <form className="task-time-form" onSubmit={handleLog}>
          {formError && <InlineError message={formError} />}
          <div className="form-row">
            <TextField id="time-hours" label="Hours" value={hours} onChange={setHours} placeholder="1.5" required />
            <TextField id="time-date" label="Date" type="date" value={workDate} onChange={setWorkDate} required />
          </div>
          <TextField id="time-note" label="Note" value={note} onChange={setNote} maxLength={500} />
          <div className="task-time-actions">
            <CheckboxField id="time-billable" label="Billable" checked={billable} onChange={setBillable} />
            <button type="submit" className="btn btn-sm btn-primary">
              Log time
            </button>
          </div>
        </form>
      )}
      {loading && <LoadingState />}
      {error && <ErrorState message={error} onRetry={reload} />}
      {data && data.length === 0 && <p className="task-empty">No time logged yet.</p>}
      {data && data.length > 0 && (
        <>
          <p className="task-time-total">
            <strong>{formatMinutes(total)}</strong> logged
            {task.estimateMinutes ? ` of ${formatMinutes(task.estimateMinutes)} estimated` : ''}
          </p>
          <ul className="task-rows">
            {data.map((entry) => (
              <li key={entry.id} className="task-row">
                <strong>{formatMinutes(entry.minutes)}</strong>
                <span className="task-row-main">
                  {entry.note ?? <span className="text-faint">No note</span>}
                  {entry.billable && <span className="task-tag">Billable</span>}
                </span>
                <span className="text-faint">
                  {nameOf(entry.userId)} · {formatDate(entry.workDate)}
                </span>
                {entry.userId === identity?.userId && (
                  <button type="button" className="btn-link" onClick={() => remove(entry)}>
                    Remove
                  </button>
                )}
              </li>
            ))}
          </ul>
        </>
      )}
    </div>
  )
}

