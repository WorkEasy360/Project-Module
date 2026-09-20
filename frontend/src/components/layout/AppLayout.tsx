import { Navigate, Outlet, useLocation } from 'react-router-dom'
import { Sidebar } from './Sidebar'
import { Header } from './Header'
import { SidebarProvider } from '../../context/SidebarContext'
import { useIdentity } from '../../context/IdentityContext'

/**
 * Application shell. Until an identity (the X-User-Id / X-Org-Id pair every request needs) is
 * configured, the shell redirects to the sign-in page — there is nothing useful to render
 * without it, since every API call would be rejected with 401.
 */
export function AppLayout() {
  const { isConfigured } = useIdentity()
  const location = useLocation()

  if (!isConfigured) {
    return <Navigate to="/sign-in" replace state={{ from: location.pathname }} />
  }

  return (
    <SidebarProvider>
      <div className="app-shell">
        <Sidebar />
        <div className="app-main">
          <Header />
          <main className="app-content">
            <Outlet />
          </main>
        </div>
      </div>
    </SidebarProvider>
  )
}
