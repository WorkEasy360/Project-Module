import { useMemo, useState } from 'react'
import { Link } from 'react-router-dom'
import '../../styles/mytasks.css'
import { tasksApi } from '../../api/tasks'
import { useIdentity } from '../../context/IdentityContext'
import { useAllProjects, usePerProject } from '../../hooks/useWorkspaceData'
import { PageHeader } from '../../components/common/PageHeader'
import { Icon, type IconName } from '../../components/common/Icon'
import { IconButton } from '../../components/common/IconButton'
import { TaskStatusBadge } from '../../components/common/Badge'
import { TaskStatusControl } from '../../components/task/TaskStatusControl'
import { EmptyState } from '../../components/common/EmptyState'
import { ErrorState } from '../../components/common/ErrorState'
import { Skeleton } from '../../components/common/Skeleton'
import { formatDate, humanizeToken } from '../../utils/format'
import { TASK_STATUSES, type Task, type TaskStatus } from '../../types/work'
import { FailedProjectsNotice, TruncationNote } from './WorkspaceNotices'
import {
  DEFAULT_FILTERS,
  MY_TASKS_SORTS,
  applyTaskFilters,
  dueHint,
  flattenTasks,
  hasActiveFilters,
  initialOf,
  isPastDue,
  scopeTasks,
  shortId,
  showsPastDueMarker,
  summarizeTasks,
  todayIso,
  type MyTasksFilters,
  type MyTasksSort,
  type TaskFocus,
} from './myTasksUtils'

/**
 * My Tasks: one table over every loaded project's task list. The backend has no cross-project
 * task endpoint, so this loads a page of projects and calls `/projects/{id}/tasks` for each
 * (Promise.allSettled — one failing project never blanks the page). All filtering, counting and
 * sorting is client-side over those real lists. The only write is the inline status control,
 * which performs the real PATCH and then reloads.
 */
