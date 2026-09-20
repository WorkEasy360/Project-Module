import { useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { useProjectWorkspace } from '../../../context/ProjectWorkspaceContext'
import { useAsyncData } from '../../../hooks/useAsyncData'
import { healthApi } from '../../../api/health'
import { reportsApi } from '../../../api/reports'
import { membersApi } from '../../../api/members'
import { tasksApi } from '../../../api/tasks'
import { delayDetectionApi } from '../../../api/delayDetection'
import { ErrorState } from '../../../components/common/ErrorState'
import { Skeleton, StatGridSkeleton } from '../../../components/common/Skeleton'
import { Icon } from '../../../components/common/Icon'
import { Badge, ArchivedBadge, TaskStatusBadge } from '../../../components/common/Badge'
import { formatDate } from '../../../utils/format'
import { EditProjectDialog } from '../EditProjectDialog'
import { ArchiveProjectDialog } from '../ArchiveProjectDialog'
import { CreateTaskDialog } from '../CreateTaskDialog'
import { RequestRecommendationDialog } from './AITab'
import type { DelayedItem, ProjectHealth, Task } from '../../../types/work'

/**
 * The first screen after opening a project. Answers, in order: where does this project stand,
 * what needs attention, who's on it — then offers the two most common actions. Every number here
 * comes from a real endpoint (health, reports/summary, tasks?status=…, members).
 */
export function OverviewTab() {
  const { project, can } = useProjectWorkspace()
  const navigate = useNavigate()
  const [editOpen, setEditOpen] = useState(false)
  const [archiveOpen, setArchiveOpen] = useState(false)
  const [taskOpen, setTaskOpen] = useState(false)
  const [aiOpen, setAiOpen] = useState(false)

  const health = useAsyncData(() => healthApi.get(project.id), [project.id])
  const summary = useAsyncData(() => reportsApi.summary(project.id), [project.id])
  const members = useAsyncData(() => membersApi.list(project.id), [project.id])
  // Stored OVERDUE/BLOCKED tasks plus the backend's delayed items (due date passed, not completed —
  // the same rule the health card's "Overdue items" count uses), so the two cards always agree.
  const attention = useAsyncData(
    () =>
      Promise.all([
        tasksApi.list(project.id, { status: 'OVERDUE', sortBy: 'DUE_DATE', sortDir: 'ASC' }),
        tasksApi.list(project.id, { status: 'BLOCKED', sortBy: 'CREATED_AT', sortDir: 'DESC' }),
        delayDetectionApi.get(project.id),
      ]).then(([overdue, blocked, delayed]) => mergeAttention([...overdue, ...blocked], delayed.items).slice(0, 8)),
    [project.id],
  )

  const total = summary.data ? Object.values(summary.data.taskCountByStatus).reduce((a, b) => a + b, 0) : 0
  const done = summary.data?.taskCountByStatus.COMPLETED ?? 0
  const progress = total > 0 ? Math.round((done / total) * 100) : null

  return (
    <div className="overview">
      <header className="overview-header">
        <div className="overview-title">
          {/* Name, status and priority live in the project header card above the tabs. */}
          <h1>Overview</h1>
          <div className="overview-badges">
            {project.archived && <ArchivedBadge />}
            {health.data && <HealthBadge health={health.data} />}
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
        <section className="card" aria-labelledby="ov-progress">
          <h2 id="ov-progress">Progress</h2>
          {summary.loading && <Skeleton height={10} />}
          {summary.error && <ErrorState message={summary.error} onRetry={summary.reload} />}
          {summary.data && (
            <>
              <div className="progress-row">
                <div className="progress-bar" role="progressbar" aria-valuenow={progress ?? 0} aria-valuemin={0} aria-valuemax={100}>
                  <div className="progress-fill" style={{ width: `${progress ?? 0}%` }} />
                </div>
                <strong>{progress === null ? '—' : `${progress}%`}</strong>
              </div>
              <p className="text-muted">
                {total === 0
                  ? 'No tasks yet.'
                  : `${done} of ${total} tasks completed · ${summary.data.taskCountByStatus.OVERDUE} overdue · ${summary.data.taskCountByStatus.BLOCKED} blocked`}
              </p>
              <dl className="kv">
                <div>
                  <dt>Start</dt>
                  <dd>{formatDate(project.startDate)}</dd>
                </div>
                <div>
                  <dt>Target end</dt>
                  <dd>{formatDate(project.targetEndDate)}</dd>
                </div>
              </dl>
            </>
          )}
        </section>

        <section className="card" aria-labelledby="ov-attention">
          <div className="card-title-row">
            <h2 id="ov-attention">Needs attention</h2>
            <Link to="../health" relative="path">
              Full health
            </Link>
          </div>
          {health.loading && <StatGridSkeleton count={3} />}
          {health.error && <ErrorState message={health.error} onRetry={health.reload} />}
          {health.data && <AttentionList health={health.data} projectId={project.id} />}
        </section>

        <section className="card" aria-labelledby="ov-work">
          <div className="card-title-row">
            <h2 id="ov-work">Overdue &amp; blocked work</h2>
            <Link to="../tasks" relative="path">
              All tasks
            </Link>
          </div>
          {attention.loading && <Skeleton height={48} />}
          {attention.error && <ErrorState message={attention.error} onRetry={attention.reload} />}
          {attention.data && attention.data.length === 0 && (
            <p className="text-muted">
              <Icon name="tasks" size={14} /> Nothing is overdue or blocked right now.
            </p>
          )}
          {attention.data && attention.data.length > 0 && (
            <ul className="list-plain">
              {attention.data.map((row) => (
                <li key={`${row.kind}-${row.id}`} className="work-item">
                  <Link to={row.to} relative="path">
                    {row.name}
                  </Link>
                  <span>
                    {row.status ? <TaskStatusBadge status={row.status} /> : <Badge tone="neutral">{row.kind}</Badge>}{' '}
                    {row.pastDue && <span className="text-danger">past due</span>}{' '}
                    {row.dueDate && <span className="text-faint">due {formatDate(row.dueDate)}</span>}
                  </span>
                </li>
              ))}
            </ul>
          )}
        </section>

        <section className="card" aria-labelledby="ov-team">
          <div className="card-title-row">
            <h2 id="ov-team">Team</h2>
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
                  <span className="mono">{m.userId.slice(0, 8)}…</span>
                  <Badge tone={m.role === 'OWNER' ? 'primary' : 'neutral'}>{m.role}</Badge>
                </li>
              ))}
              {members.data.length > 6 && (
                <li className="text-faint">+{members.data.length - 6} more</li>
              )}
            </ul>
          )}
        </section>
      </div>

      <div className="overview-footer">
        {can('EDIT_PROJECT') && (
          <button className="btn btn-ghost btn-sm" onClick={() => setAiOpen(true)}>
            <Icon name="ai" size={15} /> Analyze project with AI
          </button>
        )}
        <Link className="btn btn-ghost btn-sm" to="../more" relative="path">
          More tools <Icon name="chevronRight" size={14} />
        </Link>
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
      {aiOpen && (
        <RequestRecommendationDialog
          projectId={project.id}
          initialType="PROJECT_SUMMARY"
          onClose={() => setAiOpen(false)}
          onRequested={() => navigate(`/projects/${project.id}/ai`)}
        />
      )}
    </div>
  )
}

