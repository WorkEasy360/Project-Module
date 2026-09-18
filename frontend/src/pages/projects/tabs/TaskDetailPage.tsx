import { useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import { useProjectWorkspace } from '../../../context/ProjectWorkspaceContext'
import { useAsyncData } from '../../../hooks/useAsyncData'
import { tasksApi } from '../../../api/tasks'
import { subtasksApi } from '../../../api/subtasks'
import { checklistsApi } from '../../../api/checklists'
import { dependenciesApi } from '../../../api/dependencies'
import { LoadingState } from '../../../components/common/LoadingState'
import { ErrorState, InlineError } from '../../../components/common/ErrorState'
import { TaskStatusBadge, ArchivedBadge } from '../../../components/common/Badge'
import { TextField, TextareaField, SelectField, FormActions } from '../../../components/forms/fields'
import { Modal } from '../../../components/common/Modal'
import { ConfirmDialog } from '../../../components/common/ConfirmDialog'
import { toUserMessage } from '../../../api/errorMessage'
import { ApiError } from '../../../api/client'
import { formatDate, formatDateTime } from '../../../utils/format'
import { RequestRecommendationDialog } from './AITab'
import { Icon } from '../../../components/common/Icon'
import { TaskStatusControl } from '../../../components/task/TaskStatusControl'
import { TASK_STATUS_TRANSITIONS, type Subtask, type Task, type TaskStatus, type ChecklistItem, type Dependency } from '../../../types/work'

export function TaskDetailPage() {
  const { taskId } = useParams<{ taskId: string }>()
  const { project, can } = useProjectWorkspace()
  const task = useAsyncData(() => tasksApi.get(taskId!), [taskId])
  const [editOpen, setEditOpen] = useState(false)
  const [aiOpen, setAiOpen] = useState(false)

  if (task.loading) return <LoadingState label="Loading task…" />
  if (task.error) return <ErrorState message={task.error} onRetry={task.reload} />
  if (!task.data) return null

  const t = task.data

  return (
    <div>
      <p>
        <Link to="../tasks" relative="path">
          ← Back to tasks
        </Link>
      </p>
      <div className="card">
        <div className="card-title-row">
          <h2>
            {t.name} <TaskStatusBadge status={t.status} /> {t.archived && <ArchivedBadge />}
          </h2>
          {can('EDIT_PROJECT') && !t.archived && (
            <div className="page-actions">
              <TaskStatusControl task={t} onChanged={task.reload} />
              <button className="btn btn-sm btn-ghost" onClick={() => setAiOpen(true)}>
                <Icon name="ai" size={14} /> Get AI suggestion
              </button>
              <button className="btn btn-sm" onClick={() => setEditOpen(true)}>
                Edit
              </button>
            </div>
          )}
        </div>
        {t.description && <p>{t.description}</p>}
        <div className="form-grid">
          <div>
            <strong>Due date:</strong> {formatDate(t.dueDate)} &nbsp; <strong>Assignee:</strong>{' '}
            {t.assigneeId ?? 'Unassigned'}
          </div>
          <div className="text-faint">
            Created {formatDateTime(t.createdAt)} · Updated {formatDateTime(t.updatedAt)}
          </div>
        </div>
      </div>

      <SubtasksSection taskId={t.id} canEdit={can('EDIT_PROJECT')} />
      <ChecklistSection taskId={t.id} canEdit={can('EDIT_PROJECT')} />
      <DependenciesSection taskId={t.id} projectId={project.id} canEdit={can('EDIT_PROJECT')} />

      {editOpen && <EditTaskDialog task={t} onClose={() => setEditOpen(false)} onSaved={task.reload} />}
      {aiOpen && (
        <RequestRecommendationDialog
          projectId={project.id}
          initialType="TASK_IMPROVEMENT"
          initialResourceId={t.id}
          onClose={() => setAiOpen(false)}
          onRequested={() => setAiOpen(false)}
        />
      )}
    </div>
  )
}

export function EditTaskDialog({
  task,
  onClose,
  onSaved,
}: {
  task: Task
  onClose: () => void
  onSaved: () => void
}) {
  const [name, setName] = useState(task.name)
  const [description, setDescription] = useState(task.description ?? '')
  const [dueDate, setDueDate] = useState(task.dueDate ?? '')
  const [assigneeId, setAssigneeId] = useState(task.assigneeId ?? '')
  const [status, setStatus] = useState<TaskStatus | ''>('')
  const [error, setError] = useState<string | null>(null)
  const [conflict, setConflict] = useState(false)
  const [submitting, setSubmitting] = useState(false)

  const allowedStatuses = TASK_STATUS_TRANSITIONS[task.status]

  async function handleSubmit(event: React.FormEvent) {
    event.preventDefault()
    setSubmitting(true)
    setError(null)
    setConflict(false)
    try {
      await tasksApi.update(task.id, {
        name,
        description: description || null,
        dueDate: dueDate || null,
        assigneeId: assigneeId || null,
        ...(status ? { status: status as TaskStatus } : {}),
        version: task.version,
      })
      onSaved()
      onClose()
    } catch (err) {
      if (err instanceof ApiError && err.isConflict) setConflict(true)
      setError(toUserMessage(err))
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <Modal title="Edit Task" onClose={onClose}>
      <form onSubmit={handleSubmit} className="form-grid">
        {error && <InlineError message={error} />}
        {conflict && (
          <div className="banner banner-warning">
            This task was modified elsewhere.{' '}
            <button type="button" className="btn btn-sm" onClick={() => { onSaved(); onClose() }}>
              Reload it
            </button>{' '}
            before editing again.
          </div>
        )}
        <TextField id="edit-task-name" label="Name" value={name} onChange={setName} required maxLength={200} />
        <TextareaField id="edit-task-description" label="Description" value={description} onChange={setDescription} />
        <div className="form-row">
          <TextField id="edit-task-due" label="Due date" type="date" value={dueDate} onChange={setDueDate} />
          <TextField id="edit-task-assignee" label="Assignee ID" value={assigneeId} onChange={setAssigneeId} />
        </div>
        <SelectField
          id="edit-task-status"
          label={`Status (currently ${task.status})`}
          value={status}
          onChange={(v) => setStatus(v as TaskStatus)}
          allowEmpty
          emptyLabel="Keep current status"
          options={allowedStatuses.map((s) => ({ value: s, label: s }))}
          hint={allowedStatuses.length === 0 ? 'No further transitions allowed.' : undefined}
        />
        <FormActions>
          <button type="button" className="btn" onClick={onClose} disabled={submitting}>
            Cancel
          </button>
          <button type="submit" className="btn btn-primary" disabled={submitting || !name.trim()}>
            {submitting ? 'Saving…' : 'Save'}
          </button>
        </FormActions>
      </form>
    </Modal>
  )
}

function SubtasksSection({ taskId, canEdit }: { taskId: string; canEdit: boolean }) {
  const { data, loading, error, reload } = useAsyncData(() => subtasksApi.list(taskId), [taskId])
  const [newName, setNewName] = useState('')
  const [busyId, setBusyId] = useState<string | null>(null)

  async function handleAdd(event: React.FormEvent) {
    event.preventDefault()
    if (!newName.trim()) return
    await subtasksApi.create(taskId, { name: newName })
    setNewName('')
    reload()
  }

  async function toggleComplete(s: Subtask) {
    setBusyId(s.id)
    try {
      await subtasksApi.update(s.id, { completed: !s.completed, version: s.version })
      reload()
    } finally {
      setBusyId(null)
    }
  }

  return (
    <div className="card">
      <div className="card-title-row">
        <h3>Subtasks</h3>
      </div>
      {loading && <LoadingState />}
      {error && <ErrorState message={error} onRetry={reload} />}
      {data && data.length === 0 && <p className="text-muted">No subtasks yet.</p>}
      {data && data.length > 0 && (
        <ul className="list-plain">
          {data.map((s) => (
            <li key={s.id} className={`checklist-item${s.completed ? ' checked' : ''}`}>
              <input
                type="checkbox"
                checked={s.completed}
                disabled={!canEdit || busyId === s.id}
                onChange={() => toggleComplete(s)}
                aria-label={s.name}
              />
              <span>{s.name}</span>
            </li>
          ))}
        </ul>
      )}
      {canEdit && (
        <form onSubmit={handleAdd} className="toolbar" style={{ marginTop: 10, marginBottom: 0 }}>
          <input
            type="text"
            placeholder="New subtask name"
            value={newName}
            onChange={(e) => setNewName(e.target.value)}
            aria-label="New subtask name"
          />
          <button type="submit" className="btn btn-sm">
            Add
          </button>
        </form>
      )}
    </div>
  )
}

function ChecklistSection({ taskId, canEdit }: { taskId: string; canEdit: boolean }) {
  const { data, loading, error, reload } = useAsyncData(() => checklistsApi.list(taskId), [taskId])
  const [newText, setNewText] = useState('')
  const [busyId, setBusyId] = useState<string | null>(null)

  async function handleAdd(event: React.FormEvent) {
    event.preventDefault()
    if (!newText.trim()) return
    await checklistsApi.create(taskId, { text: newText })
    setNewText('')
    reload()
  }

  async function toggleChecked(item: ChecklistItem) {
    setBusyId(item.id)
    try {
      await checklistsApi.update(item.id, { checked: !item.checked, version: item.version })
      reload()
    } finally {
      setBusyId(null)
    }
  }

  return (
    <div className="card">
      <div className="card-title-row">
        <h3>Checklist</h3>
      </div>
      {loading && <LoadingState />}
      {error && <ErrorState message={error} onRetry={reload} />}
      {data && data.length === 0 && <p className="text-muted">No checklist items yet.</p>}
      {data && data.length > 0 && (
        <ul className="list-plain">
          {data.map((item) => (
            <li key={item.id} className={`checklist-item${item.checked ? ' checked' : ''}`}>
              <input
                type="checkbox"
                checked={item.checked}
                disabled={!canEdit || busyId === item.id}
                onChange={() => toggleChecked(item)}
                aria-label={item.text}
              />
              <span>{item.text}</span>
            </li>
          ))}
        </ul>
      )}
      {canEdit && (
        <form onSubmit={handleAdd} className="toolbar" style={{ marginTop: 10, marginBottom: 0 }}>
          <input
            type="text"
            placeholder="New checklist item"
            value={newText}
            onChange={(e) => setNewText(e.target.value)}
            aria-label="New checklist item text"
          />
          <button type="submit" className="btn btn-sm">
            Add
          </button>
        </form>
      )}
    </div>
  )
}

function DependenciesSection({
  taskId,
  canEdit,
}: {
  taskId: string
  projectId: string
  canEdit: boolean
}) {
  const { data, loading, error, reload } = useAsyncData(() => dependenciesApi.list(taskId), [taskId])
  const [newPrereq, setNewPrereq] = useState('')
  const [addError, setAddError] = useState<string | null>(null)
  const [breakTarget, setBreakTarget] = useState<Dependency | null>(null)

  async function handleAdd(event: React.FormEvent) {
    event.preventDefault()
    if (!newPrereq.trim()) return
    setAddError(null)
    try {
      await dependenciesApi.create(taskId, { prerequisiteTaskId: newPrereq.trim() })
      setNewPrereq('')
      reload()
    } catch (err) {
      setAddError(toUserMessage(err))
    }
  }

  return (
    <div className="card">
      <div className="card-title-row">
        <h3>Dependencies (this task depends on)</h3>
      </div>
      {loading && <LoadingState />}
      {error && <ErrorState message={error} onRetry={reload} />}
      {data && data.length === 0 && <p className="text-muted">No dependencies recorded.</p>}
      {data && data.length > 0 && (
        <ul className="list-plain">
          {data.map((d) => (
            <li key={d.id}>
              Prerequisite task: <code>{d.prerequisiteTaskId}</code>{' '}
              {d.broken ? (
                <span className="badge badge-danger">Broken</span>
              ) : (
                <span className="badge badge-success">OK</span>
              )}{' '}
              {canEdit && !d.broken && (
                <button className="btn btn-sm" onClick={() => setBreakTarget(d)}>
                  Mark broken
                </button>
              )}
            </li>
          ))}
        </ul>
      )}
      {canEdit && (
        <form onSubmit={handleAdd} className="toolbar" style={{ marginTop: 10, marginBottom: 0 }}>
          <input
            type="text"
            placeholder="Prerequisite task ID (UUID)"
            value={newPrereq}
            onChange={(e) => setNewPrereq(e.target.value)}
            aria-label="Prerequisite task ID"
          />
          <button type="submit" className="btn btn-sm">
            Add dependency
          </button>
        </form>
      )}
      {addError && <InlineError message={addError} />}

      {breakTarget && (
        <ConfirmDialog
          title="Mark dependency broken"
          message="Mark this dependency as broken? This cannot be reversed through this API."
          confirmLabel="Mark broken"
          onConfirm={async () => {
            await dependenciesApi.update(breakTarget.id, { broken: true, version: breakTarget.version })
            reload()
          }}
          onClose={() => setBreakTarget(null)}
        />
      )}
    </div>
  )
}