export function MyTasksPage() {
  const { identity } = useIdentity()
  const userId = identity?.userId ?? null
  const today = todayIso()

  const projects = useAllProjects()
  const projectList = projects.data?.content ?? null
  const tasks = usePerProject(projectList, (id) => tasksApi.list(id, { archived: false }))

  const [filters, setFilters] = useState<MyTasksFilters>(() => ({ ...DEFAULT_FILTERS, mineOnly: userId !== null }))
  const [sort, setSort] = useState<MyTasksSort>('dueDate,ASC')

  const all = useMemo(() => flattenTasks(projectList ?? [], tasks.byProject), [projectList, tasks.byProject])
  const scoped = useMemo(() => scopeTasks(all, filters.mineOnly, userId), [all, filters.mineOnly, userId])
  const summary = useMemo(() => summarizeTasks(scoped, userId, today), [scoped, userId, today])
  const visible = useMemo(() => applyTaskFilters(all, filters, userId, sort, today), [all, filters, userId, sort, today])
  // What the table would show with only the two toggles applied — the "of N" in the count line.
  const unfilteredCount = useMemo(
    () => applyTaskFilters(all, { ...filters, query: '', projectId: '', status: '', focus: '' }, userId, sort, today).length,
    [all, filters, userId, sort, today],
  )

  // usePerProject keys its effect on the joined project ids, which is '' for both "not loaded yet"
  // and "no projects", so with zero projects its `loading` never settles — don't wait on it then.
  const loading = projects.loading || ((projectList?.length ?? 0) > 0 && tasks.loading)
  const filtersActive = hasActiveFilters(filters)
  const mineActive = filters.mineOnly && userId !== null
  const set = (patch: Partial<MyTasksFilters>) => setFilters((f) => ({ ...f, ...patch }))
  const toggleFocus = (focus: TaskFocus) => set({ focus: filters.focus === focus ? '' : focus })
  const resetFilters = () => set({ query: '', projectId: '', status: '', focus: '' })

  if (projects.error) {
    return (
      <div className="mt">
        <PageHeader title="My Tasks" description="Tasks across your projects." />
        <ErrorState message={projects.error} onRetry={projects.reload} />
      </div>
    )
  }

  return (
    <div className="mt">
      <PageHeader
        title="My Tasks"
        description={
          mineActive
            ? 'Everything assigned to you across your projects, with what needs attention first.'
            : 'Every open task across your projects, with what needs attention first.'
        }
        actions={
          <button type="button" className="btn btn-sm" onClick={tasks.reload} disabled={loading}>
            <Icon name="refresh" size={14} /> Refresh
          </button>
        }
      />

      {/* ---------- Summary chips ---------- */}
      <div className="mt-summary rise-in" aria-label="Task summary">
        <SummaryChip
          icon="user"
          tone="info"
          label="Assigned to me"
          sub={userId ? (mineActive ? 'open tasks · showing' : 'open tasks') : 'set your identity'}
          value={summary.assignedToMe}
          loading={loading}
        />
        <SummaryChip
          icon="delayed"
          tone={summary.overdue > 0 ? 'danger' : 'neutral'}
          label="Overdue"
          sub="past due or marked overdue"
          value={summary.overdue}
          loading={loading}
          pressed={filters.focus === 'overdue'}
          onClick={() => toggleFocus('overdue')}
        />
        <SummaryChip
          icon="calendar"
          tone="warning"
          label="Due this week"
          sub="next 7 days"
          value={summary.dueThisWeek}
          loading={loading}
          pressed={filters.focus === 'week'}
          onClick={() => toggleFocus('week')}
        />
        <SummaryChip
          icon="dependency"
          tone={summary.blocked > 0 ? 'danger' : 'neutral'}
          label="Blocked"
          sub="by stored status"
          value={summary.blocked}
          loading={loading}
          pressed={filters.focus === 'blocked'}
          onClick={() => toggleFocus('blocked')}
        />
      </div>

      {/* ---------- Toolbar ---------- */}
      <div className="mt-toolbar" role="search" aria-label="Filter tasks">
        <div className="mt-search">
          <Icon name="search" size={15} className="mt-search-icon" />
          <input
            type="search"
            aria-label="Search tasks by name"
            placeholder="Search tasks…"
            value={filters.query}
            onChange={(e) => set({ query: e.target.value })}
          />
          {filters.query && (
            <IconButton icon="close" label="Clear search" size={13} className="mt-search-clear" onClick={() => set({ query: '' })} />
          )}
        </div>
        <select aria-label="Project" value={filters.projectId} onChange={(e) => set({ projectId: e.target.value })}>
          <option value="">All projects</option>
          {(projectList ?? []).map((p) => (
            <option key={p.id} value={p.id}>
              {p.name}
            </option>
          ))}
        </select>
        <select aria-label="Status" value={filters.status} onChange={(e) => set({ status: e.target.value as TaskStatus | '' })}>
          <option value="">Any status</option>
          {TASK_STATUSES.map((s) => (
            <option key={s} value={s}>
              {humanizeToken(s)}
            </option>
          ))}
        </select>
        <select aria-label="Sort tasks" value={sort} onChange={(e) => setSort(e.target.value as MyTasksSort)}>
          {MY_TASKS_SORTS.map((o) => (
            <option key={o.value} value={o.value}>
              Sort by: {o.label}
            </option>
          ))}
        </select>
        <div className="mt-toggles">
          <label className={`mt-switch${userId ? '' : ' disabled'}`} title={userId ? undefined : 'Set your identity to filter by assignee'}>
            <input
              type="checkbox"
              checked={mineActive}
              disabled={!userId}
              onChange={(e) => set({ mineOnly: e.target.checked })}
            />
            <span className="mt-switch-track" aria-hidden="true" />
            Assigned to me
          </label>
          <label className="mt-switch">
            <input type="checkbox" checked={filters.showCompleted} onChange={(e) => set({ showCompleted: e.target.checked })} />
            <span className="mt-switch-track" aria-hidden="true" />
            Show completed
          </label>
        </div>
      </div>

      {filtersActive && (
        <div className="mt-active" aria-live="polite">
          {filters.query.trim() && <span className="badge badge-primary">name contains &ldquo;{filters.query.trim()}&rdquo;</span>}
          {filters.projectId && <span className="badge badge-primary">project {projectList?.find((p) => p.id === filters.projectId)?.name ?? ''}</span>}
          {filters.status && <span className="badge badge-primary">status {humanizeToken(filters.status)}</span>}
          {filters.focus && <span className="badge badge-primary">{FOCUS_LABEL[filters.focus]}</span>}
          <button type="button" className="btn btn-sm btn-ghost" onClick={resetFilters}>
            <Icon name="close" size={13} /> Reset filters
          </button>
        </div>
      )}

      {projects.data && <TruncationNote totalElements={projects.data.totalElements} what="tasks" />}
      {!loading && projectList && <FailedProjectsNotice projects={projectList} errors={tasks.errors} what="tasks" onRetry={tasks.reload} />}

      {/* ---------- Table ---------- */}
      <section className="mt-panel" aria-label="Tasks" aria-busy={loading}>
        {loading ? (
          <TaskTableSkeleton />
        ) : projectList && projectList.length === 0 ? (
          <EmptyState
            title="No projects yet"
            description="Tasks live inside projects. Create a project to start adding tasks."
            action={
              <Link className="btn btn-primary btn-sm" to="/projects">
                <Icon name="projects" size={14} /> Go to projects
              </Link>
            }
          />
        ) : all.length === 0 ? (
          <EmptyState title="No open tasks" description="None of your loaded projects has a task yet." />
        ) : visible.length === 0 ? (
          filtersActive ? (
            <EmptyState
              title="No tasks match these filters"
              action={
                <button type="button" className="btn btn-sm" onClick={resetFilters}>
                  Reset filters
                </button>
              }
            />
          ) : mineActive ? (
            <EmptyState
              title="Nothing assigned to you"
              description="No open task across your loaded projects has your user ID as assignee."
              action={
                <button type="button" className="btn btn-sm" onClick={() => set({ mineOnly: false })}>
                  Show all tasks
                </button>
              }
            />
          ) : (
            <EmptyState
              title="All caught up"
              description="Every task across your loaded projects is completed."
              action={
                <button type="button" className="btn btn-sm" onClick={() => set({ showCompleted: true })}>
                  Show completed
                </button>
              }
            />
          )
        ) : (
          <>
            <p className="mt-count text-faint" aria-live="polite">
              {countSentence(visible.length, unfilteredCount, mineActive, projectList?.length ?? 0)}
            </p>
            <div className="mt-table-wrap">
              <table className="mt-table">
                <thead>
                  <tr>
                    <th scope="col">Task</th>
                    <th scope="col">Project</th>
                    <th scope="col">Status</th>
                    <th scope="col">Due</th>
                    <th scope="col">Assignee</th>
                    <th scope="col">
                      <span className="sr-only">Change status</span>
                    </th>
                  </tr>
                </thead>
                <tbody>
                  {visible.map(({ task, projectId, projectName }) => (
                    <TaskRow
                      key={task.id}
                      task={task}
                      projectId={projectId}
                      projectName={projectName}
                      today={today}
                      isMine={userId !== null && task.assigneeId === userId}
                      onChanged={tasks.reload}
                    />
                  ))}
                </tbody>
              </table>
            </div>
          </>
        )}
      </section>
    </div>
  )
}

