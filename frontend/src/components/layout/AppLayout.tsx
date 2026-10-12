import { Navigate, Outlet, useLocation } from 'react-router-dom'
import { Sidebar } from './Sidebar'
import { Header } from './Header'
import { AccessEndedScreen } from './AccessEndedScreen'
import { SidebarProvider } from '../../context/SidebarContext'
import { useSession } from '../../context/SessionContext'
import { LoadingState } from '../common/LoadingState'

/**
 * Application shell and sign-in guard.
 *
 * - While the session is being checked: a neutral loading state (never a flash of the app or of
 *   the sign-in page).
 * - Not signed in: redirect to the sign-in page, carrying the route that was asked for so the
 *   sign-in can bring the person back to it.
 * - Signed in, but Workeasy360 says access has ended (or cannot be confirmed): a full screen that
 *   replaces the app, because every request would be refused anyway.
 * - Signed in, subscription read-only: the app, with a dismissible notice.
 */
export function AppLayout() {
  const { status, accessProblem, dismissAccessProblem } = useSession()
  const location = useLocation()

  if (status === 'loading') {
    return (
      <div className="app-loading">
        <LoadingState label="Checking your sign-in…" />
      </div>
    )
  }

  if (status === 'unauthenticated') {
    return <Navigate to="/sign-in" replace state={{ from: `${location.pathname}${location.search}` }} />
  }

  if (accessProblem && accessProblem.code !== 'workeasy360_read_only') {
    return <AccessEndedScreen problem={accessProblem} />
  }

  return (
    <SidebarProvider>
      <div className="app-shell">
        <Sidebar />
        <div className="app-main">
          <Header />
          {accessProblem && (
            <div className="banner banner-warning app-access-banner" role="status">
              <span>{accessProblem.message}</span>
              <button type="button" className="btn btn-sm" onClick={dismissAccessProblem}>
                Dismiss
              </button>
            </div>
          )}
          <main className="app-content">
            <Outlet />
          </main>
        </div>
      </div>
    </SidebarProvider>
  )
}
