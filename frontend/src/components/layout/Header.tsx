import { useEffect, useRef, useState } from 'react'
import { useLocation, useMatch, useNavigate } from 'react-router-dom'
import { IdentityWidget } from '../common/IdentityWidget'
import { IconButton } from '../common/IconButton'
import { Icon } from '../common/Icon'
import { useSidebar } from '../../context/SidebarContext'
import { useTheme } from '../../context/ThemeContext'
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

/**
 * Top bar: hamburger, breadcrumb, global search, theme toggle and the identity control.
 *
 * Search is real and scoped to what the backend offers: inside a project it opens that
 * project's search (the `/projects/{id}/search` API); elsewhere it filters the projects list by
 * name. Ctrl/Cmd+K focuses the box, "/" still jumps straight to project search.
 */
export function Header() {
  const { toggle, collapsed, isMobile } = useSidebar()
  const { resolved, toggle: toggleTheme } = useTheme()
  const crumbs = useBreadcrumb()
  const navigate = useNavigate()
  const projectMatch = useMatch('/projects/:projectId/*')
  const projectId = projectMatch?.params.projectId
  const [query, setQuery] = useState('')
  const searchRef = useRef<HTMLInputElement>(null)

  useEffect(() => {
    function onKeyDown(event: KeyboardEvent) {
      const target = event.target as HTMLElement | null
      const typing = target && ['INPUT', 'TEXTAREA', 'SELECT'].includes(target.tagName)
      if ((event.ctrlKey || event.metaKey) && event.key.toLowerCase() === 'k') {
        event.preventDefault()
        searchRef.current?.focus()
        return
      }
      if (projectId && event.key === '/' && !typing && !event.metaKey && !event.ctrlKey) {
        event.preventDefault()
        navigate(`/projects/${projectId}/search`)
      }
    }
    document.addEventListener('keydown', onKeyDown)
    return () => document.removeEventListener('keydown', onKeyDown)
  }, [projectId, navigate])

  function submitSearch(event: React.FormEvent) {
    event.preventDefault()
    const q = query.trim()
    if (projectId) {
      navigate(`/projects/${projectId}/search${q ? `?q=${encodeURIComponent(q)}` : ''}`)
    } else {
      navigate(`/projects${q ? `?q=${encodeURIComponent(q)}` : ''}`)
    }
  }

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

      <form className="global-search" role="search" onSubmit={submitSearch}>
        <Icon name="search" size={16} />
        <input
          ref={searchRef}
          type="search"
          value={query}
          onChange={(e) => setQuery(e.target.value)}
          placeholder={projectId ? 'Search this project…' : 'Search projects…'}
          aria-label={projectId ? 'Search this project' : 'Search projects'}
        />
        <kbd aria-hidden="true">Ctrl K</kbd>
      </form>

      <div className="app-header-spacer" />

      <IconButton
        icon={resolved === 'dark' ? 'sun' : 'moon'}
        label={resolved === 'dark' ? 'Switch to light mode' : 'Switch to dark mode'}
        onClick={toggleTheme}
      />
      <IdentityWidget />
    </header>
  )
}
