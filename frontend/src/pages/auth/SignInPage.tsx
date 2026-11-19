import { useEffect, useRef, useState } from 'react'
import { Link, Navigate, useLocation, useNavigate, useParams, useSearchParams } from 'react-router-dom'
import { useIdentity } from '../../context/IdentityContext'
import { forgetEarlierSignIn, getEarlierSignIn } from '../../context/identityStore'
import { useSession } from '../../context/SessionContext'
import { useSignOutActions } from '../../hooks/useSignOutActions'
import { authApi, type Session } from '../../api/auth'
import { ApiError } from '../../api/client'
import { toUserMessage } from '../../api/errorMessage'
import { safeNextRoute, workeasy360StartUrl } from '../../api/session'
import { ssoErrorMessage } from '../../api/sessionMessages'
import { useSignInOptions } from '../../api/signInOptions'
import { Icon, type IconName } from '../../components/common/Icon'
import { LoadingState } from '../../components/common/LoadingState'
import { BRAND_NAME, BrandMark, BrandName, OperationsBadge } from '../../components/layout/Brand'
import '../../styles/signin.css'
import '../../styles/ops.css'
import '../../styles/signin-ops.css'
import '../../styles/signin-art.css'

/** The small part of Google's sign-in script this page uses. */
interface GoogleIdentity {
  accounts: {
    id: {
      initialize: (options: { client_id: string; callback: (response: { credential: string }) => void }) => void
      renderButton: (parent: HTMLElement, options: Record<string, unknown>) => void
    }
  }
}

const EMAIL_RE = /^[^\s@]+@[^\s@]+\.[^\s@]+$/
const MIN_PASSWORD_LENGTH = 8

/** The backend's own wording is written for this form; a 401 or 409 here is not a session problem. */
function authErrorMessage(error: unknown): string {
  if (error instanceof ApiError && !error.problem?.errors?.length && error.problem?.detail) {
    return error.problem.detail
  }
  return toUserMessage(error)
}

/**
 * Sign-in. Which page depends on how this deployment signs people in (`GET /api/v1/auth/config`):
 *
 * - **Email sign-in** (always, unless Workeasy360 is the only way in): email and password, or create an
 *   account; "Forgot Password?" and "Sign in with Google" depend on this deployment's email and Google
 *   client id. "Continue with WorkEasy360 Account" is always on the login card: with Workeasy360 sign-in
 *   configured it starts it (and it is offered on the registration form too); without it, it goes to
 *   `workeasy360SignInUrl` when that is set, or explains that it is not set up yet.
 * - **Workeasy360 only** (`sso_only`): only "Continue with WorkEasy360 Account".
 *
 * With Workeasy360 sign-in configured, "Continue with WorkEasy360 Account" is a plain link (a top-level
 * navigation to the backend, which performs the OIDC redirect) and is never followed automatically; the
 * person always chooses. This route is outside the guard that may start sign-in by itself
 * (`SignedOutGate`), so it never does, whatever its query. The route the person was trying to reach (set
 * by `AppLayout`) travels as `next` and the backend returns them to it. If a Workeasy360 sign-in fails,
 * the backend sends the browser back here with `?sso_error=<code>`, shown in plain language.
 */
export function SignInPage({
  successDelayMs = 1100,
  initialMode = 'sign-in',
}: {
  successDelayMs?: number
  /** 'register' opens straight on account creation (the #/sign-up address). */
  initialMode?: 'sign-in' | 'register'
}) {
  const options = useSignInOptions()
  // Until the options are known, the email page (the usual one) is shown, so nothing flashes for it.
  if (options?.ssoOnly) return <Workeasy360OnlySignIn />
  return <EmailSignIn successDelayMs={successDelayMs} initialMode={initialMode} workeasy360={options?.workeasy360 === true} />
}

