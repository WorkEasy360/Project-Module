import { useEffect, useRef, useState } from 'react'
import { Navigate, useLocation, useNavigate, useParams } from 'react-router-dom'
import { useIdentity } from '../../context/IdentityContext'
import { forgetEarlierSignIn, getEarlierSignIn } from '../../context/identityStore'
import { authApi, type Session } from '../../api/auth'
import { ApiError } from '../../api/client'
import { toUserMessage } from '../../api/errorMessage'
import { Icon } from '../../components/common/Icon'
import { BRAND_NAME, BrandMark, BrandName } from '../../components/layout/Brand'
import '../../styles/signin.css'

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
  const [config, setConfig] = useState<{ googleClientId: string; emailEnabled: boolean; legacyHandover?: boolean } | null>(null)
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

  return (
    <div className="si-page">
      <section className="si-brand" aria-label={`About ${BRAND_NAME}`}>
        <div className="si-brand-head">
          <BrandMark size={40} />
          <div>
            <div className="si-brand-name">
              <BrandName />
            </div>
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
        <form className={`si-card${phase === 'shake' ? ' shake' : ''}`} onSubmit={handleSubmit} noValidate>
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
                    : 'Welcome back'}
          </h2>
          <p className="si-form-sub">
            {resetToken
              ? 'This will sign you out everywhere else'
              : forgetting
                ? "We'll email you a link to set a new one"
                : inviteToken
              ? <>Set up your account to join your team on <BrandName /></>
              : handingOver
                ? 'We now use email sign-in. Choose the email and password you will use from now on. All your projects and tasks stay exactly as they are.'
              : registering
                ? <>Get started with <BrandName /></>
                : <>Sign in to continue to <BrandName /></>}
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
                <Icon name="user" size={16} />
                <input
                  type="text"
                  value={name}
                  onChange={(e) => setName(e.target.value)}
                  placeholder="Your name"
                  autoComplete="name"
                  maxLength={120}
                  required
                />
              </span>
            </label>
          )}
          {!resetToken && (
          <label className="si-field">
            <span className="si-label">Email</span>
            <span className="si-input">
              <Icon name="user" size={16} />
              <input
                type="email"
                value={email}
                onChange={(e) => setEmail(e.target.value)}
                placeholder="you@company.com"
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
              <Icon name="settings" size={16} />
              <input
                type={showPassword ? 'text' : 'password'}
                value={password}
                onChange={(e) => setPassword(e.target.value)}
                placeholder={registering || resetToken ? `At least ${MIN_PASSWORD_LENGTH} characters` : 'Your password'}
                autoComplete={registering || resetToken ? 'new-password' : 'current-password'}
                required
              />
              <button
                type="button"
                className="si-eye"
                onClick={() => setShowPassword((v) => !v)}
                aria-label={showPassword ? 'Hide password' : 'Show password'}
              >
                <Icon name={showPassword ? 'eyeOff' : 'eye'} size={16} />
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
            {mode === 'sign-in' && config?.emailEnabled ? (
              <button type="button" className="si-switch si-forgot" onClick={() => { setMode('forgot'); setError(null); setNotice(null) }}>
                Forgot password?
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
                {resetToken || forgetting ? 'Sending…' : handingOver ? 'Saving…' : registering ? 'Creating your account…' : 'Signing in…'}
              </>
            ) : (
              <>
                {resetToken ? 'Set new password' : forgetting ? 'Send reset link' : inviteToken ? 'Join workspace' : handingOver ? 'Keep my workspace' : registering ? 'Create account' : 'Sign in'}{' '}
                <Icon name="arrowRight" size={16} />
              </>
            )}
          </button>

          {googleClientId && !inviteToken && !resetToken && !forgetting && !handingOver && (
            <div className="si-google">
              <span className="si-divider">or</span>
              <div ref={googleButtonRef} />
            </div>
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
              {forgetting ? 'Remembered it?' : registering ? 'Already have an account?' : <>New to <BrandName />?</>}{' '}
              <button type="button" className="si-switch" onClick={switchMode}>
                {registering || forgetting ? 'Sign in' : 'Create an account'}
              </button>
            </p>
          )}

          {phase === 'success' && (
            <div className="si-success" role="status">
              <span className="si-check">
                <Icon name="check" size={34} />
              </span>
              <strong>
                Welcome to <BrandName />
              </strong>
              <span>Opening your workspace…</span>
            </div>
          )}
        </form>
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
            <stop offset="0" stopColor="#00bf62" />
            <stop offset="1" stopColor="#ffc940" />
          </linearGradient>
        </defs>
      </svg>
    </div>
  )
}
