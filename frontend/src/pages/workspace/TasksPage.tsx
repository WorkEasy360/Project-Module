import { useMemo, useState } from 'react'
import { Link, useSearchParams } from 'react-router-dom'
import { useIdentity } from '../../context/IdentityContext'
import { usePeople } from '../../context/PeopleContext'
import { ErrorState } from '../../components/common/ErrorState'
import { Icon } from '../../components/common/Icon'
import { Avatar } from '../../components/common/Avatar'
import { PriorityBadge, TaskStatusBadge, taskStatusLabel } from '../../components/common/Badge'
import { TaskStatusControl } from '../../components/task/TaskStatusControl'
import { CountTile, FilterSelect } from '../../components/ops/OpsBits'
import { formatDate, humanizeToken, todayIso } from '../../utils/format'
import { TASK_PRIORITIES, TASK_STATUSES, type Task } from '../../types/work'
import { AddTaskButton } from './AddTaskButton'
import {
  filterTasks,
  isDueToday,
  isOpen,
  isOverdue,
  isUpcoming,
  readTaskFilters,
  useWorkData,
  type TaskFilters,
} from './workRules'

const PER_PAGE = 6

/**
 * V3 Tasks. "My Tasks" shows work assigned to the signed-in person with Today / Upcoming / Overdue
 * and Completed views; "All Tasks" shows every task the person can access, filtered by project,
 * assignee, status, priority and due date, and defaults to open work. Filters live in the address,
 * so counts elsewhere (Home) open exactly the matching list.
 */
