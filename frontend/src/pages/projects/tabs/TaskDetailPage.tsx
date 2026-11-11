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
import { TaskStatusBadge, ArchivedBadge, PriorityBadge } from '../../../components/common/Badge'
import { TextField, TextareaField, SelectField, FormActions } from '../../../components/forms/fields'
import { ProjectPersonField } from '../../../components/forms/ProjectPersonField'
import { usePeople } from '../../../context/PeopleContext'
import { Modal } from '../../../components/common/Modal'
import { ConfirmDialog } from '../../../components/common/ConfirmDialog'
import { toUserMessage } from '../../../api/errorMessage'
import { ApiError } from '../../../api/client'
import { formatDate, formatDateTime, formatMinutes, humanizeToken } from '../../../utils/format'
import { followersApi, timeApi } from '../../../api/collaboration'
import { useIdentity } from '../../../context/IdentityContext'
import { useElapsed, useTimer } from '../../../context/TimerContext'
import { planFromTask, planToRequest, recurrenceLabel, TaskPlanningInputs, type TaskPlan } from '../../../components/task/TaskPlanningFields'
import { TaskCommentsPanel, TaskFilesPanel, TaskTimePanel } from './task/TaskPanels'
import { RequestRecommendationDialog } from './AITab'
import { Icon } from '../../../components/common/Icon'
import { TaskStatusControl } from '../../../components/task/TaskStatusControl'
import { TASK_STATUS_TRANSITIONS, type Subtask, type Task, type TaskStatus, type ChecklistItem, type Dependency } from '../../../types/work'

type TaskTab = 'details' | 'comments' | 'files' | 'time' | 'dependencies'

const TASK_TABS: { id: TaskTab; label: string }[] = [
  { id: 'details', label: 'Details' },
  { id: 'comments', label: 'Comments' },
  { id: 'files', label: 'Files' },
  { id: 'time', label: 'Time' },
  { id: 'dependencies', label: 'Dependencies' },
]

