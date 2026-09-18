import { useProjectWorkspace } from '../../../context/ProjectWorkspaceContext'
import { useAsyncData } from '../../../hooks/useAsyncData'
import { timelineApi } from '../../../api/timeline'
import { LoadingState } from '../../../components/common/LoadingState'
import { ErrorState } from '../../../components/common/ErrorState'
import { EmptyState } from '../../../components/common/EmptyState'
import { MilestoneStatusBadge, TaskStatusBadge } from '../../../components/common/Badge'
import { formatDate } from '../../../utils/format'

export function TimelineTab() {
  const { project } = useProjectWorkspace()
  const { data, loading, error, reload } = useAsyncData(() => timelineApi.get(project.id), [project.id])

  if (loading) return <LoadingState label="Loading timeline…" />
  if (error) return <ErrorState message={error} onRetry={reload} />
  if (!data) return null
  if (data.phases.length === 0 && data.tasks.length === 0) {
    return <EmptyState title="No timeline data yet." description="Add phases, milestones, or tasks with dates." />
  }

  return (
    <div>
      {data.phases.map((phase) => (
        <div className="card" key={phase.id}>
          <div className="card-title-row">
            <h3>{phase.name}</h3>
            <span className="text-muted">
              {formatDate(phase.startDate)} – {formatDate(phase.endDate)}
            </span>
          </div>
          {phase.milestones.length === 0 ? (
            <p className="text-muted">No milestones in this phase.</p>
          ) : (
            <ul className="list-plain">
              {phase.milestones.map((m) => (
                <li key={m.id}>
                  {m.name} — {formatDate(m.dueDate)} <MilestoneStatusBadge status={m.status} />
                </li>
              ))}
            </ul>
          )}
        </div>
      ))}

      <div className="card">
        <h3>Tasks</h3>
        {data.tasks.length === 0 ? (
          <p className="text-muted">No tasks with due dates.</p>
        ) : (
          <ul className="list-plain">
            {data.tasks.map((t) => (
              <li key={t.id}>
                {t.name} — {formatDate(t.dueDate)} <TaskStatusBadge status={t.status} />
              </li>
            ))}
          </ul>
        )}
      </div>
    </div>
  )
}
