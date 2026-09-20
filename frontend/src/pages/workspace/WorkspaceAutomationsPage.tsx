import { useCallback, useMemo, useState } from 'react'
import { Link } from 'react-router-dom'
import '../../styles/workspace.css'
import { useAllProjects, usePerProject } from '../../hooks/useWorkspaceData'
import { automationsApi } from '../../api/automations'
import { toUserMessage } from '../../api/errorMessage'
import { PageHeader } from '../../components/common/PageHeader'
import { Icon } from '../../components/common/Icon'
import { Badge, RunStatusBadge } from '../../components/common/Badge'
import { EmptyState } from '../../components/common/EmptyState'
import { ErrorState } from '../../components/common/ErrorState'
import { LoadingState } from '../../components/common/LoadingState'
import { TableSkeleton } from '../../components/common/Skeleton'
import { formatDateTime, humanizeToken } from '../../utils/format'
import { ProjectLimitNote, PerProjectErrors } from './WorkspaceNotices'
import {
  EMPTY_AUTOMATION_FILTERS,
  collectAutomations,
  filterAutomations,
  latestRun,
  sortRuns,
  triggerOptions,
  type AutomationFilters,
  type AutomationRow,
  type EnabledFilter,
  type RunsState,
} from './automationsUtils'

const RUNS_SHOWN = 5

/**
 * Automations: every rule of every loaded project (`GET /projects/{id}/automations`). Run
 * history (`GET /automations/{id}/runs`) is fetched per rule only when its row is expanded, so
 * opening the page costs one call per project, not one per rule. Rules are created and edited
 * on each project's Automations tab.
 */
