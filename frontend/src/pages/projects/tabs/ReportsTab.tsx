import { useProjectWorkspace } from '../../../context/ProjectWorkspaceContext'
import { useAsyncData } from '../../../hooks/useAsyncData'
import { reportsApi } from '../../../api/reports'
import { LoadingState } from '../../../components/common/LoadingState'
import { ErrorState } from '../../../components/common/ErrorState'
import { humanizeToken } from '../../../utils/format'

export function ReportsTab() {
  const { project } = useProjectWorkspace()
  const { data, loading, error, reload } = useAsyncData(() => reportsApi.summary(project.id), [project.id])

  if (loading) return <LoadingState label="Loading report…" />
  if (error) return <ErrorState message={error} onRetry={reload} />
  if (!data) return null

  return (
    <div>
      <div className="stat-grid">
        <div className="stat-card">
          <div className="stat-value">{data.decisionCount}</div>
          <div className="stat-label">Decisions recorded</div>
        </div>
        <div className="stat-card">
          <div className={`stat-value${data.delayedItemCount > 0 ? ' danger' : ''}`}>{data.delayedItemCount}</div>
          <div className="stat-label">Delayed items</div>
        </div>
        <div className="stat-card">
          <div className={`stat-value${data.brokenDependencyCount > 0 ? ' danger' : ''}`}>
            {data.brokenDependencyCount}
          </div>
          <div className="stat-label">Broken dependencies</div>
        </div>
      </div>

      <div className="card">
        <h3>Tasks by status</h3>
        <div className="stat-grid">
          {Object.entries(data.taskCountByStatus).map(([key, count]) => (
            <div className="stat-card" key={key}>
              <div className="stat-value">{count}</div>
              <div className="stat-label">{humanizeToken(key)}</div>
            </div>
          ))}
        </div>
      </div>

      <div className="card">
        <h3>Risks by status</h3>
        <div className="stat-grid">
          {Object.entries(data.riskCountByStatus).map(([key, count]) => (
            <div className="stat-card" key={key}>
              <div className="stat-value">{count}</div>
              <div className="stat-label">{humanizeToken(key)}</div>
            </div>
          ))}
        </div>
      </div>

      <div className="card">
        <h3>Risks by priority</h3>
        <div className="stat-grid">
          {Object.entries(data.riskCountByPriority).map(([key, count]) => (
            <div className="stat-card" key={key}>
              <div className="stat-value">{count}</div>
              <div className="stat-label">{humanizeToken(key)}</div>
            </div>
          ))}
        </div>
      </div>

      <div className="card">
        <h3>Issues by priority</h3>
        <div className="stat-grid">
          {Object.entries(data.issueCountByPriority).map(([key, count]) => (
            <div className="stat-card" key={key}>
              <div className="stat-value">{count}</div>
              <div className="stat-label">{humanizeToken(key)}</div>
            </div>
          ))}
        </div>
      </div>
    </div>
  )
}
