import { Outlet, useLocation } from 'react-router-dom'
import { Sidebar } from './Sidebar'
import { Header } from './Header'
import { AccessEndedScreen } from './AccessEndedScreen'
import { SignedOutGate } from './SignedOutGate'
import { SidebarProvider } from '../../context/SidebarContext'
import { useIdentity } from '../../context/IdentityContext'
import { useSession } from '../../context/SessionContext'
import { PeopleProvider } from '../../context/PeopleContext'
import { TimerProvider } from '../../context/TimerContext'
import { AssistantPanel } from '../assistant/AssistantPanel'
import { LoadingState } from '../common/LoadingState'

/**
 * Application shell and sign-in guard. There is nothing useful to render without a session, since every
 * API call would be rejected with 401.
 *
 * - An email session (a token in this browser): the app, straight away.
 * - Otherwise the Workeasy360 session decides:
 *   - while it is being checked: a neutral loading state (never a flash of the app or of the sign-in page);
 *   - no session at all: `SignedOutGate`, which shows the sign-in page, or, on a deployment where
 *     Workeasy360 is the only way in, starts Workeasy360 sign-in straight away, carrying the route that was
 *     asked for so the person lands back on it (integration guide 6b);
 *   - signed in, but Workeasy360 says access has ended (or cannot be confirmed): a full screen that replaces
 *     the app, because every request would be refused anyway;
 *   - signed in, subscription read-only: the app, with a dismissible notice.
 */
export function AppLayout() {
  const { identity } = useIdentity()
  const { status, accessProblem, dismissAccessProblem } = useSession()
  const location = useLocation()
  const emailSession = Boolean(identity?.token)

  if (!emailSession) {
    if (status === 'loading') {
      return (
        <div className="app-loading">
          <LoadingState label="Checking your sign-in…" />
        </div>
      )
    }

    if (status === 'unauthenticated') {
      return <SignedOutGate from={`${location.pathname}${location.search}`} />
    }

    if (accessProblem && accessProblem.code !== 'workeasy360_read_only') {
      return <AccessEndedScreen problem={accessProblem} />
    }
  }

  // Only a Workeasy360 session can have an access problem; an email session never shows one.
  const notice = emailSession ? null : accessProblem

  return (
    <PeopleProvider>
      <TimerProvider>
        <SidebarProvider>
          <div className="app-shell">
            <Sidebar />
            <div className="app-main">
              <Header />
              {notice && (
                <div className="banner banner-warning app-access-banner" role="status">
                  <span>{notice.message}</span>
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
          <AssistantPanel />
        </SidebarProvider>
      </TimerProvider>
    </PeopleProvider>
  )
}
