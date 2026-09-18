import { NavLink, useMatch } from 'react-router-dom'
import { useSidebar } from '../../context/SidebarContext'
import { Icon } from '../common/Icon'
import { projectNav, workspaceNav, type NavItem } from './navConfig'

/**
 * Deliberately short: only CORE features from the feature registry plus a "More" entry that
 * opens the Advanced Features hub. Everything else stays reachable through that hub — the
 * sidebar never needs editing when a feature is added.
 */
export function Sidebar() {
  const { isMobile, collapsed, mobileOpen, closeMobile } = useSidebar()
  const projectMatch = useMatch('/projects/:projectId/*')
  const projectId = projectMatch?.params.projectId

  const iconOnly = !isMobile && collapsed
  const visible = isMobile ? mobileOpen : true

  return (
    <>
      {isMobile && mobileOpen && (
        <div className="sidebar-backdrop" onClick={closeMobile} aria-hidden="true" />
      )}
      <aside
        className={`sidebar${iconOnly ? ' collapsed' : ''}${isMobile ? ' mobile' : ''}${mobileOpen ? ' open' : ''}`}
        aria-hidden={isMobile && !visible}
        inert={isMobile && !visible ? true : undefined}
      >
        <div className="sidebar-brand">
          <span className="sidebar-brand-mark" aria-hidden="true">
            PM
          </span>
          {!iconOnly && <span className="sidebar-brand-name">Project Module</span>}
        </div>
        <nav className="sidebar-nav" aria-label="Primary">
          <NavGroup title="Workspace" items={workspaceNav()} iconOnly={iconOnly} onNavigate={closeMobile} />
          {projectId && (
            <>
              <div className="sidebar-divider" role="separator" />
              <NavGroup
                title="This project"
                items={projectNav(projectId)}
                iconOnly={iconOnly}
                onNavigate={closeMobile}
              />
            </>
          )}
        </nav>
      </aside>
    </>
  )
}

function NavGroup({
  title,
  items,
  iconOnly,
  onNavigate,
}: {
  title: string
  items: NavItem[]
  iconOnly: boolean
  onNavigate: () => void
}) {
  return (
    <div className="sidebar-section">
      {!iconOnly && <div className="sidebar-section-title">{title}</div>}
      {items.map((item) => (
        <NavLink
          key={item.to}
          to={item.to}
          title={iconOnly ? item.label : undefined}
          className={({ isActive }) => `sidebar-link${isActive ? ' active' : ''}`}
          onClick={onNavigate}
        >
          <Icon name={item.icon} />
          {!iconOnly && <span className="sidebar-link-label">{item.label}</span>}
        </NavLink>
      ))}
    </div>
  )
}