export function WorkspaceAutomationsPage() {
  const projectsState = useAllProjects()
  const projects = projectsState.data?.content ?? null
  const automations = usePerProject(projects, (id) => automationsApi.list(id))

  const [filters, setFilters] = useState<AutomationFilters>(EMPTY_AUTOMATION_FILTERS)
  const [expanded, setExpanded] = useState<Record<string, boolean>>({})
  const [runs, setRuns] = useState<Record<string, RunsState>>({})

  const rows = useMemo(() => collectAutomations(projects ?? [], automations.byProject), [projects, automations.byProject])
  const visible = useMemo(() => filterAutomations(rows, filters), [rows, filters])
  const triggers = useMemo(() => triggerOptions(rows), [rows])
  const hasFilters = filters.projectId !== '' || filters.trigger !== '' || filters.enabled !== ''
  const enabledCount = rows.filter((r) => r.automation.enabled).length
  const projectCount = new Set(rows.map((r) => r.project.id)).size

  const loadRuns = useCallback((automationId: string) => {
    setRuns((prev) => ({ ...prev, [automationId]: { status: 'loading' } }))
    automationsApi
      .runs(automationId)
      .then((list) => setRuns((prev) => ({ ...prev, [automationId]: { status: 'loaded', runs: sortRuns(list) } })))
      .catch((err: unknown) =>
        setRuns((prev) => ({ ...prev, [automationId]: { status: 'error', message: toUserMessage(err) } })),
      )
  }, [])

  function toggleRow(automationId: string) {
    const opening = !expanded[automationId]
    setExpanded((prev) => ({ ...prev, [automationId]: opening }))
    const state = runs[automationId]?.status ?? 'idle'
    if (opening && state === 'idle') loadRuns(automationId)
  }

  const header = (
    <PageHeader title="Automations" description="All automation rules across your projects and their recent runs." />
  )

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
          description="Automation rules live inside projects."
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
      <ProjectLimitNote
        totalElements={projectsState.data?.totalElements ?? 0}
        loaded={projects.length}
        what="automation rules"
      />
      <PerProjectErrors projects={projects} errors={automations.errors} onRetry={automations.reload} />

      <div className="ws-chips" aria-label="Automation summary">
        <span className="ws-chip is-accent">
          <strong>{automations.loading ? '…' : rows.length}</strong> rules
        </span>
        <span className="ws-chip">
          <strong>{automations.loading ? '…' : enabledCount}</strong> enabled
        </span>
        <span className="ws-chip">
          <strong>{automations.loading ? '…' : projectCount}</strong> projects with rules
        </span>
      </div>
      <p className="ws-hint">
        Rules are created, edited and archived on each project's Automations tab. Run history loads when you
        expand a rule.
      </p>

      <div className="ws-toolbar">
        <div className="form-field">
          <label htmlFor="auto-project">Project</label>
          <select
            id="auto-project"
            value={filters.projectId}
            onChange={(e) => setFilters((f) => ({ ...f, projectId: e.target.value }))}
          >
            <option value="">All projects</option>
            {projects.map((p) => (
              <option key={p.id} value={p.id}>
                {p.name}
              </option>
            ))}
          </select>
        </div>
        <div className="form-field">
          <label htmlFor="auto-trigger">Trigger</label>
          <select
            id="auto-trigger"
            value={filters.trigger}
            onChange={(e) => setFilters((f) => ({ ...f, trigger: e.target.value }))}
          >
            <option value="">All triggers</option>
            {triggers.map((t) => (
              <option key={t} value={t}>
                {humanizeToken(t)}
              </option>
            ))}
          </select>
        </div>
        <div className="form-field">
          <label htmlFor="auto-enabled">State</label>
          <select
            id="auto-enabled"
            value={filters.enabled}
            onChange={(e) => setFilters((f) => ({ ...f, enabled: e.target.value as EnabledFilter }))}
          >
            <option value="">Enabled and disabled</option>
            <option value="enabled">Enabled</option>
            <option value="disabled">Disabled</option>
          </select>
        </div>
        {hasFilters && (
          <button type="button" className="btn btn-sm btn-ghost" onClick={() => setFilters(EMPTY_AUTOMATION_FILTERS)}>
            Reset filters
          </button>
        )}
        <span className="spacer" />
        {!automations.loading && (
          <span className="ws-count">
            Showing {visible.length} of {rows.length}
          </span>
        )}
      </div>

      {automations.loading ? (
        <TableSkeleton rows={4} columns={6} />
      ) : rows.length === 0 ? (
        <EmptyState
          title="No automation rules yet"
          description="Create rules from a project's Automations tab; they will all show up here."
        />
      ) : visible.length === 0 ? (
        <EmptyState title="No rules match these filters." />
      ) : (
        <div className="ws-table-wrap">
          <table className="ws-table">
            <thead>
              <tr>
                <th scope="col">Rule</th>
                <th scope="col">Project</th>
                <th scope="col">Trigger</th>
                <th scope="col">Action</th>
                <th scope="col">Status</th>
                <th scope="col">Last run</th>
                <th scope="col">
                  <span className="sr-only">Details</span>
                </th>
              </tr>
            </thead>
            <tbody>
              {visible.map((row) => (
                <AutomationRows
                  key={row.automation.id}
                  row={row}
                  expanded={expanded[row.automation.id] === true}
                  runs={runs[row.automation.id] ?? { status: 'idle' }}
                  onToggle={() => toggleRow(row.automation.id)}
                  onLoadRuns={() => loadRuns(row.automation.id)}
                />
              ))}
            </tbody>
          </table>
        </div>
      )}
    </div>
  )
}