export function TasksPage({ scope }: { scope: 'mine' | 'all' }) {
  const { identity } = useIdentity()
  const { people, nameOf } = usePeople()
  const [params, setParams] = useSearchParams()
  const [page, setPage] = useState(0)
  const today = todayIso()
  const data = useWorkData()
  const me = identity?.userId ?? null

  const filters = readTaskFilters(params)
  const projects = useMemo(() => data.data?.projects ?? [], [data.data])
  const projectName = (id: string) => projects.find((p) => p.id === id)?.name ?? 'Project'
  const base = useMemo(() => {
    const all = data.data?.tasks ?? []
    return scope === 'mine' ? all.filter((t) => me !== null && t.assigneeId === me) : all
  }, [data.data, scope, me])
  const effective: TaskFilters = scope === 'mine' ? { ...filters, assignee: '' } : filters
  const rows = filterTasks(base, effective, today, projectName)
  const pages = Math.max(1, Math.ceil(rows.length / PER_PAGE))
  const current = Math.min(page, pages - 1)
  const shown = rows.slice(current * PER_PAGE, current * PER_PAGE + PER_PAGE)
  const loading = data.loading && !data.data

  function set(patch: Partial<Record<'view' | 'project' | 'assignee' | 'status' | 'priority' | 'q', string>>) {
    const next = new URLSearchParams(params)
    for (const [k, v] of Object.entries(patch)) {
      if (v) next.set(k, v)
      else next.delete(k)
    }
    setPage(0)
    setParams(next, { replace: true })
  }
  const toggleView = (view: TaskFilters['view']) => set({ view: filters.view === view ? '' : view, status: '' })

  const counts = {
    open: base.filter(isOpen).length,
    today: base.filter((t) => isDueToday(t, today)).length,
    upcoming: base.filter((t) => isUpcoming(t, today)).length,
    overdue: base.filter((t) => isOverdue(t, today)).length,
    unassigned: base.filter((t) => isOpen(t) && !t.assigneeId).length,
    completed: base.filter((t) => !isOpen(t)).length,
  }

  if (data.error) return <ErrorState message={data.error} onRetry={data.reload} />

  return (
    <div className="ops-page ops-tasks">
      <header className="ops-page-head">
        <div>
          <h1>{scope === 'mine' ? 'My Tasks' : 'All Tasks'}</h1>
          <p>
            {scope === 'mine'
              ? 'Everything assigned to you, with what is due first.'
              : 'Every task you can access across your projects. Open work is shown first.'}
          </p>
        </div>
        <div className="ops-head-actions">
          <AddTaskButton projects={projects} onCreated={() => data.reload()} />
        </div>
      </header>

      <section className="ops-stats ops-stats-5" aria-label="Task counts">
        <CountTile icon="tasks" tone="green" label={scope === 'mine' ? 'My Open Tasks' : 'Open Tasks'} value={counts.open} loading={loading}
          active={!filters.view && !filters.status && !filters.assignee} onClick={() => set({ view: '', status: '', assignee: '' })} />
        <CountTile icon="calendar" tone="orange" label="Due Today" value={counts.today} loading={loading}
          active={filters.view === 'today'} onClick={() => toggleView('today')} />
        <CountTile icon="warning" tone="red" label="Overdue" value={counts.overdue} loading={loading}
          active={filters.view === 'overdue'} onClick={() => toggleView('overdue')} />
        {scope === 'mine' ? (
          <CountTile icon="calendar" tone="blue" label="Upcoming (7 Days)" value={counts.upcoming} loading={loading}
            active={filters.view === 'upcoming'} onClick={() => toggleView('upcoming')} />
        ) : (
          <CountTile icon="user" tone="purple" label="Unassigned" value={counts.unassigned} loading={loading}
            active={filters.assignee === 'none'} onClick={() => set({ assignee: filters.assignee === 'none' ? '' : 'none', view: '' })} />
        )}
        <CountTile icon="checkCircle" tone="blue" label="Completed" value={counts.completed} loading={loading}
          active={filters.view === 'completed'} onClick={() => toggleView('completed')} />
      </section>

      <div className={`ops-filters${scope === 'all' ? ' ops-filters-5' : ''}`} role="search" aria-label="Find tasks">
        <label className="ops-search">
          <Icon name="search" size={22} />
          <input type="search" placeholder="Search tasks or projects..." aria-label="Search tasks or projects" value={filters.query} onChange={(e) => set({ q: e.target.value })} />
        </label>
        <FilterSelect label="Project" value={filters.project} onChange={(v) => set({ project: v })}
          options={[{ value: '', label: 'All Projects' }, ...projects.map((p) => ({ value: p.id, label: p.name }))]} />
        {scope === 'all' && (
          <FilterSelect label="Assignee" value={filters.assignee} onChange={(v) => set({ assignee: v })}
            options={[{ value: '', label: 'Anyone' }, { value: 'none', label: 'Unassigned' }, ...people.map((p) => ({ value: p.id, label: p.name }))]} />
        )}
        <FilterSelect label="Status" value={filters.status} onChange={(v) => set({ status: v, view: v === 'COMPLETED' ? '' : filters.view })}
          options={[{ value: '', label: 'Open (all statuses)' }, ...TASK_STATUSES.map((s) => ({ value: s, label: taskStatusLabel(s) }))]} />
        <FilterSelect label="Priority" value={filters.priority} onChange={(v) => set({ priority: v })}
          options={[{ value: '', label: 'All Priorities' }, ...TASK_PRIORITIES.map((p) => ({ value: p, label: humanizeToken(p) }))]} />
      </div>

      <section className="ops-card ops-table-card">
        <div className="ops-table-scroll">
          <table className="ops-table">
            <thead>
              <tr>
                <th>Task</th>
                <th>Project</th>
                <th>Assignee</th>
                <th>Priority</th>
                <th>Due Date</th>
                <th>Status</th>
                <th className="ops-th-actions">Change</th>
              </tr>
            </thead>
            <tbody>
              {loading && <tr><td colSpan={7} className="ops-empty">Loading tasks…</td></tr>}
              {!loading && shown.length === 0 && (
                <tr><td colSpan={7} className="ops-empty">{base.length === 0 ? (scope === 'mine' ? 'Nothing is assigned to you yet.' : 'No tasks yet.') : 'No tasks match these filters.'}</td></tr>
              )}
              {shown.map((t) => (
                <TaskRow key={t.id} task={t} projectName={projectName(t.projectId)} assigneeName={t.assigneeId ? nameOf(t.assigneeId) : null} today={today} onChanged={data.reload} />
              ))}
            </tbody>
          </table>
        </div>
        <footer className="ops-table-foot">
          <span>
            {rows.length === 0 ? 'Showing 0 tasks' : `Showing ${current * PER_PAGE + 1} – ${Math.min(rows.length, current * PER_PAGE + PER_PAGE)} of ${rows.length} tasks`}
          </span>
          <div className="ops-pager">
            <button type="button" className="ops-page-btn" aria-label="Previous page" disabled={current === 0} onClick={() => setPage(current - 1)}>
              <Icon name="chevronLeft" size={18} />
            </button>
            <span className="ops-page-btn on" aria-current="page">{current + 1}</span>
            <button type="button" className="ops-page-btn" aria-label="Next page" disabled={current >= pages - 1} onClick={() => setPage(current + 1)}>
              <Icon name="chevronRight" size={18} />
            </button>
          </div>
        </footer>
      </section>
    </div>
  )
}

function TaskRow({ task, projectName, assigneeName, today, onChanged }: {
  task: Task
  projectName: string
  assigneeName: string | null
  today: string
  onChanged: () => void
}) {
  const overdue = isOverdue(task, today)
  return (
    <tr>
      <td>
        <Link to={`/projects/${task.projectId}/tasks/${task.id}`} className="ops-project-name">{task.name}</Link>
      </td>
      <td>
        <Link to={`/projects/${task.projectId}/overview`}>{projectName}</Link>
      </td>
      <td>
        {task.assigneeId && assigneeName ? (
          <span className="ops-owner">
            <Avatar userId={task.assigneeId} name={assigneeName} />
            {assigneeName}
          </span>
        ) : (
          <span className="text-faint">Unassigned</span>
        )}
      </td>
      <td><PriorityBadge priority={task.priority} /></td>
      <td className={overdue ? 'ops-overdue' : ''}>
        {task.dueDate ? formatDate(task.dueDate) : <span className="text-faint">No due date</span>}
        {overdue && <span className="ops-overdue-tag">Overdue</span>}
      </td>
      <td><TaskStatusBadge status={task.status} /></td>
      <td className="ops-actions">
        <TaskStatusControl task={task} onChanged={onChanged} compact />
      </td>
    </tr>
  )
}
