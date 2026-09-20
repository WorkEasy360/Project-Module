import { useMemo, useState } from 'react'
import { Link } from 'react-router-dom'
import '../../styles/workspace.css'
import { useAllProjects, usePerProject } from '../../hooks/useWorkspaceData'
import { useIdentity } from '../../context/IdentityContext'
import { membersApi } from '../../api/members'
import { PageHeader } from '../../components/common/PageHeader'
import { Icon } from '../../components/common/Icon'
import { Badge, ProjectStatusBadge } from '../../components/common/Badge'
import { EmptyState } from '../../components/common/EmptyState'
import { ErrorState } from '../../components/common/ErrorState'
import { LoadingState } from '../../components/common/LoadingState'
import { Skeleton } from '../../components/common/Skeleton'
import { humanizeToken } from '../../utils/format'
import { PROJECT_ROLES, type ProjectMember, type ProjectRole, type ProjectSummary } from '../../types/project'
import { ProjectLimitNote, PerProjectErrors } from './WorkspaceNotices'
import {
  ROLE_TONE,
  avatarText,
  collectMemberships,
  filterMemberships,
  groupPeople,
  shortId,
  teamSummary,
  type Membership,
  type Person,
} from './teamUtils'

type View = 'people' | 'projects'

/**
 * My Team: every member of every loaded project. The backend only lists members per project
 * (`GET /projects/{id}/members`) and has no user directory, so people are shown by user id and
 * roles are managed on each project's own Members tab.
 */
export function TeamPage() {
  const { identity } = useIdentity()
  const projectsState = useAllProjects()
  const projects = projectsState.data?.content ?? null
  const members = usePerProject(projects, (id) => membersApi.list(id))

  const [view, setView] = useState<View>('people')
  const [query, setQuery] = useState('')
  const [role, setRole] = useState<ProjectRole | ''>('')

  const all = useMemo(() => collectMemberships(projects ?? [], members.byProject), [projects, members.byProject])
  const filtered = useMemo(() => filterMemberships(all, { query, role }), [all, query, role])
  const people = useMemo(() => groupPeople(filtered, identity?.userId), [filtered, identity?.userId])
  const summary = useMemo(() => teamSummary(all), [all])
  const hasFilters = query.trim() !== '' || role !== ''

  const header = <PageHeader title="My Team" description="Who is on which project, and in what role." />

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
          description="Your team appears here once you are a member of a project."
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
      <ProjectLimitNote totalElements={projectsState.data?.totalElements ?? 0} loaded={projects.length} what="members" />
      <PerProjectErrors projects={projects} errors={members.errors} onRetry={members.reload} />

      <div className="ws-chips" aria-label="Team summary">
        <span className="ws-chip is-accent">
          <strong>{members.loading ? '…' : summary.people}</strong> people
        </span>
        <span className="ws-chip">
          <strong>{members.loading ? '…' : summary.projects}</strong> projects
        </span>
        <span className="ws-chip">
          <strong>{members.loading ? '…' : summary.owners}</strong> owners
        </span>
        <span className="ws-chip">
          <strong>{members.loading ? '…' : summary.managers}</strong> managers
        </span>
      </div>
      <p className="ws-hint">
        This workspace has no user directory, so people are shown by their user id. Roles are changed on each
        project's Members tab.
      </p>

      <div className="ws-toolbar">
        <div className="ws-seg" role="group" aria-label="View">
          <button type="button" aria-pressed={view === 'people'} onClick={() => setView('people')}>
            People
          </button>
          <button type="button" aria-pressed={view === 'projects'} onClick={() => setView('projects')}>
            By project
          </button>
        </div>
        <div className="form-field ws-search">
          <label htmlFor="team-search">Search</label>
          <input
            id="team-search"
            type="search"
            placeholder="User id or project name"
            value={query}
            onChange={(e) => setQuery(e.target.value)}
          />
        </div>
        <div className="form-field">
          <label htmlFor="team-role">Role</label>
          <select id="team-role" value={role} onChange={(e) => setRole(e.target.value as ProjectRole | '')}>
            <option value="">All roles</option>
            {PROJECT_ROLES.map((r) => (
              <option key={r} value={r}>
                {humanizeToken(r)}
              </option>
            ))}
          </select>
        </div>
        {hasFilters && (
          <button
            type="button"
            className="btn btn-sm btn-ghost"
            onClick={() => {
              setQuery('')
              setRole('')
            }}
          >
            Reset filters
          </button>
        )}
      </div>

      {members.loading ? (
        <TeamSkeleton />
      ) : view === 'people' ? (
        <PeopleView people={people} hasFilters={hasFilters} />
      ) : (
        <ProjectsView
          projects={projects}
          byProject={members.byProject}
          errors={members.errors}
          filtered={filtered}
          hasFilters={hasFilters}
          meId={identity?.userId}
        />
      )}
    </div>
  )
}

