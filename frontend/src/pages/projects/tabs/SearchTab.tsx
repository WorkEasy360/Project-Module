import { useState } from 'react'
import { useProjectWorkspace } from '../../../context/ProjectWorkspaceContext'
import { useAsyncData } from '../../../hooks/useAsyncData'
import { searchApi } from '../../../api/search'
import { DataTable, type Column } from '../../../components/common/DataTable'
import { Pagination } from '../../../components/common/Pagination'
import { Badge } from '../../../components/common/Badge'
import { formatDate } from '../../../utils/format'
import type { SearchResult } from '../../../types/work'

export function SearchTab() {
  const { project } = useProjectWorkspace()
  const [query, setQuery] = useState('')
  const [submittedQuery, setSubmittedQuery] = useState('')
  const [page, setPage] = useState(0)

  const { data, loading, error, reload } = useAsyncData(
    () => (submittedQuery ? searchApi.search(project.id, submittedQuery, page, 20) : Promise.resolve(null)),
    [project.id, submittedQuery, page],
  )

  function handleSubmit(event: React.FormEvent) {
    event.preventDefault()
    setPage(0)
    setSubmittedQuery(query.trim())
  }

  const columns: Column<SearchResult>[] = [
    { key: 'type', header: 'Type', render: (r) => <Badge tone="info">{r.resourceType}</Badge> },
    { key: 'name', header: 'Name', render: (r) => r.name },
    { key: 'description', header: 'Description', render: (r) => r.description ?? '—' },
    { key: 'updated', header: 'Updated', render: (r) => formatDate(r.updatedAt) },
  ]

  return (
    <div>
      <form onSubmit={handleSubmit} className="toolbar">
        <input
          type="search"
          placeholder="Search tasks, risks, issues, decisions…"
          value={query}
          onChange={(e) => setQuery(e.target.value)}
          style={{ maxWidth: 360 }}
          aria-label="Search this project"
        />
        <button type="submit" className="btn btn-primary" disabled={!query.trim()}>
          Search
        </button>
      </form>

      {!submittedQuery && <p className="text-muted">Enter a search term above to search this project.</p>}

      {submittedQuery && (
        <>
          <DataTable
            items={data?.content ?? null}
            loading={loading}
            error={error}
            onRetry={reload}
            columns={columns}
            getRowKey={(r) => `${r.resourceType}-${r.id}`}
            emptyTitle={`No results for "${submittedQuery}".`}
          />
          {data && (
            <Pagination page={data.page} totalPages={data.totalPages} totalElements={data.totalElements} onChange={setPage} />
          )}
        </>
      )}
    </div>
  )
}
