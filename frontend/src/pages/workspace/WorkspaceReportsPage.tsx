import { useMemo, useState } from 'react'
import { Link } from 'react-router-dom'
import '../../styles/workspace.css'
import { useAllProjects, usePerProject } from '../../hooks/useWorkspaceData'
import { reportsApi } from '../../api/reports'
import { PageHeader } from '../../components/common/PageHeader'
import { Icon } from '../../components/common/Icon'
import { ProjectStatusBadge } from '../../components/common/Badge'
import { EmptyState } from '../../components/common/EmptyState'
import { ErrorState } from '../../components/common/ErrorState'
import { LoadingState } from '../../components/common/LoadingState'
import { Skeleton } from '../../components/common/Skeleton'
import { humanizeToken } from '../../utils/format'
import { PROJECT_PRIORITIES, PROJECT_STATUSES, type ProjectStatus, type ProjectSummary } from '../../types/project'
import { RISK_STATUSES, TASK_STATUSES, type ProjectSummaryReport } from '../../types/work'
import { ProjectLimitNote, PerProjectErrors } from './WorkspaceNotices'
import { REPORT_SORT_OPTIONS, sortProjects, sumReports, taskProgress, type ReportSort } from './reportsUtils'

/**
 * Reports: each loaded project's summary report (`GET /projects/{id}/reports/summary`) side by
 * side, plus client-side totals. There is no organisation-wide report endpoint, so the totals
 * are computed here from whatever reports loaded.
 */
export function WorkspaceReportsPage() {
  const projectsState = useAllProjects()
  const projects = projectsState.data?.content ?? null
  const reports = usePerProject(projects, (id) => reportsApi.summary(id))

  const [sort, setSort] = useState<ReportSort>('name,ASC')
  const [status, setStatus] = useState<ProjectStatus | ''>('')

  const visible = useMemo(
    () => sortProjects((projects ?? []).filter((p) => status === '' || p.status === status), sort),
    [projects, sort, status],
  )
  const loadedReports = useMemo(
    () => (projects ?? []).map((p) => reports.byProject[p.id]).filter((r): r is ProjectSummaryReport => r !== null && r !== undefined),
    [projects, reports.byProject],
  )
  const totals = useMemo(() => sumReports(loadedReports), [loadedReports])

  const header = <PageHeader title="Reports" description="Summary reports for each of your projects, side by side." />

  if (projectsState.loading) {
    return (
      <div className="ws-page">
        {header}
        <LoadingState label="Loading projects…" />
      </div>
    )
  }
  if (projectsState.error) {
    return (
      <div className="ws-page">
        {header}
        <ErrorState message={projectsState.error} onRetry={projectsState.reload} />
      </div>
    )
  }
  if (!projects || projects.length === 0) {
    return (
      <div className="ws-page">
        {header}
        <EmptyState
          title="No projects yet"
          description="Reports appear here once you have a project."
          action={
            <Link className="btn btn-primary" to="/projects">
              Go to Projects
            </Link>
          }
        />
      </div>
    )
  }

  return (
    <div className="ws-page rise-in">
      {header}
      <ProjectLimitNote totalElements={projectsState.data?.totalElements ?? 0} loaded={projects.length} what="reports" />
      <PerProjectErrors projects={projects} errors={reports.errors} onRetry={reports.reload} />

      <TotalsStrip totals={totals} loaded={loadedReports.length} total={projects.length} loading={reports.loading} />

      <div className="ws-toolbar">
        <div className="form-field">
          <label htmlFor="reports-status">Status</label>
          <select id="reports-status" value={status} onChange={(e) => setStatus(e.target.value as ProjectStatus | '')}>
            <option value="">All statuses</option>
            {PROJECT_STATUSES.map((s) => (
              <option key={s} value={s}>
                {humanizeToken(s)}
              </option>
            ))}
          </select>
        </div>
        <div className="form-field">
          <label htmlFor="reports-sort">Sort by</label>
          <select id="reports-sort" value={sort} onChange={(e) => setSort(e.target.value as ReportSort)}>
            {REPORT_SORT_OPTIONS.map((o) => (
              <option key={o.value} value={o.value}>
                {o.label}
              </option>
            ))}
          </select>
        </div>
        <span className="spacer" />
        <span className="ws-count">
          Showing {visible.length} of {projects.length} loaded
        </span>
      </div>

      {visible.length === 0 ? (
        <EmptyState title="No projects match this filter." />
      ) : (
        <ul className="ws-grid" aria-label="Project reports">
          {visible.map((project) => (
            <ReportCard
              key={project.id}
              project={project}
              report={reports.byProject[project.id] ?? null}
              error={reports.errors[project.id]}
              loading={reports.loading}
            />
          ))}
        </ul>
      )}
    </div>
  )
}