function TeamSkeleton() {
  return (
    <ul className="ws-people" aria-hidden="true">
      {Array.from({ length: 4 }).map((_, i) => (
        <li className="ws-person" key={i}>
          <Skeleton width={34} height={34} />
          <Skeleton width="60%" height={14} />
          <Skeleton width="80%" height={14} />
        </li>
      ))}
    </ul>
  )
}

function PeopleView({ people, hasFilters }: { people: Person[]; hasFilters: boolean }) {
  if (people.length === 0) {
    return (
      <EmptyState
        title={hasFilters ? 'No people match these filters.' : 'No members found'}
        description={hasFilters ? undefined : 'The loaded projects have no members yet.'}
      />
    )
  }
  return (
    <>
      <p className="ws-count">
        {people.length} {people.length === 1 ? 'person' : 'people'}
      </p>
      <ul className="ws-people" aria-label="People">
        {people.map((person) => (
          <li key={person.userId} className={`ws-person${person.isMe ? ' is-me' : ''}`}>
            <span className="ws-avatar" aria-hidden="true">
              {avatarText(person.userId)}
            </span>
            <div className="ws-person-id">
              <span className="mono" title={person.userId}>
                {shortId(person.userId)}
              </span>
              <span className="ws-person-meta">
                {person.isMe && <Badge tone="primary">You</Badge>}
                <span>
                  {person.memberships.length} {person.memberships.length === 1 ? 'project' : 'projects'}
                </span>
              </span>
            </div>
            <ul className="ws-roles" aria-label={`Projects of ${shortId(person.userId)}`}>
              {person.memberships.map(({ project, member }) => (
                <li key={member.id} className="ws-role">
                  <Link to={`/projects/${project.id}/members`}>{project.name}</Link>
                  <Badge tone={ROLE_TONE[member.role]}>{humanizeToken(member.role)}</Badge>
                </li>
              ))}
            </ul>
          </li>
        ))}
      </ul>
    </>
  )
}

function ProjectsView({
  projects,
  byProject,
  errors,
  filtered,
  hasFilters,
  meId,
}: {
  projects: ProjectSummary[]
  byProject: Record<string, ProjectMember[] | null>
  errors: Record<string, string>
  filtered: Membership[]
  hasFilters: boolean
  meId: string | undefined
}) {
  const cards = projects
    .map((project) => ({
      project,
      members: filtered.filter((m) => m.project.id === project.id).map((m) => m.member),
      error: errors[project.id],
      loaded: byProject[project.id] !== null && byProject[project.id] !== undefined,
    }))
    // With filters active, only projects that still have a matching member are shown.
    .filter((c) => !hasFilters || c.members.length > 0)

  if (cards.length === 0) {
    return <EmptyState title="No projects match these filters." />
  }

  return (
    <ul className="ws-grid" aria-label="Projects">
      {cards.map(({ project, members, error, loaded }) => (
        <li key={project.id} className="ws-card">
          <div className="ws-card-head">
            <h3 className="ws-card-title">
              <Link to={`/projects/${project.id}`}>{project.name}</Link>
            </h3>
            <ProjectStatusBadge status={project.status} />
          </div>
          {error ? (
            <p className="ws-card-error">Members could not be loaded: {error}</p>
          ) : !loaded ? (
            <Skeleton height={14} />
          ) : members.length === 0 ? (
            <p className="text-muted">No members.</p>
          ) : (
            <ul className="ws-member-list" aria-label={`Members of ${project.name}`}>
              {members.map((m) => (
                <li key={m.id} className={`ws-member${m.userId === meId ? ' is-me' : ''}`}>
                  <span className="ws-avatar sm" aria-hidden="true">
                    {avatarText(m.userId)}
                  </span>
                  <span className="mono" title={m.userId}>
                    {shortId(m.userId)}
                    {m.userId === meId ? ' (you)' : ''}
                  </span>
                  <Badge tone={ROLE_TONE[m.role]}>{humanizeToken(m.role)}</Badge>
                </li>
              ))}
            </ul>
          )}
          <div className="ws-card-foot">
            <span className="text-faint">
              {loaded && !error ? `${members.length} ${members.length === 1 ? 'member' : 'members'}` : ''}
            </span>
            <Link to={`/projects/${project.id}/members`}>
              Manage members <Icon name="arrowRight" size={14} />
            </Link>
          </div>
        </li>
      ))}
    </ul>
  )
}
