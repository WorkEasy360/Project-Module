import { useEffect, useRef, useState } from 'react'
import { Navigate, useLocation, useNavigate, useParams } from 'react-router-dom'
import { useIdentity } from '../../context/IdentityContext'
import { forgetEarlierSignIn, getEarlierSignIn } from '../../context/identityStore'
import { authApi, type Session } from '../../api/auth'
import { ApiError } from '../../api/client'
import { toUserMessage } from '../../api/errorMessage'
import { Icon, type IconName } from '../../components/common/Icon'
import { BRAND_NAME, BrandMark, BrandName, OperationsBadge } from '../../components/layout/Brand'
import '../../styles/signin.css'
import '../../styles/ops.css'
import '../../styles/signin-ops.css'

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
 * Sign in with email and password, or create an account. The backend checks the password and
 * returns a session token; nothing is stored unless it accepts. "Forgot password" and "Sign in with
 * Google" appear only when this deployment has email and a Google client id configured.
 */
export function SignInPage({ successDelayMs = 1100 }: { successDelayMs?: number }) {
  const { identity, setIdentity } = useIdentity()
  const { inviteToken, resetToken } = useParams<{ inviteToken: string; resetToken: string }>()
  const navigate = useNavigate()
  const location = useLocation()
  const from = (location.state as { from?: string } | null)?.from
  // Someone who used the app before email sign-in still has their old ids in this browser.
  const [earlier] = useState(() => (inviteToken || resetToken ? null : getEarlierSignIn()))
  const [mode, setMode] = useState<'sign-in' | 'register' | 'forgot' | 'handover'>(
    inviteToken ? 'register' : earlier ? 'handover' : 'sign-in',
  )
  const [notice, setNotice] = useState<string | null>(null)
  const [config, setConfig] = useState<{ googleClientId: string; emailEnabled: boolean; legacyHandover?: boolean; workeasy360SignInUrl?: string } | null>(null)
  const googleButtonRef = useRef<HTMLDivElement>(null)
  const [inviteInvalid, setInviteInvalid] = useState(false)
  const [name, setName] = useState('')
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [remember, setRemember] = useState(true)
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

  // What this deployment offers: both of these depend on its configuration.
  useEffect(() => {
    authApi
      .config()
      .then((c) => {
        setConfig(c)
        // This server no longer moves earlier sign-ins: offer the ordinary sign-in instead.
        if (c.legacyHandover === false) setMode((m) => (m === 'handover' ? 'sign-in' : m))
      })
      .catch(() => undefined)
  }, [])

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
  // the exception, since whoever opened it may be joining with a new account on this browser.
  if (identity && phase === 'idle' && !inviteToken && !resetToken) {
    return <Navigate to={from && from !== '/sign-in' ? from : '/dashboard'} replace />
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
        <p className="si-sub">A simple workspace to manage projects, tasks and teams for your business.</p>
        <div className="si-showcase">
          <ul className="si-features">
            <Feature icon="clipboard" title="Project Management" text="Organize and track projects with your team" />
            <Feature icon="tasks" title="Task Management" text="Create, assign and track tasks with deadlines" />
            <Feature icon="team" title="Team Collaboration" text="Work together, share updates and stay aligned" />
            <Feature icon="barChart" title="Progress & Reports" text="Get clear visibility on team and project performance" />
          </ul>
          <LaptopPreview />
        </div>
        <ul className="si-checks" aria-label="Included">
          {['Projects', 'Tasks', 'Teams', 'Automation', 'Reports', 'AI Assistant'].map((item) => (
            <li key={item}>
              <Icon name="checkCircle" size={22} /> {item}
            </li>
          ))}
        </ul>
      </section>

      <section className="si-form-wrap" aria-labelledby="si-title">
        <form className={`si-card${phase === 'shake' ? ' shake' : ''}`} onSubmit={handleSubmit} noValidate>
          <div className="si-card-brand">
            <span className="si-brand-logo si-brand-logo-dark">
              <BrandMark size={44} />
              <BrandName />
            </span>
            <span className="si-card-brand-sep" aria-hidden="true" />
            <OperationsBadge size="md" />
          </div>
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

          {plainLogin && (
            <>
              <div className="si-or" aria-hidden="true">
                <span>OR</span>
              </div>
              <button type="button" className="si-sso" onClick={continueWithWorkEasy360}>
                <BrandMark size={30} />
                Continue with WorkEasy360 Account
              </button>
              {googleClientId && (
                <div className="si-google">
                  <div ref={googleButtonRef} />
                </div>
              )}
              <ul className="si-tiles" aria-hidden="true">
                <li>
                  <Icon name="clipboard" size={34} />
                  Manage Projects
                </li>
                <li>
                  <Icon name="checkCircle" size={34} />
                  Track Tasks
                </li>
                <li>
                  <Icon name="team" size={34} />
                  Collaborate with Teams
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

          {!inviteToken && !resetToken && !handingOver && (
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
      </section>
    </div>
  )
}

function Feature({ icon, title, text }: { icon: IconName; title: string; text: string }) {
  return (
    <li>
      <span className="si-feature-icon">
        <Icon name={icon} size={34} />
      </span>
      <span>
        <strong>{title}</strong>
        <span>{text}</span>
      </span>
    </li>
  )
}

/** A drawn (not photographed) laptop showing the Operations home page, for the welcome panel. */
function LaptopPreview() {
  const bars: [string, number][] = [
    ['Website Redesign', 80],
    ['Product Launch', 60],
    ['Marketing Campaign', 40],
  ]
  return (
    <div className="si-laptop" aria-hidden="true">
      <div className="si-laptop-screen">
        <div className="si-mini">
          <aside className="si-mini-side">
            <span className="si-mini-brand">
              <BrandMark size={14} /> WorkEasy<b>360</b> Operations
            </span>
            {['Home', 'Projects', 'Tasks', 'Team', 'Reports', 'Automation', 'Helpdesk', 'Settings'].map((item, i) => (
              <span key={item} className={`si-mini-link${i === 0 ? ' on' : ''}`}>
                {item}
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
            <div className="si-mini-actions">
              <span className="g">+ Add Task</span>
              <span className="b">Create Project</span>
              <span className="p">Invite Member</span>
            </div>
          </div>
        </div>
      </div>
      <div className="si-laptop-base" />
    </div>
  )
}
