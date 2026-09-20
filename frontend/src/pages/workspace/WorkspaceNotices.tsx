import { Link } from 'react-router-dom'
import { Icon } from '../../components/common/Icon'
import { WORKSPACE_PAGE_SIZE } from '../../hooks/useWorkspaceData'
import type { ProjectSummary } from '../../types/project'

/**
 * Small honest notices shared by the workspace pages. Both derive from the fan-out constraint in
 * `useWorkspaceData`: the backend has no cross-project endpoints, so pages load one page of
 * projects and call a per-project API for each.
 */

/** Shown when the organisation has more projects than the workspace page loads. */
export function TruncationNote({ totalElements, what }: { totalElements: number; what: string }) {
  if (totalElements <= WORKSPACE_PAGE_SIZE) return null
  return (
    <p className="ws-note text-faint" role="note">
      <Icon name="inbox" size={13} /> Showing {what} from the {WORKSPACE_PAGE_SIZE} most recently updated projects (
      {totalElements} in total).
    </p>
  )
}

/** Lists the projects whose per-project call failed, with the successful ones still on screen. */
export function FailedProjectsNotice({
  projects,
  errors,
  what,
  onRetry,
}: {
  projects: ProjectSummary[]
  errors: Record<string, string>
  what: string
  onRetry: () => void
}) {
  const failed = projects.filter((p) => errors[p.id])
  if (failed.length === 0) return null
  const names = failed.map((p) => p.name)
  const shown = names.slice(0, 3).join(', ')
  const rest = names.length - 3
  return (
    <div className="ws-notice banner banner-warning" role="status">
      <Icon name="warning" size={16} className="ws-notice-icon" />
      <span className="ws-notice-text">
        Couldn&apos;t load {what} for {failed.length === 1 ? 'one project' : `${failed.length} projects`}: {shown}
        {rest > 0 ? ` and ${rest} more` : ''}.{' '}
        <span className="ws-notice-detail" title={failed.map((p) => `${p.name}: ${errors[p.id]}`).join('\n')}>
          {errors[failed[0].id]}
        </span>
      </span>
      <button type="button" className="btn btn-sm" onClick={onRetry}>
        <Icon name="refresh" size={13} /> Retry
      </button>
    </div>
  )
}

/**
 * Honest note for the workspace fan-out limit: the backend has no cross-project endpoints, so
 * these pages load one page of projects (`WORKSPACE_PAGE_SIZE`) and call each project's API.
 * Renders nothing when every project fits on that page.
 */
export function ProjectLimitNote({
  totalElements,
  loaded,
  what,
}: {
  totalElements: number
  loaded: number
  what: string
}) {
  if (totalElements <= WORKSPACE_PAGE_SIZE) return null
  return (
    <p className="banner banner-info ws-note" role="note">
      Showing {what} for the {loaded} most recently updated projects out of {totalElements}. Projects beyond
      that are not included here — open <Link to="/projects">Projects</Link> to reach them.
    </p>
  )
}

/**
 * Inline list of projects whose per-project call failed, with a Retry — one failing project
 * never blanks the page (see `usePerProject`).
 */
export function PerProjectErrors({
  projects,
  errors,
  onRetry,
}: {
  projects: ProjectSummary[]
  errors: Record<string, string>
  onRetry: () => void
}) {
  const failed = projects.filter((p) => errors[p.id])
  if (failed.length === 0) return null
  return (
    <div className="banner banner-warning ws-project-errors" role="alert">
      <div className="ws-project-errors-head">
        <strong>
          {failed.length === 1 ? '1 project could not be loaded' : `${failed.length} projects could not be loaded`}
        </strong>
        <button type="button" className="btn btn-sm" onClick={onRetry}>
          Retry
        </button>
      </div>
      <ul className="list-plain ws-project-errors-list">
        {failed.map((p) => (
          <li key={p.id}>
            <Link to={`/projects/${p.id}`}>{p.name}</Link> — {errors[p.id]}
          </li>
        ))}
      </ul>
    </div>
  )
}
