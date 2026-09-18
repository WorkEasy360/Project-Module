import { useState } from 'react'
import { Link } from 'react-router-dom'
import { useAsyncData } from '../../hooks/useAsyncData'
import { dashboardApi } from '../../api/dashboard'
import { projectsApi } from '../../api/projects'
import { healthApi } from '../../api/health'
import { ErrorState } from '../../components/common/ErrorState'
import { EmptyState } from '../../components/common/EmptyState'
import { PageHeader } from '../../components/common/PageHeader'
import { Skeleton, StatGridSkeleton } from '../../components/common/Skeleton'
import { Icon } from '../../components/common/Icon'
import { Badge, ProjectStatusBadge, PriorityBadge } from '../../components/common/Badge'
import { CreateProjectDialog } from '../projects/CreateProjectDialog'
import type { ProjectHealth } from '../../types/work'
import type { ProjectSummary } from '../../types/project'

interface ProjectWithHealth {
  project: ProjectSummary
  health: ProjectHealth | null
}

function attentionCount(h: ProjectHealth): number {
  return h.overdueItemCount + h.blockedTaskCount + h.unresolvedRiskCount + h.unresolvedIssueCount + h.brokenDependencyCount
}

/**
 * Home answers "what needs my attention?" first, then "what am I working on?". Everything is
 * real: the org-wide /dashboard counts, the first page of projects, and each project's /health.
 * There is no cross-project "my tasks" or due-date endpoint on this backend, so nothing of that
 * kind is shown — it would have to be fabricated.
 */
export function DashboardPage() {
  const [createOpen, setCreateOpen] = useState(false)
  const summary = useAsyncData(() => dashboardApi.get(), [])
  const projects = useAsyncData(async (): Promise<ProjectWithHealth[]> => {
    const page = await projectsApi.list(0, 10)
    return Promise.all(
      page.content.map(async (project) => ({
        project,
        health: await healthApi.get(project.id).catch(() => null),
      })),
    )
  }, [])

  if (summary.error) return <ErrorState message={summary.error} onRetry={summary.reload} />

  const isEmpty = summary.data && summary.data.activeProjectCount === 0 && summary.data.archivedProjectCount === 0
  const needsAttention = (projects.data ?? []).filter((p) => p.health && attentionCount(p.health) > 0)
  const onTrack = (projects.data ?? []).filter((p) => p.health && attentionCount(p.health) === 0)

  return (
    <div>
      <PageHeader
        title="Home"
        description="What needs attention across your projects, and where things stand."
        actions={
          <button className="btn btn-primary" onClick={() => setCreateOpen(true)}>
            <Icon name="plus" size={15} /> New Project
          </button>
        }
      />

      {summary.loading && <StatGridSkeleton count={3} />}

      {!summary.loading && isEmpty && (
        <EmptyState
          title="Welcome — let's set up your first project"
          description="A project holds your tasks, team and dates. Create one to get started; it only needs a name."
          action={
            <button className="btn btn-primary" onClick={() => setCreateOpen(true)}>
              <Icon name="plus" size={15} /> Create your first project
            </button>
          }
        />
      )}

      {!summary.loading && summary.data && !isEmpty && (
        <>
          <section aria-labelledby="home-attention" className="home-section">
            <h2 id="home-attention">Needs attention</h2>
            {projects.loading && <Skeleton height={60} />}
            {projects.error && <ErrorState message={projects.error} onRetry={projects.reload} />}
            {projects.data && needsAttention.length === 0 && (
              <div className="banner banner-success">
                <Icon name="health" size={15} /> Nothing is overdue, blocked or open across your recent projects.
              </div>
            )}
            {needsAttention.length > 0 && (
              <ul className="list-plain">
                {needsAttention.map(({ project, health }) => (
                  <li key={project.id} className="attention-row">
                    <Link to={`/projects/${project.id}/overview`} className="table-link-primary">
                      {project.name}
                    </Link>
                    <span className="attention-chips">
                      {health!.overdueItemCount > 0 && <Badge tone="danger">{health!.overdueItemCount} overdue</Badge>}
                      {health!.blockedTaskCount > 0 && <Badge tone="warning">{health!.blockedTaskCount} blocked</Badge>}
                      {health!.unresolvedRiskCount > 0 && <Badge tone="warning">{health!.unresolvedRiskCount} risks</Badge>}
                      {health!.unresolvedIssueCount > 0 && <Badge tone="warning">{health!.unresolvedIssueCount} issues</Badge>}
                      {health!.brokenDependencyCount > 0 && (
                        <Badge tone="danger">{health!.brokenDependencyCount} broken links</Badge>
                      )}
                    </span>
                  </li>
                ))}
              </ul>
            )}
          </section>

          <section aria-labelledby="home-projects" className="home-section">
            <div className="card-title-row">
              <h2 id="home-projects">Your projects</h2>
              <Link to="/projects">All projects</Link>
            </div>
            <div className="stat-grid">
              <Link to="/projects" className="stat-card">
                <div className="stat-value">{summary.data.activeProjectCount}</div>
                <div className="stat-label">Active</div>
              </Link>
              <div className="stat-card">
                <div className="stat-value">{summary.data.byStatus.ACTIVE}</div>
                <div className="stat-label">In progress</div>
              </div>
              <div className="stat-card">
                <div className="stat-value">{summary.data.byStatus.PLANNING}</div>
                <div className="stat-label">Planning</div>
              </div>
              <div className="stat-card">
                <div className="stat-value success-text">{summary.data.byStatus.COMPLETED}</div>
                <div className="stat-label">Completed</div>
              </div>
            </div>
            {projects.data && projects.data.length > 0 && (
              <ul className="list-plain project-list">
                {projects.data.map(({ project, health }) => (
                  <li key={project.id} className="attention-row">
                    <Link to={`/projects/${project.id}/overview`} className="table-link-primary">
                      {project.name}
                    </Link>
                    <span className="attention-chips">
                      <ProjectStatusBadge status={project.status} />
                      <PriorityBadge priority={project.priority} />
                      {health && attentionCount(health) === 0 && <Badge tone="success">On track</Badge>}
                    </span>
                  </li>
                ))}
              </ul>
            )}
            {onTrack.length > 0 && needsAttention.length === 0 && projects.data && projects.data.length > 0 && (
              <p className="text-faint">All {onTrack.length} recent project(s) are on track.</p>
            )}
          </section>

          <section aria-labelledby="home-activity" className="home-section">
            <h2 id="home-activity">Recent activity</h2>
            <p className="text-muted">
              An activity feed isn't available yet — the backend records changes but doesn't expose them
              through its API. It will appear here automatically once it does.
            </p>
          </section>
        </>
      )}

      {createOpen && (
        <CreateProjectDialog
          onClose={() => setCreateOpen(false)}
          onCreated={() => {
            setCreateOpen(false)
            summary.reload()
            projects.reload()
          }}
        />
      )}
    </div>
  )
}