function HealthBadge({ health }: { health: ProjectHealth }) {
  // Same severity mapping as the dashboard's healthLevel(): blocked is red everywhere.
  const critical = health.overdueItemCount + health.blockedTaskCount + health.brokenDependencyCount
  const warning = health.unresolvedRiskCount + health.unresolvedIssueCount
  if (critical > 0) return <Badge tone="danger">Needs attention</Badge>
  if (warning > 0) return <Badge tone="warning">Watch</Badge>
  return <Badge tone="success">On track</Badge>
}

function AttentionList({ health, projectId }: { health: ProjectHealth; projectId: string }) {
  const rows: { label: string; value: number; tone: 'danger' | 'warning'; to: string }[] = [
    { label: 'Overdue items', value: health.overdueItemCount, tone: 'danger', to: 'delayed' },
    { label: 'Tasks blocked by a dependency', value: health.blockedTaskCount, tone: 'danger', to: 'dependencies' },
    { label: 'Open risks', value: health.unresolvedRiskCount, tone: 'warning', to: 'risks' },
    { label: 'Open issues', value: health.unresolvedIssueCount, tone: 'warning', to: 'issues' },
    { label: 'Broken task relationships', value: health.brokenDependencyCount, tone: 'danger', to: 'dependencies' },
  ]
  const active = rows.filter((r) => r.value > 0)
  if (active.length === 0) {
    return (
      <p className="text-muted">
        <Icon name="health" size={14} /> Everything looks healthy.
      </p>
    )
  }
  return (
    <ul className="list-plain">
      {active.map((r) => (
        <li key={r.label} className="work-item">
          <Link to={`/projects/${projectId}/${r.to}`}>{r.label}</Link>
          <Badge tone={r.tone}>{r.value}</Badge>
        </li>
      ))}
    </ul>
  )
}

interface AttentionRow {
  kind: 'Task' | 'Milestone' | 'Phase'
  id: string
  name: string
  /** Stored task status (tasks only). Never rewritten — a Blocked task stays Blocked. */
  status: Task['status'] | null
  dueDate: string | null
  pastDue: boolean
  to: string
}

/**
 * One list from two real sources: tasks whose stored status is OVERDUE/BLOCKED, and the delayed
 * items the backend derives (due date passed, not completed). A task in both appears once.
 */
export function mergeAttention(tasks: Task[], delayed: DelayedItem[]): AttentionRow[] {
  const rows: AttentionRow[] = tasks.map((t) => ({
    kind: 'Task',
    id: t.id,
    name: t.name,
    status: t.status,
    dueDate: t.dueDate,
    pastDue: delayed.some((d) => d.entityType === 'TASK' && d.id === t.id),
    to: `../tasks/${t.id}`,
  }))
  const seen = new Set(rows.map((r) => r.id))
  for (const d of delayed) {
    if (seen.has(d.id)) continue
    const kind: AttentionRow['kind'] = d.entityType === 'MILESTONE' ? 'Milestone' : d.entityType === 'PHASE' ? 'Phase' : 'Task'
    rows.push({
      kind,
      id: d.id,
      name: d.name,
      status: kind === 'Task' ? 'TODO' : null,
      dueDate: d.dueDate,
      pastDue: true,
      to: kind === 'Task' ? `../tasks/${d.id}` : kind === 'Milestone' ? '../milestones' : '../phases',
    })
  }
  return rows
}
