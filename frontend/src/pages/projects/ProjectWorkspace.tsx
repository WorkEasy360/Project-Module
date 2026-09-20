import { useCallback, useMemo, useState } from 'react'
import { NavLink, Outlet, useParams } from 'react-router-dom'
import '../../styles/project.css'
import { useAsyncData } from '../../hooks/useAsyncData'
import { projectsApi } from '../../api/projects'
import { membersApi } from '../../api/members'
import { useIdentity } from '../../context/IdentityContext'
import { useSidebar } from '../../context/SidebarContext'
import { ProjectWorkspaceContext } from '../../context/ProjectWorkspaceContext'
import { hasPermission, type ProjectPermission } from '../../utils/permissions'
import { LoadingState } from '../../components/common/LoadingState'
import { ErrorState } from '../../components/common/ErrorState'
import { Icon } from '../../components/common/Icon'
import { ArchivedBadge, PriorityBadge, ProjectStatusBadge } from '../../components/common/Badge'
import { projectNav } from '../../components/layout/navConfig'
import { formatDate } from '../../utils/format'
import { EditProjectDialog } from './EditProjectDialog'

/**
 * Project shell. A compact header card (identity, status, dates, owner, Edit) sits above the
 * tab bar; the tab bar shows only CORE project features plus "More" (both derived from the
 * feature registry); on phones the same items become a bottom tab bar instead.
 */
export function ProjectWorkspace() {
  const { projectId } = useParams<{ projectId: string }>()
  const { identity } = useIdentity()
  const { isMobile } = useSidebar()
  const [editOpen, setEditOpen] = useState(false)

  const projectState = useAsyncData(() => projectsApi.get(projectId!), [projectId])
  const membersState = useAsyncData(() => membersApi.list(projectId!), [projectId])

  const currentMember = useMemo(() => {
    if (!membersState.data || !identity) return null
    return membersState.data.find((m) => m.userId === identity.userId) ?? null
  }, [membersState.data, identity])

  const can = useCallback(
    (permission: ProjectPermission) => hasPermission(currentMember?.role ?? null, permission),
    [currentMember],
  )

  if (projectState.loading) return <LoadingState label="Loading project…" />
  if (projectState.error) return <ErrorState message={projectState.error} onRetry={projectState.reload} />
  if (!projectState.data) return <ErrorState message="Project not found." />

  const project = projectState.data
  const tabs = projectNav(project.id)
  const hasDates = Boolean(project.startDate || project.targetEndDate)

  return (
    <ProjectWorkspaceContext.Provider
      value={{ project, reloadProject: projectState.reload, currentMember, can }}
    >
      <header className="pj-header rise-in" aria-label="Project summary">
        <span className="pj-tile lg" aria-hidden="true">
          {project.name.charAt(0).toUpperCase()}
        </span>
        <div className="pj-header-main">
          <div className="pj-header-title">
            <h1>{project.name}</h1>
            <ProjectStatusBadge status={project.status} />
            <PriorityBadge priority={project.priority} />
            {project.archived && <ArchivedBadge />}
          </div>
          <div className="pj-header-meta">
            {hasDates && (
              <span className="pj-meta-item" title="Start → target end">
                <Icon name="calendar" size={13} />
                <span>
                  {project.startDate ? formatDate(project.startDate) : 'No start date'}
                  {' → '}
                  {project.targetEndDate ? formatDate(project.targetEndDate) : 'No target end'}
                </span>
              </span>
            )}
            <span className="pj-meta-item" title={`Owner ${project.ownerId}`}>
              <Icon name="user" size={13} />
              <span>
                Owner <span className="mono">{project.ownerId.slice(0, 8)}…</span>
              </span>
            </span>
          </div>
        </div>
        {can('EDIT_PROJECT') && !project.archived && (
          <div className="pj-header-actions">
            <button type="button" className="btn btn-sm" onClick={() => setEditOpen(true)}>
              Edit
            </button>
          </div>
        )}
      </header>

      {!isMobile && (
        <nav className="tab-bar" aria-label="Project sections">
          {tabs.map((tab) => (
            <NavLink
              key={tab.to}
              to={tab.to}
              className={({ isActive }) => `tab-link${isActive ? ' active' : ''}`}
            >
              {tab.label}
            </NavLink>
          ))}
        </nav>
      )}
      <div className={`pj-content${isMobile ? ' has-mobile-tabbar' : ''}`}>
        <Outlet />
      </div>
      {isMobile && (
        <nav className="mobile-tabbar" aria-label="Project sections">
          {tabs.map((tab) => (
            <NavLink
              key={tab.to}
              to={tab.to}
              className={({ isActive }) => `mobile-tab${isActive ? ' active' : ''}`}
            >
              <Icon name={tab.icon} size={20} />
              <span>{tab.label}</span>
            </NavLink>
          ))}
        </nav>
      )}
      {editOpen && <EditProjectDialog onClose={() => setEditOpen(false)} />}
    </ProjectWorkspaceContext.Provider>
  )
}