function countSentence(shown: number, scoped: number, mine: boolean, projectCount: number): string {
  const tasks = `${shown} ${shown === 1 ? 'task' : 'tasks'}${shown !== scoped ? ` of ${scoped}` : ''}`
  const projects = `${projectCount} ${projectCount === 1 ? 'project' : 'projects'}`
  return `${tasks}${mine ? ' assigned to you' : ''} across ${projects}`
}

const FOCUS_LABEL: Record<Exclude<TaskFocus, ''>, string> = {
  overdue: 'overdue only',
  week: 'due this week',
  blocked: 'blocked only',
}

function TaskRow({
  task,
  projectId,
  projectName,
  today,
  isMine,
  onChanged,
}: {
  task: Task
  projectId: string
  projectName: string
  today: string
  isMine: boolean
  onChanged: () => void
}) {
  const pastDue = isPastDue(task, today)
  const hint = dueHint(task.dueDate, today)
  return (
    <tr className={`mt-row${pastDue ? ' past-due' : ''}${task.status === 'COMPLETED' ? ' done' : ''}`}>
      <td data-label="Task" className="mt-cell-task">
        <Link to={`/projects/${projectId}/tasks/${task.id}`} className="mt-task-name">
          {task.name}
        </Link>
      </td>
      <td data-label="Project" className="mt-cell-project">
        <Link to={`/projects/${projectId}/overview`} className="mt-project">
          <Icon name="projects" size={13} /> {projectName}
        </Link>
      </td>
      <td data-label="Status" className="mt-cell-status">
        <span className="status-cell">
          <TaskStatusBadge status={task.status} />
          {showsPastDueMarker(task, today) && (
            <span className="mt-pastdue" title="The due date has passed">
              past due
            </span>
          )}
        </span>
      </td>
      <td data-label="Due" className="mt-cell-due">
        {task.dueDate ? (
          <span className={`mt-due${pastDue ? ' past' : hint === 'Today' ? ' today' : ''}`}>
            <Icon name="calendar" size={13} />
            <span>{formatDate(task.dueDate)}</span>
            {task.status !== 'COMPLETED' && <span className="mt-due-hint">{hint}</span>}
          </span>
        ) : (
          <span className="text-faint">No due date</span>
        )}
      </td>
      <td data-label="Assignee" className="mt-cell-assignee">
        {task.assigneeId ? (
          <span className="mt-assignee" title={task.assigneeId}>
            <span className={`mt-avatar${isMine ? ' me' : ''}`} aria-hidden="true">
              {initialOf(task.assigneeId)}
            </span>
            <span className="mono">{isMine ? 'You' : shortId(task.assigneeId)}</span>
          </span>
        ) : (
          <span className="mt-assignee unassigned">
            <span className="mt-avatar empty" aria-hidden="true">
              –
            </span>
            <span>Unassigned</span>
          </span>
        )}
      </td>
      <td data-label="Change status" className="mt-cell-action">
        <TaskStatusControl task={task} onChanged={onChanged} compact />
      </td>
    </tr>
  )
}

