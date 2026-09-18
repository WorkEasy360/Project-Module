import { useState } from 'react'
import { useProjectWorkspace } from '../../../context/ProjectWorkspaceContext'
import { useIdentity } from '../../../context/IdentityContext'
import { useAsyncData } from '../../../hooks/useAsyncData'
import { commentsApi } from '../../../api/comments'
import { LoadingState } from '../../../components/common/LoadingState'
import { ErrorState, InlineError } from '../../../components/common/ErrorState'
import { EmptyState } from '../../../components/common/EmptyState'
import { Pagination } from '../../../components/common/Pagination'
import { ConfirmDialog } from '../../../components/common/ConfirmDialog'
import { formatDateTime } from '../../../utils/format'
import { toUserMessage } from '../../../api/errorMessage'
import type { Comment } from '../../../types/work'

export function CommentsTab() {
  const { project, can } = useProjectWorkspace()
  const { identity } = useIdentity()
  const [page, setPage] = useState(0)
  const [newBody, setNewBody] = useState('')
  const [postError, setPostError] = useState<string | null>(null)
  const [posting, setPosting] = useState(false)
  const [editing, setEditing] = useState<Comment | null>(null)
  const [editBody, setEditBody] = useState('')
  const [deleteTarget, setDeleteTarget] = useState<Comment | null>(null)

  const { data, loading, error, reload } = useAsyncData(
    () => commentsApi.list(project.id, page, 20),
    [project.id, page],
  )

  async function handlePost(event: React.FormEvent) {
    event.preventDefault()
    if (!newBody.trim()) return
    setPosting(true)
    setPostError(null)
    try {
      await commentsApi.create(project.id, { body: newBody })
      setNewBody('')
      reload()
    } catch (err) {
      setPostError(toUserMessage(err))
    } finally {
      setPosting(false)
    }
  }

  async function handleSaveEdit() {
    if (!editing) return
    await commentsApi.update(editing.id, { body: editBody, version: editing.version })
    setEditing(null)
    reload()
  }

  return (
    <div>
      <div className="card">
        <form onSubmit={handlePost} className="form-grid">
          {postError && <InlineError message={postError} />}
          <div className="form-field">
            <label htmlFor="new-comment">Add a comment</label>
            <textarea
              id="new-comment"
              value={newBody}
              onChange={(e) => setNewBody(e.target.value)}
              placeholder="Write a comment…"
            />
          </div>
          <div className="form-actions">
            <button type="submit" className="btn btn-primary" disabled={posting || !newBody.trim()}>
              {posting ? 'Posting…' : 'Post Comment'}
            </button>
          </div>
        </form>
      </div>

      {loading && <LoadingState />}
      {error && <ErrorState message={error} onRetry={reload} />}
      {data && data.content.length === 0 && <EmptyState title="No comments yet." />}

      {data && data.content.length > 0 && (
        <ul className="list-plain">
          {data.content.map((c) => {
            const isAuthor = identity?.userId === c.authorId
            const isEditing = editing?.id === c.id
            return (
              <li key={c.id} className="comment-item">
                <div className="comment-meta">
                  <span>
                    {c.authorId} · {formatDateTime(c.createdAt)}
                    {c.updatedAt !== c.createdAt && ' (edited)'}
                  </span>
                  <span className="page-actions">
                    {isAuthor && !isEditing && (
                      <button
                        className="btn btn-sm"
                        onClick={() => {
                          setEditing(c)
                          setEditBody(c.body)
                        }}
                      >
                        Edit
                      </button>
                    )}
                    {(isAuthor || can('EDIT_PROJECT')) && (
                      <button className="btn btn-sm btn-danger" onClick={() => setDeleteTarget(c)}>
                        Delete
                      </button>
                    )}
                  </span>
                </div>
                {isEditing ? (
                  <div className="form-grid">
                    <textarea value={editBody} onChange={(e) => setEditBody(e.target.value)} />
                    <div className="form-actions">
                      <button className="btn btn-sm" onClick={() => setEditing(null)}>
                        Cancel
                      </button>
                      <button className="btn btn-sm btn-primary" onClick={handleSaveEdit}>
                        Save
                      </button>
                    </div>
                  </div>
                ) : (
                  <p style={{ margin: 0 }}>{c.body}</p>
                )}
              </li>
            )
          })}
        </ul>
      )}

      {data && (
        <Pagination page={data.page} totalPages={data.totalPages} totalElements={data.totalElements} onChange={setPage} />
      )}

      {deleteTarget && (
        <ConfirmDialog
          title="Delete comment"
          message="Delete this comment? This cannot be undone."
          confirmLabel="Delete"
          onConfirm={async () => {
            await commentsApi.remove(deleteTarget.id)
            reload()
          }}
          onClose={() => setDeleteTarget(null)}
        />
      )}
    </div>
  )
}