function TotalsStrip({
  totals,
  loaded,
  total,
  loading,
}: {
  totals: ProjectSummaryReport
  loaded: number
  total: number
  loading: boolean
}) {
  const progress = taskProgress(totals)
  const openIssues = PROJECT_PRIORITIES.reduce((s, p) => s + (totals.issueCountByPriority[p] ?? 0), 0)
  const items: { label: string; value: number; tone?: 'danger' | 'warning' }[] = [
    { label: 'Tasks', value: progress.total },
    { label: 'Completed tasks', value: progress.completed },
    { label: 'Blocked tasks', value: totals.taskCountByStatus.BLOCKED ?? 0, tone: 'danger' },
    { label: 'Overdue tasks', value: totals.taskCountByStatus.OVERDUE ?? 0, tone: 'warning' },
    { label: 'Open risks', value: totals.riskCountByStatus.OPEN ?? 0, tone: 'warning' },
    { label: 'Issues', value: openIssues },
    { label: 'Decisions', value: totals.decisionCount },
    { label: 'Delayed items', value: totals.delayedItemCount, tone: 'danger' },
    { label: 'Broken dependencies', value: totals.brokenDependencyCount, tone: 'danger' },
  ]
  return (
    <section className="ws-strip" aria-labelledby="reports-totals-title">
      <div className="ws-strip-head">
        <h2 id="reports-totals-title">Across your projects</h2>
        <p>
          Computed from the loaded project reports ({loading ? '…' : loaded} of {total}).
        </p>
      </div>
      <div className="ws-strip-grid">
        {items.map((item) => (
          <div className="ws-strip-item" key={item.label}>
            <strong className={item.tone && item.value > 0 ? item.tone : undefined}>{loading ? '…' : item.value}</strong>
            <span>{item.label}</span>
          </div>
        ))}
      </div>
    </section>
  )
}

function ReportCard({
  project,
  report,
  error,
  loading,
}: {
  project: ProjectSummary
  report: ProjectSummaryReport | null
  error: string | undefined
  loading: boolean
}) {
  const progress = report ? taskProgress(report) : null
  return (
    <li className="ws-card">
      <div className="ws-card-head">
        <h3 className="ws-card-title">
          <Link to={`/projects/${project.id}/reports`}>{project.name}</Link>
        </h3>
        <ProjectStatusBadge status={project.status} />
      </div>

      {error ? (
        <p className="ws-card-error">Report could not be loaded: {error}</p>
      ) : !report ? (
        loading ? (
          <div aria-hidden="true">
            <Skeleton height={8} />
            <div style={{ marginTop: 10 }}>
              <Skeleton width="70%" />
            </div>
          </div>
        ) : null
      ) : (
        <>
          {progress && progress.total > 0 ? (
            <div className="ws-progress">
              <div
                className="progress-bar"
                role="progressbar"
                aria-valuemin={0}
                aria-valuemax={100}
                aria-valuenow={progress.percent}
                aria-label={`${progress.completed} of ${progress.total} tasks completed`}
              >
                <div className="progress-fill" style={{ width: `${progress.percent}%` }} />
              </div>
              <span>
                {progress.completed}/{progress.total} tasks · {progress.percent}%
              </span>
            </div>
          ) : (
            <p className="text-faint" style={{ margin: 0, fontSize: 12.5 }}>
              No tasks yet.
            </p>
          )}

          <div className="ws-report-group">
            <h4>Tasks by status</h4>
            <ul className="ws-numbers">
              {TASK_STATUSES.map((s) => (
                <li key={s}>
                  <strong className={toneFor(s, report.taskCountByStatus[s] ?? 0)}>{report.taskCountByStatus[s] ?? 0}</strong>
                  {humanizeToken(s)}
                </li>
              ))}
            </ul>
          </div>
          <div className="ws-report-group">
            <h4>Risks by status</h4>
            <ul className="ws-numbers">
              {RISK_STATUSES.map((s) => (
                <li key={s}>
                  <strong className={toneFor(s, report.riskCountByStatus[s] ?? 0)}>{report.riskCountByStatus[s] ?? 0}</strong>
                  {humanizeToken(s)}
                </li>
              ))}
            </ul>
          </div>
          <div className="ws-report-group">
            <h4>Risks by priority</h4>
            <ul className="ws-numbers">
              {PROJECT_PRIORITIES.map((p) => (
                <li key={p}>
                  <strong>{report.riskCountByPriority[p] ?? 0}</strong>
                  {humanizeToken(p)}
                </li>
              ))}
            </ul>
          </div>
          <div className="ws-report-group">
            <h4>Issues by priority</h4>
            <ul className="ws-numbers">
              {PROJECT_PRIORITIES.map((p) => (
                <li key={p}>
                  <strong>{report.issueCountByPriority[p] ?? 0}</strong>
                  {humanizeToken(p)}
                </li>
              ))}
            </ul>
          </div>
          <div className="ws-report-group">
            <h4>Other</h4>
            <ul className="ws-numbers">
              <li>
                <strong>{report.decisionCount}</strong>Decisions
              </li>
              <li>
                <strong className={report.delayedItemCount > 0 ? 'danger' : undefined}>{report.delayedItemCount}</strong>
                Delayed items
              </li>
              <li>
                <strong className={report.brokenDependencyCount > 0 ? 'danger' : undefined}>
                  {report.brokenDependencyCount}
                </strong>
                Broken dependencies
              </li>
            </ul>
          </div>
        </>
      )}

      <div className="ws-card-foot">
        <span className="text-faint">Priority: {humanizeToken(project.priority)}</span>
        <Link to={`/projects/${project.id}/reports`}>
          Full report <Icon name="arrowRight" size={14} />
        </Link>
      </div>
    </li>
  )
}

/** Highlights non-zero counts that mean trouble (blocked / overdue tasks, open risks). */
function toneFor(key: string, value: number): 'danger' | 'warning' | undefined {
  if (value === 0) return undefined
  if (key === 'BLOCKED') return 'danger'
  if (key === 'OVERDUE' || key === 'OPEN') return 'warning'
  return undefined
}