function EmailSignIn({
  successDelayMs,
  initialMode,
  workeasy360,
}: {
  successDelayMs: number
  initialMode: 'sign-in' | 'register'
  workeasy360: boolean
}) {
  const { identity, setIdentity } = useIdentity()
  const options = useSignInOptions()
  const { inviteToken, resetToken } = useParams<{ inviteToken: string; resetToken: string }>()
  const navigate = useNavigate()
  const location = useLocation()
  const [params] = useSearchParams()
  const from = (location.state as { from?: string } | null)?.from
  const ssoError = params.get('sso_error')
  // Someone who used the app before email sign-in still has their old ids in this browser.
  const [earlier] = useState(() => (inviteToken || resetToken ? null : getEarlierSignIn()))
  const [mode, setMode] = useState<'sign-in' | 'register' | 'forgot' | 'handover'>(
    inviteToken ? 'register' : earlier ? 'handover' : initialMode,
  )
  const [notice, setNotice] = useState<string | null>(null)
  const config = options?.config ?? null
  const googleButtonRef = useRef<HTMLDivElement>(null)
  const [inviteInvalid, setInviteInvalid] = useState(false)
  const [name, setName] = useState('')
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  // The plain login starts unticked, as in the brand artwork; joining or creating an account keeps you signed in.
  const [remember, setRemember] = useState(() => Boolean(inviteToken) || initialMode === 'register' || earlier !== null)
  const [showPassword, setShowPassword] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [phase, setPhase] = useState<'idle' | 'checking' | 'success' | 'shake'>('idle')

  const handingOver = mode === 'handover' && earlier !== null
  // Moving over creates an account, so it asks for the same details as registering.
  const registering = mode === 'register' || handingOver
  const forgetting = mode === 'forgot'

  function enterWorkspace(session: Session) {
    // The phase moves first: an identity while idle would redirect straight past the animation.
    setPhase('success')
    setIdentity(
      {
        userId: session.account.id,
        orgId: session.account.organizationId,
        token: session.token,
        email: session.account.email,
        name: session.account.name,
      },
      remember,
    )
    window.setTimeout(() => navigate(from && from !== '/sign-in' ? from : '/dashboard', { replace: true }), successDelayMs)
  }

  // This server no longer moves earlier sign-ins: offer the ordinary sign-in instead.
  const legacyHandover = config?.legacyHandover
  useEffect(() => {
    if (legacyHandover === false) setMode((m) => (m === 'handover' ? 'sign-in' : m))
  }, [legacyHandover])

  const googleClientId = config?.googleClientId
  useEffect(() => {
    if (!googleClientId || inviteToken || resetToken) return
    const render = () => {
      const google = (window as unknown as { google?: GoogleIdentity }).google
      if (!google || !googleButtonRef.current) return
      google.accounts.id.initialize({
        client_id: googleClientId,
        callback: (response) => {
          authApi
            .google({ credential: response.credential, rememberMe: true })
            .then(enterWorkspace)
            .catch((err: unknown) => fail(authErrorMessage(err)))
        },
      })
      google.accounts.id.renderButton(googleButtonRef.current, { theme: 'outline', size: 'large', width: 340 })
    }
    const script = document.createElement('script')
    script.src = 'https://accounts.google.com/gsi/client'
    script.async = true
    script.onload = render
    document.head.appendChild(script)
    return () => script.remove()
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [googleClientId, inviteToken, resetToken])

  // An invitation link fixes the address the new account will use.
  useEffect(() => {
    if (!inviteToken) return
    authApi
      .previewInvitation(inviteToken)
      .then((invitation) => setEmail(invitation.email))
      .catch((err: unknown) => {
        setInviteInvalid(true)
        setError(authErrorMessage(err))
      })
  }, [inviteToken])

  // Already signed in (and not mid-verification): go straight to the workspace. An invitation is
  // the exception, since whoever opened it may be joining with a new account on this browser. So is a
  // Workeasy360 sign-in that failed while another one is still open (a launcher sign-in for another
  // organization): the person is still in their previous organization, so say what happened.
  if (identity && phase === 'idle' && !inviteToken && !resetToken) {
    if (ssoError && identity.provider === 'workeasy360') {
      return (
        <SignInFrame>
          <div className="si-card">
            <CardBrand />
            <h2 id="si-title">Welcome back</h2>
            <StillSignedIn ssoError={ssoError} next={safeNextRoute(from)} />
          </div>
        </SignInFrame>
      )
    }
    return <Navigate to={safeNextRoute(from)} replace />
  }

  async function handleSubmit(event: React.FormEvent) {
    event.preventDefault()
    setNotice(null)
    if (resetToken) {
      await submitReset(resetToken)
      return
    }
    const trimmedEmail = email.trim()
    if (forgetting) {
      await submitForgot(trimmedEmail)
      return
    }
    if (registering && !name.trim()) {
      fail('Please enter your name.')
      return
    }
    if (!EMAIL_RE.test(trimmedEmail)) {
      fail('Please enter a valid email address.')
      return
    }
    if (!password) {
      fail('Please enter your password.')
      return
    }
    if (registering && password.length < MIN_PASSWORD_LENGTH) {
      fail(`Your password must be at least ${MIN_PASSWORD_LENGTH} characters.`)
      return
    }
    setPhase('checking')
    setError(null)
    try {
      const session = inviteToken
        ? await authApi.acceptInvitation({ token: inviteToken, name: name.trim(), password, rememberMe: remember })
        : handingOver && earlier
          ? await authApi.claimEarlierSignIn({
              userId: earlier.userId,
              organizationId: earlier.orgId,
              name: name.trim(),
              email: trimmedEmail,
              password,
              rememberMe: remember,
            })
        : registering
          ? await authApi.register({ name: name.trim(), email: trimmedEmail, password, rememberMe: remember })
          : await authApi.login({ email: trimmedEmail, password, rememberMe: remember })
      enterWorkspace(session)
    } catch (err) {
      setPassword('')
      if (handingOver && err instanceof ApiError && err.status === 409) {
        // This workspace was already moved over (perhaps on another device): just sign in.
        setMode('sign-in')
        setPhase('idle')
        setError(null)
        setNotice('Your workspace already has an email sign-in. Sign in with that email and password.')
        return
      }
      fail(authErrorMessage(err))
    }
  }

  function startFresh() {
    forgetEarlierSignIn()
    setMode('register')
    setError(null)
    setNotice(null)
    setPassword('')
  }

  async function submitForgot(address: string) {
    if (!EMAIL_RE.test(address)) {
      fail('Please enter a valid email address.')
      return
    }
    setPhase('checking')
    setError(null)
    try {
      await authApi.forgotPassword(address)
      setNotice('If that address has an account, a reset link is on its way. Check your email.')
      setMode('sign-in')
      setPhase('idle')
    } catch (err) {
      fail(authErrorMessage(err))
    }
  }

  async function submitReset(token: string) {
    if (password.length < MIN_PASSWORD_LENGTH) {
      fail(`Your password must be at least ${MIN_PASSWORD_LENGTH} characters.`)
      return
    }
    setPhase('checking')
    setError(null)
    try {
      await authApi.resetPassword({ token, newPassword: password })
      setPassword('')
      setNotice('Your password has been changed. Sign in with your new password.')
      setPhase('idle')
      navigate('/sign-in', { replace: true })
    } catch (err) {
      fail(authErrorMessage(err))
    }
  }

  function switchMode() {
    setMode(registering || forgetting ? 'sign-in' : 'register')
    setError(null)
    setNotice(null)
    setPassword('')
  }

  function fail(message: string) {
    setError(message)
    setPhase('shake')
    window.setTimeout(() => setPhase((p) => (p === 'shake' ? 'idle' : p)), 500)
  }

  function forgotPassword() {
    setError(null)
    if (config?.emailEnabled) {
      setMode('forgot')
      setNotice(null)
    } else {
      setNotice('Ask your workspace admin for a password reset link. They can create one from My Team.')
    }
  }

  function continueWithWorkEasy360() {
    if (config?.workeasy360SignInUrl) {
      window.location.href = config.workeasy360SignInUrl
    } else {
      setError(null)
      setNotice('Signing in with a WorkEasy360 account is not set up for this workspace yet. Please log in with your email and password.')
    }
  }

  const plainLogin = mode === 'sign-in' && !inviteToken && !resetToken
  // With Workeasy360 sign-in configured it is offered on the registration form too (not on the one-purpose
  // ones: an invitation or reset link, a forgotten password, the hand-over of an earlier sign-in).
  const offerWorkeasy360OnRegister = workeasy360 && registering && !inviteToken && !resetToken && !handingOver

  return (
    <SignInFrame>
      <form className={`si-card${phase === 'shake' ? ' shake' : ''}`} onSubmit={handleSubmit} noValidate>
        <CardBrand />
        <h2 id="si-title">
          {resetToken
            ? 'Choose a new password'
            : forgetting
              ? 'Reset your password'
              : inviteToken
                ? "You've been invited"
                : handingOver
                  ? 'Welcome back! One quick step'
                  : registering
                    ? 'Create your account'
                    : 'Operations Login'}
        </h2>
        <p className="si-form-sub">
          {resetToken
            ? 'This will sign you out everywhere else'
            : forgetting
              ? "We'll email you a link to set a new one"
              : inviteToken
                ? <>Set up your account to join your team on {BRAND_NAME}</>
                : handingOver
                  ? 'We now use email sign-in. Choose the email and password you will use from now on. All your projects and tasks stay exactly as they are.'
                  : registering
                    ? <>Get started with {BRAND_NAME}</>
                    : <>Manage projects, tasks and teams<br />in one place.</>}
        </p>

        {ssoError && (
          <div className="banner banner-error" role="alert">
            {ssoErrorMessage(ssoError)}
          </div>
        )}

        {error && (
          <div className="banner banner-error" role="alert">
            {error}
          </div>
        )}

        {notice && (
          <div className="banner banner-info" role="status">
            {notice}
          </div>
        )}

        {registering && !resetToken && (
          <label className="si-field">
            <span className="si-label">Full name</span>
            <span className="si-input">
              <Icon name="user" size={22} />
              <input
                type="text"
                value={name}
                onChange={(e) => setName(e.target.value)}
                placeholder="Enter your name"
                autoComplete="name"
                maxLength={120}
                required
              />
            </span>
          </label>
        )}
        {!resetToken && (
          <label className="si-field">
            <span className="si-label">Email Address</span>
            <span className="si-input">
              <Icon name="mail" size={22} />
              <input
                type="email"
                value={email}
                onChange={(e) => setEmail(e.target.value)}
                placeholder="Enter your email"
                autoComplete="username"
                spellCheck={false}
                readOnly={Boolean(inviteToken)}
                required
              />
            </span>
          </label>
        )}
        {!forgetting && (
          <label className="si-field">
            <span className="si-label">{resetToken ? 'New password' : 'Password'}</span>
            <span className="si-input">
              <Icon name="lock" size={22} />
              <input
                type={showPassword ? 'text' : 'password'}
                value={password}
                onChange={(e) => setPassword(e.target.value)}
                placeholder={registering || resetToken ? `At least ${MIN_PASSWORD_LENGTH} characters` : 'Enter your password'}
                autoComplete={registering || resetToken ? 'new-password' : 'current-password'}
                required
              />
              <button
                type="button"
                className="si-eye"
                onClick={() => setShowPassword((v) => !v)}
                aria-label={showPassword ? 'Hide password' : 'Show password'}
              >
                <Icon name={showPassword ? 'eyeOff' : 'eye'} size={22} />
              </button>
            </span>
          </label>
        )}

        {!forgetting && !resetToken && (
          <div className="si-row">
            <label className="checkbox-row">
              <input type="checkbox" checked={remember} onChange={(e) => setRemember(e.target.checked)} />
              Remember me
            </label>
            {mode === 'sign-in' ? (
              <button type="button" className="si-switch si-forgot" onClick={forgotPassword}>
                Forgot Password?
              </button>
            ) : (
              <span className="si-hint">{remember ? 'Kept on this device' : 'This tab only'}</span>
            )}
          </div>
        )}

        <button
          type="submit"
          className="btn btn-primary si-submit"
          disabled={phase === 'checking' || phase === 'success' || inviteInvalid}
        >
          {phase === 'checking' ? (
            <>
              <span className="spinner" style={{ width: 16, height: 16, borderWidth: 2 }} />{' '}
              {resetToken || forgetting ? 'Sending…' : handingOver ? 'Saving…' : registering ? 'Creating your account…' : 'Logging in…'}
            </>
          ) : (
            <>
              {!resetToken && !forgetting && !registering && <Icon name="login" size={24} />}
              {resetToken ? 'Set new password' : forgetting ? 'Send reset link' : inviteToken ? 'Join workspace' : handingOver ? 'Keep my workspace' : registering ? 'Create account' : 'Log In to Operations'}
              {(resetToken || forgetting || registering) && <Icon name="arrowRight" size={16} />}
            </>
          )}
        </button>

        {(plainLogin || offerWorkeasy360OnRegister) && (
          <>
            <div className="si-or" aria-hidden="true">
              <span>OR</span>
            </div>
            {workeasy360 ? (
              <Workeasy360Choice next={safeNextRoute(from)} />
            ) : (
              <button type="button" className="si-sso" onClick={continueWithWorkEasy360}>
                <BrandMark size={30} />
                Continue with WorkEasy360 Account
              </button>
            )}
          </>
        )}

        {plainLogin && (
          <>
            {googleClientId && (
              <div className="si-google">
                <div ref={googleButtonRef} />
              </div>
            )}
            <ul className="si-tiles" aria-hidden="true">
              <li>
                <ArtIcon name="clipboard" size={44} />
                <span>Manage<br />Projects</span>
              </li>
              <li>
                <ArtIcon name="checkCircle" size={44} />
                <span>Track<br />Tasks</span>
              </li>
              <li>
                <ArtIcon name="people" size={44} />
                <span>Collaborate<br />with Teams</span>
              </li>
            </ul>
          </>
        )}

        {handingOver && (
          <p className="si-foot">
            Already did this?{' '}
            <button type="button" className="si-switch" onClick={() => { setMode('sign-in'); setError(null); setPassword('') }}>
              Sign in
            </button>
            {' · '}
            <button type="button" className="si-switch" onClick={startFresh}>
              Start a new workspace instead
            </button>
          </p>
        )}

        {!inviteToken && !resetToken && !handingOver && !plainLogin && (
          <p className="si-foot">
            {forgetting ? 'Remembered it?' : registering ? 'Already have an account?' : 'New here?'}{' '}
            <button type="button" className="si-switch" onClick={switchMode}>
              {registering || forgetting ? 'Log in' : 'Create an account'}
            </button>
          </p>
        )}

        {phase === 'success' && (
          <div className="si-success" role="status">
            <span className="si-check">
              <Icon name="check" size={34} />
            </span>
            <strong>Welcome to {BRAND_NAME}</strong>
            <span>Opening your workspace…</span>
          </div>
        )}
      </form>
    </SignInFrame>
  )
}

/**
 * "Continue with WorkEasy360 Account" when Workeasy360 sign-in is configured: a plain link to the backend,
 * which starts it. While the Workeasy360 session is being checked there is nothing to choose yet; a
 * session check that could not reach the server says so.
 */
function Workeasy360Choice({ next }: { next: string }) {
  const { status, loadError } = useSession()
  return (
    <>
      {loadError && (
        <div className="banner banner-error" role="alert">
          {loadError}
        </div>
      )}
      {status === 'loading' ? (
        <LoadingState label="Checking your sign-in…" />
      ) : (
        <a className="si-sso" href={workeasy360StartUrl(next)}>
          <BrandMark size={30} />
          Continue with WorkEasy360 Account
        </a>
      )}
    </>
  )
}

/**
 * A deployment where Workeasy360 is the only way in (`sso_only`): there is no password of this app's own
 * there, so the only choice is "Continue with WorkEasy360 Account".
 */
function Workeasy360OnlySignIn() {
  const { status, loadError } = useSession()
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
    <SignInFrame>
      <div className="si-card">
        <CardBrand />
        <h2 id="si-title">Operations Login</h2>
        <p className="si-form-sub">Sign in to continue to {BRAND_NAME}</p>

        {stillSignedIn && ssoError ? (
          <StillSignedIn ssoError={ssoError} next={next} />
        ) : (
          <>
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
            {status === 'loading' ? (
              <LoadingState label="Checking your sign-in…" />
            ) : (
              <div className="si-sso-panel">
                <h3 className="si-sso-title">Already a Workeasy360 user?</h3>
                <p className="si-sso-text">
                  {BRAND_NAME} uses your Workeasy360 account. There is no separate password to remember.
                </p>
                <a className="si-sso" href={workeasy360StartUrl(next)}>
                  <BrandMark size={30} />
                  Continue with WorkEasy360 Account
                </a>
              </div>
            )}
          </>
        )}

        <p className="si-foot">
          You will go to Workeasy360 to sign in, then come straight back here.
        </p>
      </div>
    </SignInFrame>
  )
}

