import { useEffect, useState } from 'react'
import { Link, NavLink, useLocation } from 'react-router-dom'
import { useSidebar } from '../../context/SidebarContext'
import { useWorkspaceSettings } from '../../context/WorkspaceSettingsContext'
import { Icon } from '../common/Icon'
import { BrandMark, BrandName, OperationsBadge } from './Brand'
import { SIDEBAR, SIDEBAR_FOOTER, isLinkActive, type SideEntry } from './navConfig'

export { BrandMark }

/**
 * The dark green sidebar: the WorkEasy360 logo with the Operations badge, the main sections (some
 * open to show their pages), Settings below a divider, and the current plan at the bottom.
 */
export function Sidebar() {
  const { isMobile, collapsed, mobileOpen, closeMobile, toggle } = useSidebar()
  const { settings } = useWorkspaceSettings()
  const iconOnly = !isMobile && collapsed
  const visible = isMobile ? mobileOpen : true
  const plan = settings?.plan

  return (
    <>
      {isMobile && mobileOpen && <div className="sidebar-backdrop" onClick={closeMobile} aria-hidden="true" />}
      <aside
        className={`sidebar${iconOnly ? ' collapsed' : ''}${isMobile ? ' mobile' : ''}${mobileOpen ? ' open' : ''}`}
        aria-hidden={isMobile && !visible}
        inert={isMobile && !visible ? true : undefined}
      >
        <Link to="/dashboard" className="sidebar-brand" onClick={closeMobile} aria-label="WorkEasy360 Operations home">
          <span className="sidebar-brand-row">
            <BrandMark size={iconOnly ? 30 : 34} />
            {!iconOnly && <BrandName />}
          </span>
          {!iconOnly && <OperationsBadge size="md" />}
        </Link>

        {!isMobile && (
          <button
            type="button"
            className="sidebar-collapse-btn"
            onClick={toggle}
            aria-label={iconOnly ? 'Expand navigation' : 'Shrink navigation'}
            title={iconOnly ? 'Expand navigation' : 'Shrink navigation'}
          >
            <Icon name={iconOnly ? 'chevronRight' : 'chevronLeft'} size={18} />
          </button>
        )}

        <nav className="sidebar-nav" aria-label="Primary">
          {SIDEBAR.map((entry) => (
            <SidebarEntry key={entry.label} entry={entry} iconOnly={iconOnly} onNavigate={closeMobile} />
          ))}
          <div className="sidebar-divider" role="separator" />
          {SIDEBAR_FOOTER.map((entry) => (
            <SidebarEntry key={entry.label} entry={entry} iconOnly={iconOnly} onNavigate={closeMobile} />
          ))}
        </nav>

        {plan && !iconOnly && (
          <div className="sidebar-plan">
            <div className="sidebar-plan-head">
              <Icon name="crown" size={30} className="sidebar-plan-crown" />
              <div>
                <span className="sidebar-plan-label">Current Plan</span>
                <strong className="sidebar-plan-name">{plan.name}</strong>
                {plan.validUntil && <span className="sidebar-plan-valid">Valid till {plan.validUntil}</span>}
              </div>
            </div>
            <a className="sidebar-plan-btn" href={plan.manageUrl} target="_blank" rel="noopener noreferrer">
              Manage Plan <Icon name="arrowRight" size={16} />
            </a>
          </div>
        )}
      </aside>
    </>
  )
}

function SidebarEntry({ entry, iconOnly, onNavigate }: { entry: SideEntry; iconOnly: boolean; onNavigate: () => void }) {
  const { pathname } = useLocation()
  const childActive = entry.children?.some((c) => isLinkActive(c.to, pathname)) ?? false
  // null: follow the page (open when one of its pages is showing); true/false: the person chose.
  const [open, setOpen] = useState<boolean | null>(null)
  useEffect(() => setOpen(null), [pathname])
  const expanded = open ?? childActive

  if (!entry.children) {
    return (
      <NavLink
        to={entry.to!}
        title={iconOnly ? entry.label : undefined}
        className={() => `sidebar-link${isLinkActive(entry.to!, pathname) ? ' active' : ''}`}
        onClick={onNavigate}
      >
        <Icon name={entry.icon} size={22} />
        {!iconOnly && <span className="sidebar-link-label">{entry.label}</span>}
      </NavLink>
    )
  }

  if (iconOnly) {
    return (
      <NavLink
        to={entry.children[0].to}
        title={entry.label}
        className={() => `sidebar-link${childActive ? ' active' : ''}`}
        onClick={onNavigate}
      >
        <Icon name={entry.icon} size={22} />
      </NavLink>
    )
  }

  return (
    <div className={`sidebar-group${expanded ? ' open' : ''}`}>
      <button
        type="button"
        className={`sidebar-link sidebar-group-toggle${childActive ? ' active' : ''}`}
        aria-expanded={expanded}
        onClick={() => setOpen(!expanded)}
      >
        <Icon name={entry.icon} size={22} />
        <span className="sidebar-link-label">{entry.label}</span>
        <Icon name="chevronDown" size={18} className="sidebar-group-chevron" />
      </button>
      {expanded && (
        <div className="sidebar-sub">
          {entry.children.map((child) => (
            <NavLink
              key={child.to}
              to={child.to}
              className={() => `sidebar-sublink${isLinkActive(child.to, pathname) ? ' active' : ''}`}
              onClick={onNavigate}
            >
              {child.label}
            </NavLink>
          ))}
        </div>
      )}
    </div>
  )
}
