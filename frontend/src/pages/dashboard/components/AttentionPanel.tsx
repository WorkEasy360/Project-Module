import { useMemo, useState } from 'react'
import { Link } from 'react-router-dom'
import { Icon } from '../../../components/common/Icon'
import { IconButton } from '../../../components/common/IconButton'
import { TaskStatusBadge } from '../../../components/common/Badge'
import { Skeleton } from '../../../components/common/Skeleton'
import { formatDate } from '../../../utils/format'
import {
  ATTENTION_SORTS,
  EMPTY_ATTENTION_FILTERS,
  filterAttention,
  initialOf,
  type AttentionSort,
  type AttentionTask,
  type LoadedProject,
} from '../dashboardUtils'

/**
 * "Needs Attention": overdue and blocked tasks across the loaded projects, with working search,
 * status/project/assignee filters and sorting — all client-side over real task lists. Rows open
 * the task's page. No checkboxes/kebab: no cross-project bulk action exists on this backend.
 */
export function AttentionPanel({
  items,
  attention,
  loading,
}: {
  items: LoadedProject[]
  attention: AttentionTask[]
  loading: boolean
}) {
  const [filters, setFilters] = useState(EMPTY_ATTENTION_FILTERS)
  const [sort, setSort] = useState<AttentionSort>('dueDate,ASC')

  const projectOptions = items.map((i) => ({ id: i.project.id, name: i.project.name }))
  const assigneeOptions = useMemo(
    () => [...new Set(attention.map((a) => a.task.assigneeId).filter((x): x is string => Boolean(x)))],
    [attention],
  )
  const visible = useMemo(() => filterAttention(attention, filters, sort), [attention, filters, sort])
  const filtersActive = filters.query.trim() || filters.reason || filters.projectId || filters.assigneeId

  return (
    <section className="hb-panel hb-attention" aria-labelledby="hb-attention-title">
      <header className="hb-panel-head">
        <span className="hb-panel-icon tone-danger">
          <Icon name="warning" size={20} />
        </span>
        <div className="hb-panel-titles">
          <h2 id="hb-attention-title">
            Needs Attention{' '}
            {!loading && (
              <span className="hb-count-badge" aria-label={`${attention.length} items`}>
                {attention.length} {attention.length === 1 ? 'item' : 'items'}
              </span>
            )}
          </h2>
          <p>Overdue and blocked tasks across your loaded projects</p>
        </div>
        <Link to="/tasks" className="hb-link-all">
          View All <Icon name="arrowRight" size={14} />
        </Link>
      </header>

      <div className="hb-filters" role="search" aria-label="Filter attention items">
        <div className="hb-search">
          <Icon name="search" size={15} className="hb-search-icon" />
          <input
            type="search"
            aria-label="Search tasks needing attention"
            placeholder="Search…"
            value={filters.query}
            onChange={(e) => setFilters((f) => ({ ...f, query: e.target.value }))}
          />
          {filters.query && (
            <IconButton icon="close" label="Clear task search" size={13} className="hb-search-clear" onClick={() => setFilters((f) => ({ ...f, query: '' }))} />
          )}
        </div>
        <select aria-label="Status" value={filters.reason} onChange={(e) => setFilters((f) => ({ ...f, reason: e.target.value as AttentionTask['reason'] | '' }))}>
          <option value="">Status</option>
          <option value="OVERDUE">Overdue</option>
          <option value="BLOCKED">Blocked</option>
        </select>
        <select aria-label="Project" value={filters.projectId} onChange={(e) => setFilters((f) => ({ ...f, projectId: e.target.value }))}>
          <option value="">Project</option>
          {projectOptions.map((p) => (
            <option key={p.id} value={p.id}>
              {p.name}
            </option>
          ))}
        </select>
        <select aria-label="Assignee" value={filters.assigneeId} onChange={(e) => setFilters((f) => ({ ...f, assigneeId: e.target.value }))}>
          <option value="">Assignee</option>
          {assigneeOptions.map((id) => (
            <option key={id} value={id}>
              {id.slice(0, 8)}…
            </option>
          ))}
        </select>
        <select aria-label="Sort attention items" value={sort} onChange={(e) => setSort(e.target.value as AttentionSort)}>
          {ATTENTION_SORTS.map((s) => (
            <option key={s.value} value={s.value}>
              Sort by: {s.label}
            </option>
          ))}
        </select>
      </div>

      {loading && (
        <ul className="hb-task-list" aria-hidden="true">
          {Array.from({ length: 3 }).map((_, i) => (
            <li key={i} className="hb-task-row">
              <Skeleton width={40} height={40} />
              <Skeleton width="40%" height={14} />
              <Skeleton width={70} height={20} />
            </li>
          ))}
        </ul>
      )}

      {!loading && visible.length === 0 && (
        <div className="hb-empty" role="status">
          <span className="hb-empty-icon tone-success">
            <Icon name="health" size={18} />
          </span>
          {attention.length === 0 ? (
            <p>Nothing is overdue or blocked across your loaded projects.</p>
          ) : (
            <>
              <p>No items match these filters.</p>
              {filtersActive && (
                <button type="button" className="btn btn-sm" onClick={() => setFilters(EMPTY_ATTENTION_FILTERS)}>
                  Reset filters
                </button>
              )}
            </>
          )}
        </div>
      )}

      {!loading && visible.length > 0 && (
        <ul className="hb-task-list">
          {visible.map(({ task, projectName, reason, pastDue }) => (
            <li key={task.id} className="hb-task-row">
              <span className="hb-task-icon tone-danger" aria-hidden="true">
                <Icon name={reason === 'OVERDUE' ? 'delayed' : 'dependency'} size={16} />
              </span>
              <div className="hb-task-main">
                <Link to={`/projects/${task.projectId}/tasks/${task.id}`} className="hb-task-title">
                  {task.name}
                </Link>
                <span className="hb-task-project">{projectName}</span>
              </div>
              <span className="hb-task-status">
                <TaskStatusBadge status={task.status} />
                {pastDue && task.status !== 'OVERDUE' && (
                  <span className="hb-pastdue" title="The due date has passed">
                    past due
                  </span>
                )}
              </span>
              <span className="hb-task-date">
                <Icon name="calendar" size={14} /> {task.dueDate ? formatDate(task.dueDate) : 'No due date'}
              </span>
              <span className="hb-avatar" title={task.assigneeId ? `Assignee ${task.assigneeId}` : 'Unassigned'} aria-label={task.assigneeId ? `Assignee ${task.assigneeId.slice(0, 8)}` : 'Unassigned'}>
                {initialOf(task.assigneeId)}
              </span>
              <Link to={`/projects/${task.projectId}/tasks/${task.id}`} className="icon-btn hb-task-open" aria-label={`Open ${task.name}`} title="Open task">
                <Icon name="chevronRight" size={16} />
              </Link>
            </li>
          ))}
        </ul>
      )}
    </section>
  )
}
