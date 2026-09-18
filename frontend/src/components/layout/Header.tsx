import { useEffect } from 'react'
import { useLocation, useMatch, useNavigate } from 'react-router-dom'
import { IdentityWidget } from '../common/IdentityWidget'
import { IconButton } from '../common/IconButton'
import { useSidebar } from '../../context/SidebarContext'
import { ROUTE_LABELS } from './navConfig'

function useBreadcrumb(): string[] {
  const { pathname } = useLocation()
  const crumbs = pathname
    .split('/')
    .filter(Boolean)
    .map((segment) => ROUTE_LABELS[segment])
    .filter((label): label is string => Boolean(label))
  return crumbs.length > 0 ? crumbs : ['Home']
}

export function Header() {
  const { toggle, collapsed, isMobile } = useSidebar()
  const crumbs = useBreadcrumb()
  const navigate = useNavigate()
  const projectMatch = useMatch('/projects/:projectId/*')
  const projectId = projectMatch?.params.projectId

  // "/" jumps to project search — the seam a future command palette would plug into.
  useEffect(() => {
    if (!projectId) return
    function onKeyDown(event: KeyboardEvent) {
      const target = event.target as HTMLElement | null
      const typing = target && ['INPUT', 'TEXTAREA', 'SELECT'].includes(target.tagName)
      if (event.key === '/' && !typing && !event.metaKey && !event.ctrlKey) {
        event.preventDefault()
        navigate(`/projects/${projectId}/search`)
      }
    }
    document.addEventListener('keydown', onKeyDown)
    return () => document.removeEventListener('keydown', onKeyDown)
  }, [projectId, navigate])

  return (
    <header className="app-header">
      <div className="app-header-left">
        <IconButton
          icon="menu"
          label={isMobile ? 'Open navigation' : collapsed ? 'Expand sidebar' : 'Collapse sidebar'}
          onClick={toggle}
          active={!isMobile && !collapsed}
        />
        <nav className="breadcrumb" aria-label="Breadcrumb">
          {crumbs.map((crumb, i) => (
            <span key={`${crumb}-${i}`} className="breadcrumb-item">
              {i > 0 && <span className="breadcrumb-sep">/</span>}
              {crumb}
            </span>
          ))}
        </nav>
      </div>
      <div className="app-header-spacer" />
      {projectId && (
        <IconButton
          icon="search"
          label="Search this project (press /)"
          onClick={() => navigate(`/projects/${projectId}/search`)}
        />
      )}
      <IdentityWidget />
    </header>
  )
}
