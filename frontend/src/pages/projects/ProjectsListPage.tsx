import { useMemo, useState } from 'react'
import { Link, useSearchParams } from 'react-router-dom'
import '../../styles/project.css'
import { useAsyncData } from '../../hooks/useAsyncData'
import { projectsApi } from '../../api/projects'
import { DataTable, type Column } from '../../components/common/DataTable'
import { Pagination } from '../../components/common/Pagination'
import { PageHeader } from '../../components/common/PageHeader'
import { TableSkeleton } from '../../components/common/Skeleton'
import { EmptyState } from '../../components/common/EmptyState'
import { ErrorState } from '../../components/common/ErrorState'
import { Icon } from '../../components/common/Icon'
import { IconButton } from '../../components/common/IconButton'
import { ProjectStatusBadge, PriorityBadge } from '../../components/common/Badge'
import { formatDate } from '../../utils/format'
import type { ProjectSummary } from '../../types/project'
import { CreateProjectDialog } from './CreateProjectDialog'
import {
  filterProjectsByName,
  readStoredProjectsView,
  storeProjectsView,
  type ProjectsView,
} from './projectsListUtils'

const SORT_OPTIONS = [
  { value: 'createdAt,DESC', label: 'Newest first' },
  { value: 'createdAt,ASC', label: 'Oldest first' },
  { value: 'name,ASC', label: 'Name (A-Z)' },
  { value: 'priority,DESC', label: 'Priority (high first)' },
]

export function ProjectsListPage() {
  const [page, setPage] = useState(0)
  const [sort, setSort] = useState(SORT_OPTIONS[0].value)
  const [createOpen, setCreateOpen] = useState(false)
  const [view, setView] = useState<ProjectsView>(readStoredProjectsView)
  const [searchParams, setSearchParams] = useSearchParams()
  const query = searchParams.get('q') ?? ''

  const { data, loading, error, reload } = useAsyncData(
    () => projectsApi.list(page, 20, sort),
    [page, sort],
  )

  const visible = useMemo(() => filterProjectsByName(data?.content ?? [], query), [data, query])
  const filtering = query.trim() !== ''

  function changeView(next: ProjectsView) {
    setView(next)
    storeProjectsView(next)
  }

  function clearQuery() {
    setSearchParams(
      (prev) => {
        const next = new URLSearchParams(prev)
        next.delete('q')
        return next
      },
      { replace: true },
    )
  }

  const columns: Column<ProjectSummary>[] = [
    {
      key: 'name',
      header: 'Name',
      render: (p) => (
        <Link to={`/projects/${p.id}`} className="table-link-primary">
          {p.name}
        </Link>
      ),
    },
    { key: 'status', header: 'Status', render: (p) => <ProjectStatusBadge status={p.status} /> },
    { key: 'priority', header: 'Priority', render: (p) => <PriorityBadge priority={p.priority} /> },
    { key: 'updatedAt', header: 'Updated', render: (p) => formatDate(p.updatedAt) },
  ]

  const newProjectButton = (
    <button className="btn btn-primary" onClick={() => setCreateOpen(true)}>
      New Project
    </button>
  )

  return (
    <div className="rise-in">
      <PageHeader
        title="Projects"
        description="All active projects in your organization."
        actions={
          <button className="btn btn-primary" onClick={() => setCreateOpen(true)}>
            <Icon name="plus" size={15} /> New Project
          </button>
        }
      />

      <div className="card">
        <div className="toolbar pj-toolbar">
          <div className="form-field" style={{ minWidth: 200 }}>
            <label htmlFor="sort">Sort by</label>
            <select
              id="sort"
              value={sort}
              onChange={(e) => {
                setSort(e.target.value)
                setPage(0)
              }}
            >
              {SORT_OPTIONS.map((opt) => (
                <option key={opt.value} value={opt.value}>
                  {opt.label}
                </option>
              ))}
            </select>
          </div>
          <div className="spacer" />
          <div className="pj-view" role="group" aria-label="Layout">
            <IconButton icon="dashboard" label="Card view" active={view === 'card'} onClick={() => changeView('card')} />
            <IconButton icon="list" label="List view" active={view === 'list'} onClick={() => changeView('list')} />
          </div>
        </div>

        {filtering && (
          <div className="pj-chip-row">
            <span className="pj-chip">
              Filtered by “{query}”
              <button type="button" onClick={clearQuery} aria-label="Clear filter" title="Clear filter">
                <Icon name="close" size={12} />
              </button>
            </span>
            <span>
              Filtering applies to the {data ? data.content.length : ''} project{data?.content.length === 1 ? '' : 's'} loaded on this page
              {data && data.totalPages > 1 ? ' — other pages are not searched' : ''}.
            </span>
          </div>
        )}

        {loading ? (
          <TableSkeleton rows={6} columns={4} />
        ) : error ? (
          <ErrorState message={error} onRetry={reload} />
        ) : filtering && visible.length === 0 && (data?.content.length ?? 0) > 0 ? (
          <EmptyState
            title={`No projects on this page match “${query}”.`}
            description="Clear the filter or move to another page."
            action={
              <button className="btn" onClick={clearQuery}>
                Clear filter
              </button>
            }
          />
        ) : view === 'card' ? (
          visible.length === 0 ? (
            <EmptyState
              title="No projects found."
              description="Create your first project to get started."
              action={newProjectButton}
            />
          ) : (
            <ul className="pj-card-grid" aria-label="Projects">
              {visible.map((p) => (
                <li key={p.id} className="pj-card">
                  <div className="pj-card-head">
                    <span className="pj-tile" aria-hidden="true">
                      {p.name.charAt(0).toUpperCase()}
                    </span>
                    <Link to={`/projects/${p.id}`} className="pj-card-title" title={p.name}>
                      {p.name}
                    </Link>
                  </div>
                  <div className="pj-card-badges">
                    <ProjectStatusBadge status={p.status} />
                    <PriorityBadge priority={p.priority} />
                  </div>
                  <div className="pj-card-meta">
                    <span className="pj-meta-item" title={`Owner ${p.ownerId}`}>
                      <Icon name="user" size={13} />
                      <span className="mono">{p.ownerId.slice(0, 8)}…</span>
                    </span>
                    <span className="pj-meta-item" title="Last updated">
                      <Icon name="clock" size={13} />
                      {formatDate(p.updatedAt)}
                    </span>
                  </div>
                </li>
              ))}
            </ul>
          )
        ) : (
          <div className="pj-table">
            <DataTable
              items={visible}
              loading={false}
              error={null}
              onRetry={reload}
              columns={columns}
              getRowKey={(p) => p.id}
              emptyTitle="No projects found."
              emptyDescription="Create your first project to get started."
              emptyAction={newProjectButton}
            />
          </div>
        )}

        {data && (
          <Pagination
            page={data.page}
            totalPages={data.totalPages}
            totalElements={data.totalElements}
            onChange={setPage}
          />
        )}
      </div>

      {createOpen && (
        <CreateProjectDialog
          onClose={() => setCreateOpen(false)}
          onCreated={() => {
            setCreateOpen(false)
            reload()
          }}
        />
      )}
    </div>
  )
}
