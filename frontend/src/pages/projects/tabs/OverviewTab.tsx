import { usePeople } from '../../../context/PeopleContext'
import { useState } from 'react'
import { Link, useLocation, useNavigate } from 'react-router-dom'
import { useProjectWorkspace } from '../../../context/ProjectWorkspaceContext'
import { useAsyncData } from '../../../hooks/useAsyncData'
import { membersApi } from '../../../api/members'
import { tasksApi } from '../../../api/tasks'
import { ErrorState } from '../../../components/common/ErrorState'
import { Skeleton } from '../../../components/common/Skeleton'
import { Icon } from '../../../components/common/Icon'
import { Badge, ArchivedBadge, ProjectStatusBadge, TaskStatusBadge } from '../../../components/common/Badge'
import { formatDate, humanizeToken, todayIso } from '../../../utils/format'
import { EditProjectDialog } from '../EditProjectDialog'
import { ArchiveProjectDialog } from '../ArchiveProjectDialog'
import { CreateTaskDialog } from '../CreateTaskDialog'
import type { Task } from '../../../types/work'

/** Task completion summary for a project, worked out from its tasks (V3: never entered by hand). */
export function completionSummary(tasks: Task[], today: string) {
  const total = tasks.length
  const done = tasks.filter((t) => t.status === 'COMPLETED').length
  const open = tasks.filter((t) => t.status !== 'COMPLETED')
  return {
    total,
    done,
    open: open.length,
    blocked: open.filter((t) => t.status === 'BLOCKED').length,
    overdue: open.filter((t) => t.dueDate !== null && t.dueDate < today).length,
    unassigned: open.filter((t) => !t.assigneeId).length,
    progress: total > 0 ? Math.round((done / total) * 100) : null,
    attention: open
      .filter((t) => t.status === 'BLOCKED' || (t.dueDate !== null && t.dueDate < today))
      .sort((a, b) => (a.dueDate ?? '9999').localeCompare(b.dueDate ?? '9999'))
      .slice(0, 6),
  }
}

/**
 * V3 Project Details > Overview: owner, dates, status and the task completion summary, with the
 * work that needs attention and who is on the project.
 */
