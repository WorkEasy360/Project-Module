import { Link, Navigate, useLocation, useSearchParams } from 'react-router-dom'
import { useSession } from '../../context/SessionContext'
import { useSignOutActions } from '../../hooks/useSignOutActions'
import { safeNextRoute, workeasy360StartUrl } from '../../api/session'
import { ssoErrorMessage } from '../../api/sessionMessages'
import { Icon } from '../../components/common/Icon'
import { LoadingState } from '../../components/common/LoadingState'
import { BrandMark } from '../../components/layout/Sidebar'
import '../../styles/signin.css'

/**
 * Sign-in. Easy Project has no passwords or user ids of its own: people sign in with Workeasy360.
 * "Continue with Workeasy360" is a plain link — a top-level navigation to the backend, which
 * performs the OIDC redirect — and is never followed automatically; the person always chooses.
 * The route they were trying to reach (set by `AppLayout`) travels as `next` and the backend
 * returns them to it. If the sign-in fails, the backend sends the browser back here with
 * `?sso_error=<code>`, shown below in plain language.
 */
export function SignInPage() {
  const { status, loadError } = useSession()
  const signOuts = useSignOutActions()
  const location = useLocation()
  const [params] = useSearchParams()
  const from = (location.state as { from?: string } | null)?.from
  const next = safeNextRoute(from)
  const ssoError = params.get('sso_error')

  // Signed in and nothing to explain: straight into the app. Signed in *and* a failed sign-in to
  // report (a launcher sign-in for another organization that did not complete): the person is
  // still in their previous organization, so say what happened and let them choose.
  const stillSignedIn = status === 'authenticated'
  if (stillSignedIn && !ssoError) {
    return <Navigate to={next} replace />
  }

  return (
    <div className="si-page">
      <section className="si-brand" aria-label="About WorkEasy360">
        <div className="si-brand-head">
          <BrandMark size={40} />
          <div>
            <div className="si-brand-name">WorkEasy360</div>
            <div className="si-brand-tag">Plan · Collaborate · Achieve</div>
          </div>
        </div>
        <h1 className="si-headline">
          Turn Ideas into <span>Real Progress</span>
        </h1>
        <p className="si-sub">All your projects, tasks, and teams in one powerful workspace.</p>
        <FloatingScene />
        <p className="si-quote">&ldquo;Small steps make big progress.&rdquo;</p>
      </section>

      <section className="si-form-wrap" aria-labelledby="si-title">
        <div className="si-card">
          <h2 id="si-title">Welcome back</h2>
          <p className="si-form-sub">Sign in to continue to Easy Project</p>

          {ssoError && (
            <div className="banner banner-error" role="alert">
              {ssoErrorMessage(ssoError)}
            </div>
          )}
          {!ssoError && loadError && (
            <div className="banner banner-error" role="alert">
              {loadError}
            </div>
          )}

          {stillSignedIn ? (
            <div className="si-sso">
              <h3 className="si-sso-title">You are still signed in</h3>
              <p className="si-sso-text">
                The sign-in above did not complete, so you are still in your current organization.
              </p>
              <Link className="btn btn-primary si-submit" to={next} replace>
                Continue in current organization
              </Link>
              <button type="button" className="btn si-submit" onClick={signOuts.signOut} disabled={signOuts.busy}>
                Sign out
              </button>
              {signOuts.error && (
                <div className="banner banner-error" role="alert">
                  {signOuts.error}
                </div>
              )}
              <a className="si-sso-text" href={workeasy360StartUrl(next)}>
                Try signing in with Workeasy360 again
              </a>
            </div>
          ) : status === 'loading' ? (
            <LoadingState label="Checking your sign-in…" />
          ) : (
            <div className="si-sso">
              <h3 className="si-sso-title">Already a Workeasy360 user?</h3>
              <p className="si-sso-text">
                Easy Project uses your Workeasy360 account. There is no separate password to remember.
              </p>
              <a className="btn btn-primary si-submit" href={workeasy360StartUrl(next)}>
                Continue with Workeasy360 <Icon name="arrowRight" size={16} />
              </a>
            </div>
          )}

          <p className="si-foot">
            You will go to Workeasy360 to sign in, then come straight back here.
          </p>
        </div>
      </section>
    </div>
  )
}

/** Decorative, CSS-animated "floating cards" scene (no images, respects reduced motion). */
function FloatingScene() {
  return (
    <div className="si-scene" aria-hidden="true">
      <div className="si-folder">
        <div className="si-folder-tab" />
        <div className="si-folder-body" />
      </div>
      <div className="si-float si-float-1">
        <Icon name="check" size={14} /> Plan
      </div>
      <div className="si-float si-float-2">
        <Icon name="kanban" size={14} /> Build
      </div>
      <div className="si-float si-float-3">
        <Icon name="check" size={14} /> Deliver
      </div>
      <div className="si-chip si-chip-1">
        <Icon name="tasks" size={16} />
      </div>
      <div className="si-chip si-chip-2">
        <Icon name="calendar" size={16} />
      </div>
      <div className="si-chip si-chip-3">
        <Icon name="team" size={16} />
      </div>
      <svg className="si-path" viewBox="0 0 320 120" preserveAspectRatio="none">
        <path d="M5 95 C 80 20, 160 130, 315 30" fill="none" stroke="url(#si-grad)" strokeWidth="3" strokeLinecap="round" strokeDasharray="8 8" />
        <defs>
          <linearGradient id="si-grad" x1="0" x2="1">
            <stop offset="0" stopColor="#6366f1" />
            <stop offset="1" stopColor="#a855f7" />
          </linearGradient>
        </defs>
      </svg>
    </div>
  )
}