/** A Workeasy360 sign-in failed, but the person is still signed in (to their previous organization). */
function StillSignedIn({ ssoError, next }: { ssoError: string; next: string }) {
  const signOuts = useSignOutActions()
  return (
    <>
      <div className="banner banner-error" role="alert">
        {ssoErrorMessage(ssoError)}
      </div>
      <div className="si-sso-panel">
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
    </>
  )
}

/** The logo and the Operations badge at the top of every sign-in card. */
function CardBrand() {
  return (
    <div className="si-card-brand">
      <span className="si-brand-logo si-brand-logo-dark">
        <BrandMark size={44} />
        <BrandName />
      </span>
      <span className="si-card-brand-sep" aria-hidden="true" />
      <OperationsBadge size="md" />
    </div>
  )
}

/** The brand panel (as in the brand artwork) and the form column every variant of the page shares. */
function SignInFrame({ children }: { children: React.ReactNode }) {
  return (
    <div className="si-page si-ops">
      <section className="si-brand" aria-label={`About ${BRAND_NAME}`}>
        <div className="si-brand-top">
          <span className="si-brand-logo">
            <BrandMark size={56} />
            <BrandName />
          </span>
          <OperationsBadge size="lg" />
        </div>
        <h1 className="si-headline">
          WorkEasy <span>Operations</span>
        </h1>
        <p className="si-tagline">Plan • Assign • Track • Get Work Done</p>
        <p className="si-sub">
          A simple workspace to manage projects, tasks and teams <span className="si-sub-end">for your business.</span>
        </p>
        <div className="si-showcase">
          <ul className="si-features">
            <Feature icon="clipboard" title="Project Management" text="Organize and track projects with your team" />
            <Feature icon="checklist" title="Task Management" text="Create, assign and track tasks with deadlines" />
            <Feature icon="people" title="Team Collaboration" text="Work together, share updates and stay aligned" />
            <Feature icon="bars" title="Progress & Reports" text="Get clear visibility on team and project performance" />
          </ul>
          <LaptopPreview />
        </div>
        <ul className="si-checks" aria-label="Included">
          {['Projects', 'Tasks', 'Teams', 'Automation', 'Reports', 'AI Assistant'].map((item) => (
            <li key={item}>
              <span className="si-check-dot" aria-hidden="true">
                <Icon name="check" size={18} />
              </span>
              {item}
            </li>
          ))}
        </ul>
      </section>

      <section className="si-form-wrap" aria-labelledby="si-title">
        {children}
      </section>
    </div>
  )
}