function SummaryChip({
  icon,
  tone,
  label,
  sub,
  value,
  loading,
  pressed,
  onClick,
}: {
  icon: IconName
  tone: 'info' | 'danger' | 'warning' | 'success' | 'neutral'
  label: string
  sub: string
  value: number
  loading: boolean
  pressed?: boolean
  onClick?: () => void
}) {
  const body = (
    <>
      <span className="mt-chip-icon">
        <Icon name={icon} size={18} />
      </span>
      <span className="mt-chip-body">
        <span className="mt-chip-value">{loading ? <Skeleton width={28} height={22} /> : value}</span>
        <span className="mt-chip-label">{label}</span>
        <span className="mt-chip-sub">{sub}</span>
      </span>
    </>
  )
  const cls = `mt-chip tone-${tone}${pressed ? ' pressed' : ''}`
  if (!onClick) {
    return <div className={cls}>{body}</div>
  }
  return (
    <button type="button" className={cls} aria-pressed={pressed} onClick={onClick} title={pressed ? 'Clear this focus' : `Show only: ${label}`}>
      {body}
    </button>
  )
}

function TaskTableSkeleton() {
  return (
    <div className="mt-table-wrap" aria-hidden="true">
      <table className="mt-table">
        <tbody>
          {Array.from({ length: 6 }).map((_, r) => (
            <tr key={r}>
              <td>
                <Skeleton width="60%" height={14} />
              </td>
              <td>
                <Skeleton width="50%" height={12} />
              </td>
              <td>
                <Skeleton width={70} height={20} />
              </td>
              <td>
                <Skeleton width={90} height={12} />
              </td>
              <td>
                <Skeleton width={28} height={28} />
              </td>
              <td>
                <Skeleton width={96} height={26} />
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  )
}