export function TaskDetailPage() {
  const { taskId } = useParams<{ taskId: string }>()
  const { project, can } = useProjectWorkspace()
  const task = useAsyncData(() => tasksApi.get(taskId!), [taskId])
  const [tab, setTab] = useState<TaskTab>('details')
  const [editOpen, setEditOpen] = useState(false)
  const [aiOpen, setAiOpen] = useState(false)
  const [timeVersion, setTimeVersion] = useState(0)

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
              {can('EDIT_PROJECT') && !t.archived && (
                <button className="btn btn-sm btn-ghost" onClick={() => setAiOpen(true)}>
                  <Icon name="ai" size={14} /> AI suggestion
                </button>
              )}
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
              <SubtasksSection taskId={t.id} canEdit={canEdit} />
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
          {tab === 'time' && (
            <section className="card">
              <TaskTimePanel task={t} canEdit={canEdit} onChanged={() => setTimeVersion((v) => v + 1)} />
            </section>
          )}
          {tab === 'dependencies' && (
            <DependenciesSection taskId={t.id} projectId={project.id} canEdit={can('EDIT_PROJECT')} />
          )}
        </div>

        <TaskSidePanel task={t} canEdit={canEdit} timeVersion={timeVersion} onChanged={task.reload} />
      </div>

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

/** The task's facts at a glance, and the actions a person takes most often. */
function TaskSidePanel({
  task,
  canEdit,
  timeVersion,
  onChanged,
}: {
  task: Task
  canEdit: boolean
  timeVersion: number
  onChanged: () => void
}) {
  const { nameOf } = usePeople()
  const { identity } = useIdentity()
  const { timer, start, stop } = useTimer()
  const elapsed = useElapsed(timer?.taskId === task.id ? timer.startedAt : null)
  const followers = useAsyncData(() => followersApi.list(task.id), [task.id])
  const logged = useAsyncData(() => timeApi.listForTask(task.id), [task.id, timeVersion, timer?.taskId])
  const [error, setError] = useState<string | null>(null)

  const following = identity ? (followers.data ?? []).includes(identity.userId) : false
  const totalMinutes = (logged.data ?? []).reduce((sum, entry) => sum + entry.minutes, 0)

  async function run(action: () => Promise<unknown>) {
    setError(null)
    try {
      await action()
    } catch (err) {
      setError(toUserMessage(err))
    }
  }

  return (
    <aside className="task-side" aria-label="Task summary">
      {error && <InlineError message={error} />}
      {canEdit && (
        <div className="task-side-timer">
          {timer?.taskId === task.id ? (
            <button type="button" className="btn btn-primary" onClick={() => run(stop)}>
              <span className="timer-dot" aria-hidden="true" /> Stop timer · {elapsed}
            </button>
          ) : (
            <button
              type="button"
              className="btn"
              disabled={Boolean(timer)}
              title={timer ? 'You have a timer running on another task' : undefined}
              onClick={() => run(() => start(task.id))}
            >
              <Icon name="clock" size={15} /> Start timer
            </button>
          )}
        </div>
      )}
      <dl className="task-facts">
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
          <dt>Start date</dt>
          <dd>{formatDate(task.startDate)}</dd>
        </div>
        <div>
          <dt>Due date</dt>
          <dd>{formatDate(task.dueDate)}</dd>
        </div>
        <div>
          <dt>Time</dt>
          <dd>
            {formatMinutes(totalMinutes)}
            {task.estimateMinutes ? ` of ${formatMinutes(task.estimateMinutes)}` : ''}
          </dd>
        </div>
        {task.recurrence !== 'NONE' && (
          <div>
            <dt>Repeats</dt>
            <dd>{recurrenceLabel(task.recurrence)}</dd>
          </div>
        )}
        {task.tags.length > 0 && (
          <div>
            <dt>Tags</dt>
            <dd className="task-tags">
              {task.tags.map((tag) => (
                <span key={tag} className="task-tag">
                  {tag}
                </span>
              ))}
            </dd>
          </div>
        )}
        <div>
          <dt>Followers</dt>
          <dd>
            {(followers.data ?? []).length === 0 ? (
              <span className="text-muted">None</span>
            ) : (
              (followers.data ?? []).map(nameOf).join(', ')
            )}
          </dd>
        </div>
      </dl>
      <button
        type="button"
        className="btn btn-sm btn-ghost task-follow"
        onClick={() =>
          run(async () => {
            await (following ? followersApi.unfollow(task.id) : followersApi.follow(task.id))
            followers.reload()
          })
        }
      >
        <Icon name="bell" size={14} /> {following ? 'Stop following' : 'Follow this task'}
      </button>
      <p className="task-side-meta text-faint">
        Created {formatDateTime(task.createdAt)}
        <br />
        Updated {formatDateTime(task.updatedAt)}
      </p>
    </aside>
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
        <TextField id="edit-task-name" label="Name" value={name} onChange={setName} required maxLength={200} />
        <TextareaField id="edit-task-description" label="Description" value={description} onChange={setDescription} />
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
        <TaskPlanningInputs idPrefix="edit-task" plan={plan} onChange={setPlan} />
        <SelectField
          id="edit-task-status"
          label={`Status (currently ${humanizeToken(task.status)})`}
          value={status}
          onChange={(v) => setStatus(v as TaskStatus)}
          allowEmpty
          emptyLabel="Keep current status"
          options={allowedStatuses.map((s) => ({ value: s, label: humanizeToken(s) }))}
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
        <form onSubmit={handleAdd} className="inline-add">
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

function DependenciesSection({
  taskId,
  projectId,
  canEdit,
}: {
  taskId: string
  projectId: string
  canEdit: boolean
}) {
  const projectTasks = useAsyncData(() => tasksApi.list(projectId, { sortBy: 'NAME', sortDir: 'ASC' }), [projectId])
  const taskName = (id: string) => projectTasks.data?.find((task) => task.id === id)?.name ?? 'A task'
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
        <h3>This task depends on</h3>
      </div>
      {loading && <LoadingState />}
      {error && <ErrorState message={error} onRetry={reload} />}
      {data && data.length === 0 && <p className="text-muted">Nothing yet.</p>}
      {data && data.length > 0 && (
        <ul className="list-plain">
          {data.map((d) => (
            <li key={d.id}>
              {taskName(d.prerequisiteTaskId)}{' '}
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
        <form onSubmit={handleAdd} className="inline-add">
          <select value={newPrereq} onChange={(e) => setNewPrereq(e.target.value)} aria-label="Task this one depends on">
            <option value="">Choose a task</option>
            {(projectTasks.data ?? [])
              .filter((task) => task.id !== taskId && !(data ?? []).some((d) => d.prerequisiteTaskId === task.id))
              .map((task) => (
                <option key={task.id} value={task.id}>
                  {task.name}
                </option>
              ))}
          </select>
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