type ArtIconName = 'clipboard' | 'checklist' | 'people' | 'bars' | 'checkCircle'

/** Line icons drawn like the ones in the brand artwork (fuller than the app's small icons). */
function ArtIcon({ name, size }: { name: ArtIconName; size: number }) {
  const common = {
    width: size,
    height: size,
    viewBox: '0 0 48 48',
    fill: 'none',
    stroke: 'currentColor',
    strokeWidth: 3.2,
    strokeLinecap: 'round' as const,
    strokeLinejoin: 'round' as const,
    'aria-hidden': true,
  }
  switch (name) {
    case 'clipboard':
      return (
        <svg {...common}>
          <path d="M17 8h-5a3 3 0 0 0-3 3v29a3 3 0 0 0 3 3h24a3 3 0 0 0 3-3V11a3 3 0 0 0-3-3h-5" />
          <rect x="17" y="4.5" width="14" height="7" rx="2" />
          <path d="M16 21h16M16 28h16M16 35h10" />
        </svg>
      )
    case 'checklist':
      return (
        <svg {...common}>
          <path d="M6 11l3.5 3.5L16 8M6 24l3.5 3.5L16 21M6 37l3.5 3.5L16 34" />
          <path d="M22 12h20M22 25h20M22 38h20" />
        </svg>
      )
    case 'people':
      return (
        <svg {...common}>
          <circle cx="24" cy="15" r="6" />
          <circle cx="11" cy="19" r="4.5" />
          <circle cx="37" cy="19" r="4.5" />
          <path d="M14 40v-3a10 10 0 0 1 20 0v3z" />
          <path d="M3.5 38v-2a7.5 7.5 0 0 1 10-7M44.5 38v-2a7.5 7.5 0 0 0-10-7" />
        </svg>
      )
    case 'bars':
      return (
        <svg width={size} height={size} viewBox="0 0 48 48" fill="currentColor" aria-hidden="true">
          <rect x="6" y="27" width="9" height="15" rx="1.5" />
          <rect x="19.5" y="17" width="9" height="25" rx="1.5" />
          <rect x="33" y="6" width="9" height="36" rx="1.5" />
        </svg>
      )
    case 'checkCircle':
      return (
        <svg {...common}>
          <circle cx="24" cy="24" r="18" />
          <path d="M15.5 24.5l6 6 11-12" />
        </svg>
      )
  }
}

