import { Navigate, Outlet, useLocation } from 'react-router-dom'
import { Sidebar } from './Sidebar'
import { Header } from './Header'
import { SidebarProvider } from '../../context/SidebarContext'
import { useIdentity } from '../../context/IdentityContext'
import { PeopleProvider } from '../../context/PeopleContext'
import { AssistantPanel } from '../assistant/AssistantPanel'
import '../../styles/ops.css'
import { WorkspaceSettingsProvider } from '../../context/WorkspaceSettingsContext'

/**
 * Application shell. Until someone is signed in, the shell redirects to the sign-in page — there
 * is nothing useful to render without a session, since every API call would be rejected with 401.
 */
export function AppLayout() {
  const { isConfigured } = useIdentity()
  const location = useLocation()

  if (!isConfigured) {
    return <Navigate to="/sign-in" replace state={{ from: location.pathname }} />
  }

  return (
    <WorkspaceSettingsProvider>
    <PeopleProvider>
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
        <AssistantPanel />
      </SidebarProvider>
    </PeopleProvider>
    </WorkspaceSettingsProvider>
  )
}
