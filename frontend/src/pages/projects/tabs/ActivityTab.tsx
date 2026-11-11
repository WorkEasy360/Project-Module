import { useState } from 'react'
import { activityApi } from '../../../api/collaboration'
import { EmptyState } from '../../../components/common/EmptyState'
import { ErrorState } from '../../../components/common/ErrorState'
import { LoadingState } from '../../../components/common/LoadingState'
import { Pagination } from '../../../components/common/Pagination'
import { usePeople } from '../../../context/PeopleContext'
import { useProjectWorkspace } from '../../../context/ProjectWorkspaceContext'
import { useAsyncData } from '../../../hooks/useAsyncData'
import { formatDateTime, initials } from '../../../utils/format'

/** Who did what on this project, newest first. */
export function ActivityTab() {
  const { project } = useProjectWorkspace()
  const { nameOf } = usePeople()
  const [page, setPage] = useState(0)
  const { data, loading, error, reload } = useAsyncData(() => activityApi.list(project.id, page), [project.id, page])

  if (loading && !data) return <LoadingState label="Loading activity…" />
  if (error) return <ErrorState message={error} onRetry={reload} />
  if (!data || data.content.length === 0) {
    return <EmptyState title="No activity yet" description="Changes to this project will appear here." />
  }

  return (
    <div className="card">
      <ul className="activity-list">
        {data.content.map((entry) => {
          const who = entry.actorId ? nameOf(entry.actorId) : 'System'
          return (
            <li key={entry.id} className="activity-row">
              <span className="task-avatar" aria-hidden="true">
                {initials(who)}
              </span>
              <div>
                <p>
                  <strong>{who}</strong> <span className="activity-summary">{entry.summary}</span>
                </p>
                <span className="text-faint">{formatDateTime(entry.occurredAt)}</span>
              </div>
            </li>
          )
        })}
      </ul>
      <Pagination page={data.page} totalPages={data.totalPages} totalElements={data.totalElements} onChange={setPage} />
    </div>
  )
}
