import { useProjectWorkspace } from '../../../context/ProjectWorkspaceContext'
import { useAsyncData } from '../../../hooks/useAsyncData'
import { dependencyAnalysisApi } from '../../../api/dependencyAnalysis'
import { LoadingState } from '../../../components/common/LoadingState'
import { ErrorState } from '../../../components/common/ErrorState'
import { Badge } from '../../../components/common/Badge'

export function DependenciesTab() {
  const { project } = useProjectWorkspace()
  const { data, loading, error, reload } = useAsyncData(
    () => dependencyAnalysisApi.get(project.id),
    [project.id],
  )

  if (loading) return <LoadingState label="Analyzing dependencies…" />
  if (error) return <ErrorState message={error} onRetry={reload} />
  if (!data) return null

  const hasIssues = data.cycles.length > 0 || data.blockedTaskIds.length > 0 || data.brokenDependencies.length > 0

  return (
    <div>
      {!hasIssues && <div className="banner banner-success">No dependency cycles, blocked tasks, or broken dependencies detected.</div>}

      <div className="card">
        <h3>Dependency cycles</h3>
        {data.cycles.length === 0 ? (
          <p className="text-muted">None detected.</p>
        ) : (
          <ul className="list-plain">
            {data.cycles.map((cycle, idx) => (
              <li key={idx}>
                <Badge tone="danger">Cycle</Badge> {cycle.join(' → ')}
              </li>
            ))}
          </ul>
        )}
      </div>

      <div className="card">
        <h3>Blocked tasks</h3>
        {data.blockedTaskIds.length === 0 ? (
          <p className="text-muted">None.</p>
        ) : (
          <ul className="list-plain">
            {data.blockedTaskIds.map((id) => (
              <li key={id}>
                <code>{id}</code>
              </li>
            ))}
          </ul>
        )}
      </div>

      <div className="card">
        <h3>Broken dependencies</h3>
        {data.brokenDependencies.length === 0 ? (
          <p className="text-muted">None.</p>
        ) : (
          <ul className="list-plain">
            {data.brokenDependencies.map((d) => (
              <li key={d.id}>
                <code>{d.dependentTaskId}</code> depends on <code>{d.prerequisiteTaskId}</code>{' '}
                <Badge tone="danger">Broken</Badge>
              </li>
            ))}
          </ul>
        )}
      </div>
    </div>
  )
}
