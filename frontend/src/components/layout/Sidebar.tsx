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
          <BrandMark />
          {!iconOnly && (
            <span className="sidebar-brand-text">
              <span className="sidebar-brand-name">WorkEasy360</span>
              <span className="sidebar-brand-tagline">Plan · Collaborate · Achieve</span>
            </span>
          )}
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

/** The WorkEasy360 loop mark, drawn inline so it scales crisply and follows the theme. */
export function BrandMark({ size = 30 }: { size?: number }) {
  return (
    <svg className="brand-mark" width={size} height={size} viewBox="0 0 40 40" aria-hidden="true" focusable="false">
      <defs>
        <linearGradient id="we-brand" x1="0" y1="0" x2="1" y2="1">
          <stop offset="0" stopColor="#6366f1" />
          <stop offset="1" stopColor="#a855f7" />
        </linearGradient>
      </defs>
      <path
        d="M8 12c0-2.8 2.2-5 5-5s5 2.2 5 5v16c0 2.8 2.2 5 5 5s5-2.2 5-5V12c0-2.8 2.2-5 5-5"
        fill="none"
        stroke="url(#we-brand)"
        strokeWidth="5.5"
        strokeLinecap="round"
      />
      <circle cx="33" cy="12" r="4.5" fill="url(#we-brand)" />
    </svg>
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