function AutomationRows({
  row,
  expanded,
  runs,
  onToggle,
  onLoadRuns,
}: {
  row: AutomationRow
  expanded: boolean
  runs: RunsState
  onToggle: () => void
  onLoadRuns: () => void
}) {
  const { automation, project } = row
  const detailsId = `auto-details-${automation.id}`
  const runsHref = `/projects/${project.id}/automations/${automation.id}/runs`

  return (
    <>
      <tr className={expanded ? 'is-expanded' : undefined}>
        <td data-label="Rule" className="ws-name">
          {automation.name}
        </td>
        <td data-label="Project">
          <Link to={`/projects/${project.id}/automations`}>{project.name}</Link>
        </td>
        <td data-label="Trigger">
          <Badge tone="info">{humanizeToken(automation.triggerEvent)}</Badge>
        </td>
        <td data-label="Action">{humanizeToken(automation.actionType)}</td>
        <td data-label="Status">
          {automation.archived ? (
            <Badge tone="neutral">Archived</Badge>
          ) : (
            <Badge tone={automation.enabled ? 'success' : 'neutral'}>{automation.enabled ? 'Enabled' : 'Disabled'}</Badge>
          )}
        </td>
        <td data-label="Last run">
          <LastRunCell runs={runs} onLoad={onLoadRuns} runsHref={runsHref} />
        </td>
        <td>
          <button
            type="button"
            className="btn btn-sm btn-ghost"
            aria-expanded={expanded}
            aria-controls={detailsId}
            onClick={onToggle}
          >
            {expanded ? 'Hide' : 'Details'}
          </button>
        </td>
      </tr>
      {expanded && (
        <tr className="ws-details" id={detailsId}>
          <td colSpan={7}>
            <div className="ws-details-body">
              <div>
                <h4>Rule</h4>
                <dl>
                  {automation.description && (
                    <>
                      <dt>Description</dt>
                      <dd>{automation.description}</dd>
                    </>
                  )}
                  <dt>Message</dt>
                  <dd>{automation.actionMessage}</dd>
                  {automation.actionRecipientId && (
                    <>
                      <dt>Recipient</dt>
                      <dd className="mono">{automation.actionRecipientId}</dd>
                    </>
                  )}
                  {automation.actionChannelReference && (
                    <>
                      <dt>Channel</dt>
                      <dd className="mono">{automation.actionChannelReference}</dd>
                    </>
                  )}
                  <dt>Updated</dt>
                  <dd>{formatDateTime(automation.updatedAt)}</dd>
                </dl>
                <p className="ws-hint" style={{ marginTop: 10 }}>
                  <Link to={`/projects/${project.id}/automations`}>
                    Edit on the project's Automations tab <Icon name="arrowRight" size={13} />
                  </Link>
                </p>
              </div>
              <div>
                <h4>Recent runs</h4>
                <RunsList runs={runs} onRetry={onLoadRuns} runsHref={runsHref} />
              </div>
            </div>
          </td>
        </tr>
      )}
    </>
  )
}

function LastRunCell({ runs, onLoad, runsHref }: { runs: RunsState; onLoad: () => void; runsHref: string }) {
  if (runs.status === 'idle') {
    return (
      <button type="button" className="btn btn-sm" onClick={onLoad}>
        Load runs
      </button>
    )
  }
  if (runs.status === 'loading') return <span className="text-faint">Loading…</span>
  if (runs.status === 'error') {
    return (
      <span className="ws-lastrun">
        <span className="text-danger">Failed to load</span>
        <button type="button" className="btn btn-sm" onClick={onLoad}>
          Retry
        </button>
      </span>
    )
  }
  const last = latestRun(runs.runs)
  if (!last) return <span className="text-faint">Never run</span>
  return (
    <span className="ws-lastrun">
      <RunStatusBadge status={last.status} />
      <Link className="text-faint" to={runsHref} title="Run history">
        {formatDateTime(last.executedAt)}
      </Link>
    </span>
  )
}

function RunsList({ runs, onRetry, runsHref }: { runs: RunsState; onRetry: () => void; runsHref: string }) {
  if (runs.status === 'idle' || runs.status === 'loading') return <p className="text-faint">Loading runs…</p>
  if (runs.status === 'error') {
    return (
      <p className="text-danger">
        {runs.message}{' '}
        <button type="button" className="btn btn-sm" onClick={onRetry}>
          Retry
        </button>
      </p>
    )
  }
  if (runs.runs.length === 0) return <p className="text-faint">This rule has not run yet.</p>
  return (
    <>
      <ul className="ws-runs">
        {runs.runs.slice(0, RUNS_SHOWN).map((run) => (
          <li key={run.id}>
            <RunStatusBadge status={run.status} />
            <span>{formatDateTime(run.executedAt)}</span>
            <span className="text-faint">{humanizeToken(run.triggerEvent)}</span>
            {run.errorMessage && <span className="ws-run-error">{run.errorMessage}</span>}
          </li>
        ))}
      </ul>
      <p className="ws-hint" style={{ marginTop: 8 }}>
        <Link to={runsHref}>
          {runs.runs.length > RUNS_SHOWN ? `All ${runs.runs.length} runs` : 'Run history'}{' '}
          <Icon name="arrowRight" size={13} />
        </Link>
      </p>
    </>
  )
}
