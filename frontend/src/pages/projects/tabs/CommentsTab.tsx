import { usePeople } from '../../../context/PeopleContext'
import { useProjectPeople } from '../../../components/forms/ProjectPersonField'
import { MentionComposer } from '../../../components/task/MentionComposer'
import { useState } from 'react'
import { useProjectWorkspace } from '../../../context/ProjectWorkspaceContext'
import { useIdentity } from '../../../context/IdentityContext'
import { useAsyncData } from '../../../hooks/useAsyncData'
import { commentsApi } from '../../../api/comments'
import { LoadingState } from '../../../components/common/LoadingState'
import { ErrorState } from '../../../components/common/ErrorState'
import { EmptyState } from '../../../components/common/EmptyState'
import { Pagination } from '../../../components/common/Pagination'
import { ConfirmDialog } from '../../../components/common/ConfirmDialog'
import { formatDateTime } from '../../../utils/format'
import type { Comment } from '../../../types/work'

export function CommentsTab() {
  const { nameOf } = usePeople()
  const { project, can } = useProjectWorkspace()
  const { identity } = useIdentity()
  const [page, setPage] = useState(0)
  const people = useProjectPeople(project.id)
  const [editing, setEditing] = useState<Comment | null>(null)
  const [editBody, setEditBody] = useState('')
  const [deleteTarget, setDeleteTarget] = useState<Comment | null>(null)

  const { data, loading, error, reload } = useAsyncData(
    () => commentsApi.list(project.id, page, 20),
    [project.id, page],
  )

  async function handleSaveEdit() {
    if (!editing) return
    await commentsApi.update(editing.id, { body: editBody, version: editing.version })
    setEditing(null)
    reload()
  }

  return (
    <div>
      <div className="card">
        <MentionComposer
          id="new-comment"
          label="Add a comment"
          people={people.filter((person) => person.value !== identity?.userId)}
          submitLabel="Post comment"
          onSubmit={async (body, mentions) => {
            await commentsApi.create(project.id, { body, mentions })
            reload()
          }}
        />
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
                    {nameOf(c.authorId)} · {formatDateTime(c.createdAt)}
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
