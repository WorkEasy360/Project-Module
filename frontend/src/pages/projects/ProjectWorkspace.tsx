import { useCallback, useMemo } from 'react'
import { NavLink, Outlet, useParams } from 'react-router-dom'
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
import { projectNav } from '../../components/layout/navConfig'

/**
 * Project shell. The tab bar shows only CORE project features plus "More" (both derived from the
 * feature registry); on phones the same items become a bottom tab bar instead.
 */
export function ProjectWorkspace() {
  const { projectId } = useParams<{ projectId: string }>()
  const { identity } = useIdentity()
  const { isMobile } = useSidebar()

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

  return (
    <ProjectWorkspaceContext.Provider
      value={{ project, reloadProject: projectState.reload, currentMember, can }}
    >
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
      <div className={isMobile ? 'has-mobile-tabbar' : undefined}>
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
    </ProjectWorkspaceContext.Provider>
  )
}
