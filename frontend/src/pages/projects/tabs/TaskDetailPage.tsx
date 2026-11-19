import { useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import { useProjectWorkspace } from '../../../context/ProjectWorkspaceContext'
import { useAsyncData } from '../../../hooks/useAsyncData'
import { tasksApi } from '../../../api/tasks'
import { checklistsApi } from '../../../api/checklists'
import { activityApi } from '../../../api/collaboration'
import { LoadingState } from '../../../components/common/LoadingState'
import { ErrorState, InlineError } from '../../../components/common/ErrorState'
import { TaskStatusBadge, ArchivedBadge, PriorityBadge, taskStatusLabel } from '../../../components/common/Badge'
import { TextField, TextareaField, SelectField, FormActions } from '../../../components/forms/fields'
import { ProjectPersonField } from '../../../components/forms/ProjectPersonField'
import { usePeople } from '../../../context/PeopleContext'
import { Modal } from '../../../components/common/Modal'
import { toUserMessage } from '../../../api/errorMessage'
import { ApiError } from '../../../api/client'
import { formatDate, formatDateTime } from '../../../utils/format'
import { planFromTask, planToRequest, TaskPlanningInputs, type TaskPlan } from '../../../components/task/TaskPlanningFields'
import { TaskCommentsPanel, TaskFilesPanel } from './task/TaskPanels'
import { TaskStatusControl } from '../../../components/task/TaskStatusControl'
import { TASK_STATUS_TRANSITIONS, type Task, type TaskStatus, type ChecklistItem } from '../../../types/work'

type TaskTab = 'details' | 'comments' | 'files' | 'history'

/** V3 Task Details: the facts, a simple checklist, comments, attachments and the task's history. */
const TASK_TABS: { id: TaskTab; label: string }[] = [
  { id: 'details', label: 'Details' },
  { id: 'comments', label: 'Comments' },
  { id: 'files', label: 'Attachments' },
  { id: 'history', label: 'History' },
]

export function TaskDetailPage() {
  const { taskId } = useParams<{ taskId: string }>()
  const { project, can } = useProjectWorkspace()
  const task = useAsyncData(() => tasksApi.get(taskId!), [taskId])
  const [tab, setTab] = useState<TaskTab>('details')
  const [editOpen, setEditOpen] = useState(false)

  if (task.loading) return <LoadingState label="Loading task…" />
  if (task.error) return <ErrorState message={task.error} onRetry={task.reload} />
  if (!task.data) return null

  const t = task.data
  const canEdit = can('EDIT_TASKS') && !t.archived

  return (
    <div className="task-page">
      <Link className="task-back" to="../tasks" relative="path">
        ← All tasks
      </Link>
      <div className="task-layout">
        <div className="task-main">
          <header className="task-head">
            <div>
              <h2>{t.name}</h2>
              {t.archived && <ArchivedBadge />}
            </div>
            <div className="page-actions">
              {canEdit && (
                <button className="btn btn-sm" onClick={() => setEditOpen(true)}>
                  Edit
                </button>
              )}
            </div>
          </header>

          <nav className="task-tabs" aria-label="Task sections">
            {TASK_TABS.map((item) => (
              <button
                key={item.id}
                type="button"
                className={`task-tab${tab === item.id ? ' active' : ''}`}
                aria-current={tab === item.id ? 'page' : undefined}
                onClick={() => setTab(item.id)}
              >
                {item.label}
              </button>
            ))}
          </nav>

          {tab === 'details' && (
            <>
              <section className="card">
                <h3>Description</h3>
                {t.description ? <p className="task-description">{t.description}</p> : <p className="text-muted">No description.</p>}
              </section>
              <ChecklistSection taskId={t.id} canEdit={canEdit} />
            </>
          )}
          {tab === 'comments' && (
            <section className="card">
              <TaskCommentsPanel task={t} />
            </section>
          )}
          {tab === 'files' && (
            <section className="card">
              <TaskFilesPanel task={t} canEdit={canEdit} />
            </section>
          )}
          {tab === 'history' && <TaskHistory task={t} />}
        </div>

        <TaskSidePanel task={t} projectName={project.name} canEdit={canEdit} onChanged={task.reload} />
      </div>

      {editOpen && <EditTaskDialog task={t} onClose={() => setEditOpen(false)} onSaved={task.reload} />}
    </div>
  )
}

/** The task's facts at a glance: project, status, priority, assignee, due date and when it changed. */
function TaskSidePanel({
  task,
  projectName,
  canEdit,
  onChanged,
}: {
  task: Task
  projectName: string
  canEdit: boolean
  onChanged: () => void
}) {
  const { nameOf } = usePeople()
  return (
    <aside className="task-side" aria-label="Task summary">
      <dl className="task-facts">
        <div>
          <dt>Project</dt>
          <dd>{projectName}</dd>
        </div>
        <div>
          <dt>Status</dt>
          <dd>
            <TaskStatusBadge status={task.status} />
            {canEdit && <TaskStatusControl task={task} onChanged={onChanged} compact />}
          </dd>
        </div>
        <div>
          <dt>Priority</dt>
          <dd>
            <PriorityBadge priority={task.priority} />
          </dd>
        </div>
        <div>
          <dt>Assignee</dt>
          <dd>{task.assigneeId ? nameOf(task.assigneeId) : <span className="text-muted">Unassigned</span>}</dd>
        </div>
        <div>
          <dt>Due date</dt>
          <dd>{task.dueDate ? formatDate(task.dueDate) : <span className="text-muted">No due date</span>}</dd>
        </div>
      </dl>
      <p className="task-side-meta text-faint">
        Created {formatDateTime(task.createdAt)}
        <br />
        Last changed {formatDateTime(task.updatedAt)}
      </p>
    </aside>
  )
}

/** Who created, assigned, changed and completed this task, and when, from the project's activity. */
function TaskHistory({ task }: { task: Task }) {
  const { nameOf } = usePeople()
  const activity = useAsyncData(() => activityApi.list(task.projectId, 0, 200), [task.projectId, task.updatedAt])
  if (activity.loading) return <LoadingState label="Loading history…" />
  if (activity.error) return <ErrorState message={activity.error} onRetry={activity.reload} />
  const quoted = '"' + task.name + '"'
  const entries = (activity.data?.content ?? []).filter((entry) => entry.summary.includes(quoted))
  return (
    <section className="card">
      <h3>History</h3>
      {entries.length === 0 ? (
        <p className="text-muted">No changes recorded yet.</p>
      ) : (
        <ul className="list-plain task-history">
          {entries.map((entry) => (
            <li key={entry.id}>
              <span>{entry.summary}</span>
              <span className="text-faint">
                {entry.actorId ? nameOf(entry.actorId) + ' · ' : ''}
                {formatDateTime(entry.occurredAt)}
              </span>
            </li>
          ))}
        </ul>
      )}
    </section>
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
  const [plan, setPlan] = useState<TaskPlan>(planFromTask(task))
  const [status, setStatus] = useState<TaskStatus | ''>('')
  const [moreOpen, setMoreOpen] = useState(false)
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
        ...planToRequest(plan),
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
        <TextField id="edit-task-name" label="Task Title" value={name} onChange={setName} required maxLength={200} />
        <div className="form-row">
          <TextField id="edit-task-due" label="Due date" type="date" value={dueDate} onChange={setDueDate} />
          <ProjectPersonField
            id="edit-task-assignee"
            label="Assignee"
            projectId={task.projectId}
            value={assigneeId}
            onChange={setAssigneeId}
          />
        </div>
        <SelectField
          id="edit-task-status"
          label={`Status (currently ${taskStatusLabel(task.status)})`}
          value={status}
          onChange={(v) => setStatus(v as TaskStatus)}
          allowEmpty
          emptyLabel="Keep current status"
          options={allowedStatuses.map((s) => ({ value: s, label: taskStatusLabel(s) }))}
          hint={allowedStatuses.length === 0 ? 'No further transitions allowed.' : undefined}
        />
        <button
          type="button"
          className="btn btn-sm btn-ghost more-details-toggle"
          aria-expanded={moreOpen}
          onClick={() => setMoreOpen(!moreOpen)}
        >
          {moreOpen ? 'Fewer details' : 'More Details'}
        </button>
        {moreOpen && (
          <>
            <TaskPlanningInputs idPrefix="edit-task" plan={plan} onChange={setPlan} />
            <TextareaField id="edit-task-description" label="Description" value={description} onChange={setDescription} />
          </>
        )}
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
        <form onSubmit={handleAdd} className="inline-add">
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

