import { NavLink } from 'react-router-dom'
import { useSidebar } from '../../context/SidebarContext'
import { Icon } from '../common/Icon'
import { BrandMark, BrandName } from './Brand'
import { useIdentity } from '../../context/IdentityContext'
import { usePeople } from '../../context/PeopleContext'
import { initials } from '../../utils/format'

export { BrandMark }
import { workspaceNavGroups, type NavItem } from './navConfig'

/**
 * The workspace's core features, in short labelled sections. A project's own screens are its tabs,
 * on the project page, so they are not repeated here.
 */
export function Sidebar() {
  const { isMobile, collapsed, mobileOpen, closeMobile } = useSidebar()
  const { identity } = useIdentity()
  const { isAdmin } = usePeople()

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
              <span className="sidebar-brand-name">
                <BrandName />
              </span>
              <span className="sidebar-brand-tagline">Plan · Collaborate · Achieve</span>
            </span>
          )}
        </div>
        <nav className="sidebar-nav" aria-label="Primary">
          {workspaceNavGroups().map((group) => (
            <NavGroup key={group.title} title={group.title} items={group.items} iconOnly={iconOnly} onNavigate={closeMobile} />
          ))}
        </nav>
        {identity && (
          <div className="sidebar-user" title={iconOnly ? identity.name ?? undefined : undefined}>
            <span className="sidebar-user-avatar" aria-hidden="true">
              {initials(identity.name ?? identity.email ?? '?')}
            </span>
            {!iconOnly && (
              <span className="sidebar-user-text">
                <strong>{identity.name ?? identity.email}</strong>
                <span>{isAdmin ? 'Admin' : 'Member'}</span>
              </span>
            )}
          </div>
        )}
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
