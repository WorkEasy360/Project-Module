import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useState,
  useSyncExternalStore,
  type ReactNode,
} from 'react'
import { useMediaQuery } from '../hooks/useMediaQuery'
import { getCollapsed, setCollapsed, subscribeCollapsed } from './sidebarStore'

const MOBILE_QUERY = '(max-width: 768px)'

interface SidebarContextValue {
  /** Mobile (off-canvas drawer) vs. desktop/tablet (docked, collapsible) layout mode. */
  isMobile: boolean
  /** Desktop/tablet only: sidebar shows icons only, persisted across visits. */
  collapsed: boolean
  /** Mobile only: whether the off-canvas drawer is open. */
  mobileOpen: boolean
  /** Hamburger button behavior: collapses/expands on desktop, opens/closes the drawer on mobile. */
  toggle: () => void
  closeMobile: () => void
}

const SidebarContext = createContext<SidebarContextValue | null>(null)

export function SidebarProvider({ children }: { children: ReactNode }) {
  const isMobile = useMediaQuery(MOBILE_QUERY)
  const collapsed = useSyncExternalStore(subscribeCollapsed, getCollapsed, () => false)
  const [mobileOpen, setMobileOpen] = useState(false)

  // Switching between mobile and desktop mid-session shouldn't leave a stale drawer open.
  useEffect(() => {
    if (!isMobile) setMobileOpen(false)
  }, [isMobile])

  // Escape closes the mobile drawer, matching every other overlay (Modal) in this app.
  useEffect(() => {
    if (!mobileOpen) return
    function onKeyDown(event: KeyboardEvent) {
      if (event.key === 'Escape') setMobileOpen(false)
    }
    document.addEventListener('keydown', onKeyDown)
    return () => document.removeEventListener('keydown', onKeyDown)
  }, [mobileOpen])

  const toggle = useCallback(() => {
    if (isMobile) {
      setMobileOpen((open) => !open)
    } else {
      setCollapsed(!getCollapsed())
    }
  }, [isMobile])

  const closeMobile = useCallback(() => setMobileOpen(false), [])

  const value: SidebarContextValue = { isMobile, collapsed, mobileOpen, toggle, closeMobile }

  return <SidebarContext.Provider value={value}>{children}</SidebarContext.Provider>
}

export function useSidebar(): SidebarContextValue {
  const ctx = useContext(SidebarContext)
  if (!ctx) throw new Error('useSidebar must be used within a SidebarProvider')
  return ctx
}
