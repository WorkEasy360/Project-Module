import { Link, useSearchParams } from 'react-router-dom'
import '../../styles/workspace.css'
import { globalSearchApi } from '../../api/collaboration'
import { EmptyState } from '../../components/common/EmptyState'
import { ErrorState } from '../../components/common/ErrorState'
import { Icon } from '../../components/common/Icon'
import { LoadingState } from '../../components/common/LoadingState'
import { PageHeader } from '../../components/common/PageHeader'
import { useAsyncData } from '../../hooks/useAsyncData'

/** Projects and tasks matching the header search, from the projects the person belongs to. */
export function SearchResultsPage() {
  const [params] = useSearchParams()
  const query = (params.get('q') ?? '').trim()
  const { data, loading, error, reload } = useAsyncData(
    () => (query.length >= 2 ? globalSearchApi.search(query) : Promise.resolve([])),
    [query],
  )

  return (
    <div className="ws-page rise-in">
      <PageHeader title="Search" description={query ? `Results for "${query}"` : 'Search your projects and tasks.'} />
      {query.length < 2 && <EmptyState title="Type at least 2 characters in the search box above." />}
      {loading && <LoadingState label="Searching…" />}
      {error && <ErrorState message={error} onRetry={reload} />}
      {query.length >= 2 && data && data.length === 0 && (
        <EmptyState title="Nothing found" description="Try a different word. Only your own projects are searched." />
      )}
      {data && data.length > 0 && (
        <ul className="ws-directory ws-results">
          {data.map((hit) => (
            <li key={`${hit.type}-${hit.id}`} className="ws-directory-row">
              <span className="ws-avatar sm" aria-hidden="true">
                <Icon name={hit.type === 'PROJECT' ? 'projects' : 'tasks'} size={14} />
              </span>
              <span className="ws-directory-name">
                <Link to={hit.type === 'PROJECT' ? `/projects/${hit.id}` : `/projects/${hit.projectId}/tasks/${hit.id}`}>
                  {hit.name}
                </Link>
                <span className="text-muted">{hit.type === 'PROJECT' ? 'Project' : `Task in ${hit.projectName}`}</span>
              </span>
            </li>
          ))}
        </ul>
      )}
    </div>
  )
}
