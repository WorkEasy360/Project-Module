import { useProjectWorkspace } from '../../../context/ProjectWorkspaceContext'
import { useAsyncData } from '../../../hooks/useAsyncData'
import { healthApi } from '../../../api/health'
import { LoadingState } from '../../../components/common/LoadingState'
import { ErrorState } from '../../../components/common/ErrorState'

const INDICATORS: { key: keyof import('../../../types/work').ProjectHealth; label: string; tone: 'danger' | 'warning' }[] = [
  { key: 'overdueItemCount', label: 'Overdue items (tasks, milestones, phases past due)', tone: 'danger' },
  { key: 'blockedTaskCount', label: 'Blocked tasks', tone: 'warning' },
  { key: 'unresolvedRiskCount', label: 'Unresolved risks', tone: 'warning' },
  { key: 'unresolvedIssueCount', label: 'Unresolved issues', tone: 'warning' },
  { key: 'brokenDependencyCount', label: 'Broken dependencies', tone: 'danger' },
]

export function HealthTab() {
  const { project } = useProjectWorkspace()
  const { data, loading, error, reload } = useAsyncData(() => healthApi.get(project.id), [project.id])

  if (loading) return <LoadingState label="Loading health…" />
  if (error) return <ErrorState message={error} onRetry={reload} />
  if (!data) return null

  const allClear = INDICATORS.every((i) => data[i.key] === 0)

  return (
    <div>
      <p className="text-muted">
        These are the deterministic indicators the backend computes directly from project data —
        there is no weighted composite score or AI-generated prediction here.
      </p>
      {allClear && <div className="banner banner-success">All indicators are clear.</div>}
      <div className="stat-grid">
        {INDICATORS.map((indicator) => {
          const value = data[indicator.key]
          return (
            <div className="stat-card" key={indicator.key}>
              <div className={`stat-value${value > 0 ? ` ${indicator.tone}` : ''}`}>{value}</div>
              <div className="stat-label">{indicator.label}</div>
            </div>
          )
        })}
      </div>
    </div>
  )
}