export function OverviewTab() {
  const { nameOf } = usePeople()
  const { project, can } = useProjectWorkspace()
  const navigate = useNavigate()
  const location = useLocation()
  // The projects list's "Edit or archive" opens this page with the edit form already open.
  const [editOpen, setEditOpen] = useState(() => Boolean((location.state as { edit?: boolean } | null)?.edit))
  const [archiveOpen, setArchiveOpen] = useState(false)
  const [taskOpen, setTaskOpen] = useState(false)

  const tasks = useAsyncData(() => tasksApi.list(project.id, { sortBy: 'DUE_DATE', sortDir: 'ASC' }), [project.id])
  const members = useAsyncData(() => membersApi.list(project.id), [project.id])
  const summary = tasks.data ? completionSummary(tasks.data, todayIso()) : null

  return (
    <div className="overview">
      <header className="overview-header">
        <div className="overview-title">
          <h1>Overview</h1>
          <div className="overview-badges">
            {project.archived && <ArchivedBadge />}
          </div>
          {project.description ? (
            <p className="overview-description">{project.description}</p>
          ) : (
            can('EDIT_PROJECT') && (
              <p className="overview-description text-faint">
                No description yet —{' '}
                <button type="button" className="link-btn" onClick={() => setEditOpen(true)}>
                  add one
                </button>
                .
              </p>
            )
          )}
        </div>
        <div className="page-actions">
          {can('EDIT_PROJECT') && !project.archived && (
            <button className="btn btn-primary" onClick={() => setTaskOpen(true)}>
              <Icon name="plus" size={15} /> Add Task
            </button>
          )}
          {can('MANAGE_MEMBERS') && !project.archived && (
            <Link className="btn" to="../members" relative="path">
              <Icon name="member" size={15} /> Add Member
            </Link>
          )}
        </div>
      </header>

      <div className="overview-grid">
        <div className="overview-col">
        <section className="card" aria-labelledby="ov-summary">
          <h2 id="ov-summary">Summary</h2>
          <dl className="kv">
            <div>
              <dt>Owner</dt>
              <dd>{nameOf(project.ownerId)}</dd>
            </div>
            <div>
              <dt>Status</dt>
              <dd>
                <ProjectStatusBadge status={project.status} /> {project.archived && <ArchivedBadge />}
              </dd>
            </div>
            <div>
              <dt>Start date</dt>
              <dd>{formatDate(project.startDate)}</dd>
            </div>
            <div>
              <dt>Due date</dt>
              <dd>{formatDate(project.targetEndDate)}</dd>
            </div>
          </dl>
        </section>

        <section className="card" aria-labelledby="ov-progress">
          <h2 id="ov-progress">Task completion</h2>
          {tasks.loading && <Skeleton height={10} />}
          {tasks.error && <ErrorState message={tasks.error} onRetry={tasks.reload} />}
          {summary && (
            <>
              <div className="progress-row">
                <div className="progress-bar" role="progressbar" aria-valuenow={summary.progress ?? 0} aria-valuemin={0} aria-valuemax={100}>
                  <div className="progress-fill" style={{ width: `${summary.progress ?? 0}%` }} />
                </div>
                <strong>{summary.progress === null ? 'No tasks' : `${summary.progress}%`}</strong>
              </div>
              <p className="text-muted">
                {summary.total === 0
                  ? 'This project has no tasks yet, so there is no progress to show.'
                  : `${summary.done} of ${summary.total} tasks done · ${summary.overdue} overdue · ${summary.blocked} blocked · ${summary.unassigned} unassigned`}
              </p>
            </>
          )}
        </section>

        </div>

        <section className="card" aria-labelledby="ov-work">
          <div className="card-title-row">
            <h2 id="ov-work">Overdue &amp; blocked work</h2>
            <Link to="../tasks" relative="path">
              All tasks
            </Link>
          </div>
          {tasks.loading && <Skeleton height={48} />}
          {summary && summary.attention.length === 0 && (
            <p className="text-muted">
              <Icon name="tasks" size={14} /> Nothing is overdue or blocked right now.
            </p>
          )}
          {summary && summary.attention.length > 0 && (
            <ul className="list-plain">
              {summary.attention.map((task) => (
                <li key={task.id} className="work-item">
                  <Link to={`../tasks/${task.id}`} relative="path">
                    {task.name}
                  </Link>
                  <span>
                    <TaskStatusBadge status={task.status} />{' '}
                    {task.dueDate && task.dueDate < todayIso() && <span className="text-danger">past due</span>}{' '}
                    {task.dueDate && <span className="text-faint">due {formatDate(task.dueDate)}</span>}
                  </span>
                </li>
              ))}
            </ul>
          )}
        </section>

        <section className="card" aria-labelledby="ov-team">
          <div className="card-title-row">
            <h2 id="ov-team">Members</h2>
            <Link to="../members" relative="path">
              Manage
            </Link>
          </div>
          {members.loading && <Skeleton height={32} />}
          {members.error && <ErrorState message={members.error} onRetry={members.reload} />}
          {members.data && (
            <ul className="list-plain">
              {members.data.slice(0, 6).map((m) => (
                <li key={m.id} className="work-item">
                  <span>{nameOf(m.userId)}</span>
                  <Badge tone={m.role === 'OWNER' ? 'primary' : 'neutral'}>{humanizeToken(m.role)}</Badge>
                </li>
              ))}
              {members.data.length > 6 && <li className="text-faint">+{members.data.length - 6} more</li>}
            </ul>
          )}
        </section>
      </div>

      <div className="overview-footer">
        {can('EDIT_PROJECT') && !project.archived && (
          <button className="btn btn-ghost btn-sm" onClick={() => setEditOpen(true)}>
            Edit project
          </button>
        )}
        {can('ARCHIVE_PROJECT') && !project.archived && (
          <button className="btn btn-ghost btn-sm text-danger" onClick={() => setArchiveOpen(true)}>
            Archive project
          </button>
        )}
      </div>

      {editOpen && <EditProjectDialog onClose={() => setEditOpen(false)} />}
      {archiveOpen && <ArchiveProjectDialog onClose={() => setArchiveOpen(false)} />}
      {taskOpen && (
        <CreateTaskDialog
          projectId={project.id}
          onClose={() => setTaskOpen(false)}
          onCreated={(task) => navigate(`/projects/${project.id}/tasks/${task.id}`)}
        />
      )}
    </div>
  )
}
