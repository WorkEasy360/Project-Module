import { useMemo } from 'react'
import { Link } from 'react-router-dom'
import { Icon } from '../../../components/common/Icon'
import { Skeleton } from '../../../components/common/Skeleton'
import { Badge } from '../../../components/common/Badge'
import { useAsyncData } from '../../../hooks/useAsyncData'
import { aiApi } from '../../../api/ai'
import { ApiError } from '../../../api/client'
import { RECOMMENDATION_TYPE_LABELS } from '../../../types/ai'
import { pendingRecommendations, type LoadedProject, type ProjectRecommendations } from '../dashboardUtils'

/**
 * Latest pending AI recommendations across the loaded projects, from the real per-project
 * /ai/recommendations list. A 503 means no AI provider is configured on the backend — we say so
 * instead of inventing suggestions, and there is no chat endpoint, so no "ask me anything" box.
 */
export function AIRecommendationsCard({ projects, projectsLoading }: { projects: LoadedProject[]; projectsLoading: boolean }) {
  const ids = projects.map((p) => p.project.id).join(',')
  const results = useAsyncData(
    (): Promise<ProjectRecommendations[]> =>
      Promise.all(
        projects.map(async ({ project }) => {
          try {
            const recommendations = await aiApi.list(project.id)
            return { projectId: project.id, projectName: project.name, recommendations, unavailable: false }
          } catch (err) {
            const unavailable = err instanceof ApiError && err.isIntegrationUnavailable
            return { projectId: project.id, projectName: project.name, recommendations: null, unavailable }
          }
        }),
      ),
    [ids],
  )
  const loading = projectsLoading || results.loading
  const data = useMemo(() => results.data ?? [], [results.data])
  const pending = useMemo(() => pendingRecommendations(data), [data])
  const unavailable = data.length > 0 && data.some((r) => r.unavailable) && data.every((r) => r.recommendations === null || r.unavailable)
  const failed = data.filter((r) => r.recommendations === null && !r.unavailable).length

  return (
    <section className="hb-panel hb-widget hb-ai" aria-labelledby="hb-ai-title">
      <header className="hb-panel-head compact">
        <span className="hb-panel-icon tone-brand">
          <Icon name="sparkles" size={18} />
        </span>
        <div className="hb-panel-titles">
          <h2 id="hb-ai-title">AI Recommendations</h2>
          <p>Pending suggestions awaiting your review</p>
        </div>
      </header>

      {loading && <Skeleton height={64} />}

      {!loading && unavailable && (
        <div className="hb-widget-unavailable" role="status">
          <span className="hb-empty-icon tone-neutral">
            <Icon name="ai" size={18} />
          </span>
          <p>
            <strong>AI provider not configured.</strong> The backend answered that no AI service is available, so
            nothing has been generated or made up. Recommendations appear here once a provider is set up.
          </p>
        </div>
      )}

      {!loading && !unavailable && pending.length === 0 && (
        <div className="hb-widget-unavailable" role="status">
          <span className="hb-empty-icon tone-neutral">
            <Icon name="inbox" size={18} />
          </span>
          <p>
            {failed > 0 ? (
              <>
                <strong>Couldn&apos;t load recommendations</strong> for {failed} of {data.length} loaded projects.
              </>
            ) : (
              <>
                <strong>No pending recommendations.</strong> Request one from a project&apos;s AI tab — a task
                breakdown, a risk analysis or a what-if question.
              </>
            )}
          </p>
        </div>
      )}

      {!loading && pending.length > 0 && (
        <ul className="hb-ai-list">
          {pending.map(({ recommendation: r, projectId, projectName }) => (
            <li key={r.id} className="hb-ai-row">
              <span className="hb-ai-icon" aria-hidden="true">
                <Icon name="sparkles" size={15} />
              </span>
              <span className="hb-ai-body">
                <Link to={`/projects/${projectId}/ai`} className="hb-ai-name">
                  {r.title || RECOMMENDATION_TYPE_LABELS[r.type]}
                </Link>
                <span className="hb-ai-meta">
                  <Badge tone="info">{RECOMMENDATION_TYPE_LABELS[r.type]}</Badge>
                  <span>{projectName}</span>
                </span>
              </span>
              <Link to={`/projects/${projectId}/ai`} className="icon-btn hb-ai-open" aria-label={`Review ${r.title || RECOMMENDATION_TYPE_LABELS[r.type]}`} title="Review in the project's AI tab">
                <Icon name="chevronRight" size={16} />
              </Link>
            </li>
          ))}
        </ul>
      )}
    </section>
  )
}