function Feature({ icon, title, text }: { icon: ArtIconName; title: string; text: string }) {
  return (
    <li>
      <span className="si-feature-icon">
        <ArtIcon name={icon} size={44} />
      </span>
      <span>
        <strong>{title}</strong>
        <span>{text}</span>
      </span>
    </li>
  )
}

/**
 * A drawn (not photographed) laptop showing the Operations home page, for the welcome panel.
 * Drawn to match the brand artwork: the lid is mapped onto the artwork's exact corners
 * (see .si-laptop-lid in signin-ops.css) and the keyboard deck is drawn as SVG below it.
 */
function LaptopPreview() {
  const nav: [string, IconName][] = [
    ['Home', 'home'],
    ['Projects', 'clipboard'],
    ['Tasks', 'tasks'],
    ['Team', 'team'],
    ['Reports', 'barChart'],
    ['Automation', 'automation'],
    ['Helpdesk', 'headset'],
    ['Settings', 'settings'],
  ]
  const bars: [string, number][] = [
    ['Website Redesign', 80],
    ['Product Launch', 60],
    ['Marketing Campaign', 40],
  ]
  return (
    <div className="si-laptop" aria-hidden="true">
      <svg className="si-laptop-deck" viewBox="0 0 740 480" preserveAspectRatio="none">
        <defs>
          <linearGradient id="si-deck-top" x1="0" y1="0" x2="0" y2="1">
            <stop offset="0" stopColor="#f4f6f8" />
            <stop offset="1" stopColor="#cdd2d8" />
          </linearGradient>
          <linearGradient id="si-deck-front" x1="0" y1="0" x2="0" y2="1">
            <stop offset="0" stopColor="#c3c8cf" />
            <stop offset="1" stopColor="#7d848d" />
          </linearGradient>
          <radialGradient id="si-deck-shadow">
            <stop offset="0" stopColor="rgba(0,0,0,0.55)" />
            <stop offset="1" stopColor="rgba(0,0,0,0)" />
          </radialGradient>
        </defs>
        <ellipse cx="365" cy="470" rx="380" ry="16" fill="url(#si-deck-shadow)" />
        <polygon points="92,415 720,412 726,430 690,453 4,461" fill="url(#si-deck-top)" />
        <polygon points="98,416 704,414 682,429 54,433" fill="#15181d" />
        <polygon points="58,431 680,428 676,431 50,435" fill="#2b3038" />
        <path d="M190,446 L290,444 Q288,452 282,453 L198,455 Q191,453 190,446 Z" fill="#c6cbd2" />
        <polygon points="4,461 690,453 682,468 14,474" fill="url(#si-deck-front)" />
        <polygon points="690,453 726,430 722,440 682,468" fill="#9aa1aa" />
        <rect x="640" y="458" width="10" height="3" rx="1" fill="#2e333a" />
        <rect x="656" y="457" width="10" height="3" rx="1" fill="#2e333a" />
        <rect x="672" y="456" width="6" height="3" rx="1" fill="#2e333a" />
      </svg>
      <div className="si-laptop-lid">
        <span className="si-laptop-camera" />
        <div className="si-mini">
          <header className="si-mini-top">
            <span className="si-mini-brand">
              <BrandMark size={22} /> <span>WorkEasy<b>360</b></span> Operations
            </span>
            <span className="si-mini-tools">
              <Icon name="help" size={15} />
              <Icon name="user" size={15} />
              <span className="si-mini-avatar">MR</span>
            </span>
          </header>
          <aside className="si-mini-side">
            {nav.map(([item, icon], i) => (
              <span key={item} className={`si-mini-link${i === 0 ? ' on' : ''}`}>
                <Icon name={icon} size={14} /> {item}
              </span>
            ))}
          </aside>
          <div className="si-mini-main">
            <div className="si-mini-stats">
              <span><small>My Tasks</small><b>12</b></span>
              <span><small>Due Today</small><b className="amber">3</b></span>
              <span><small>Overdue</small><b className="red">2</b></span>
              <span><small>Active Projects</small><b>8</b></span>
            </div>
            <div className="si-mini-row">
              <div className="si-mini-card">
                <small>Project Progress</small>
                {bars.map(([label, pct]) => (
                  <span key={label} className="si-mini-bar">
                    <i>{label}</i>
                    <em><u style={{ width: `${pct}%` }} /></em>
                    <i>{pct}%</i>
                  </span>
                ))}
              </div>
              <div className="si-mini-card si-mini-donut">
                <small>Tasks by Status</small>
                <span className="si-mini-ring"><b>24</b><i>Tasks</i></span>
              </div>
            </div>
            <div className="si-mini-card si-mini-quick">
              <small>Quick Actions</small>
              <div className="si-mini-actions">
                <span className="g"><Icon name="plus" size={22} />Add Task</span>
                <span className="b"><Icon name="folder" size={20} />Create Project</span>
                <span className="p"><Icon name="userPlus" size={20} />Invite Member</span>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>
  )
}
