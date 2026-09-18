import { useState } from 'react'
import { Link } from 'react-router-dom'
import { useAsyncData } from '../../hooks/useAsyncData'
import { projectsApi } from '../../api/projects'
import { DataTable, type Column } from '../../components/common/DataTable'
import { Pagination } from '../../components/common/Pagination'
import { PageHeader } from '../../components/common/PageHeader'
import { TableSkeleton } from '../../components/common/Skeleton'
import { Icon } from '../../components/common/Icon'
import { ProjectStatusBadge, PriorityBadge } from '../../components/common/Badge'
import { formatDate } from '../../utils/format'
import type { ProjectSummary } from '../../types/project'
import { CreateProjectDialog } from './CreateProjectDialog'

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

  const { data, loading, error, reload } = useAsyncData(
    () => projectsApi.list(page, 20, sort),
    [page, sort],
  )

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

  return (
    <div>
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
        <div className="toolbar">
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
        </div>

        {loading ? (
          <TableSkeleton rows={6} columns={4} />
        ) : (
          <DataTable
            items={data?.content ?? null}
            loading={false}
            error={error}
            onRetry={reload}
            columns={columns}
            getRowKey={(p) => p.id}
            emptyTitle="No projects found."
            emptyDescription="Create your first project to get started."
            emptyAction={
              <button className="btn btn-primary" onClick={() => setCreateOpen(true)}>
                New Project
              </button>
            }
          />
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
