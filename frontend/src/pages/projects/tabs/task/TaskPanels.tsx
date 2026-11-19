import { useRef, useState } from 'react'
import { attachmentsApi, taskCommentsApi, type Attachment } from '../../../../api/collaboration'
import { toUserMessage } from '../../../../api/errorMessage'
import { ErrorState, InlineError } from '../../../../components/common/ErrorState'
import { Icon } from '../../../../components/common/Icon'
import { LoadingState } from '../../../../components/common/LoadingState'
import { useProjectPeople } from '../../../../components/forms/ProjectPersonField'
import { MentionComposer } from '../../../../components/task/MentionComposer'
import { useIdentity } from '../../../../context/IdentityContext'
import { usePeople } from '../../../../context/PeopleContext'
import { useAsyncData } from '../../../../hooks/useAsyncData'
import type { Task } from '../../../../types/work'
import { formatDateTime, formatFileSize } from '../../../../utils/format'
import { Avatar } from '../../../../components/common/Avatar'

export function TaskCommentsPanel({ task, onChanged }: { task: Task; onChanged?: () => void }) {
  const { nameOf } = usePeople()
  const { identity } = useIdentity()
  const people = useProjectPeople(task.projectId)
  const { data, loading, error, reload } = useAsyncData(() => taskCommentsApi.list(task.id), [task.id])

  async function remove(id: string) {
    await taskCommentsApi.remove(id)
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
              <Avatar userId={comment.authorId} name={nameOf(comment.authorId)} className="task-avatar" />
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
