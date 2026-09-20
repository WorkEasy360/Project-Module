import { useState } from 'react'
import { Navigate, useLocation, useNavigate } from 'react-router-dom'
import { useIdentity } from '../../context/IdentityContext'
import { dashboardApi } from '../../api/dashboard'
import { toUserMessage } from '../../api/errorMessage'
import { Icon } from '../../components/common/Icon'
import { BrandMark } from '../../components/layout/Sidebar'
import '../../styles/signin.css'

const UUID_RE = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i

/**
 * Sign-in. This backend has no username/password login: every request is authorised by the
 * X-User-Id / X-Org-Id headers a gateway would inject. So "signing in" here means providing
 * those two identifiers, which are then *verified against the real backend* (a dashboard read
 * with those headers must succeed) before the app opens. Nothing is simulated: an identity the
 * backend rejects (401/403) is not stored. There is no third-party SSO because no such backend
 * integration exists.
 */
export function SignInPage({ successDelayMs = 1100 }: { successDelayMs?: number }) {
  const { identity, setIdentity, clearIdentity } = useIdentity()
  const navigate = useNavigate()
  const location = useLocation()
  const from = (location.state as { from?: string } | null)?.from
  const [userId, setUserId] = useState('')
  const [orgId, setOrgId] = useState('')
  const [remember, setRemember] = useState(true)
  const [showIds, setShowIds] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [phase, setPhase] = useState<'idle' | 'checking' | 'success' | 'shake'>('idle')

  // Already signed in (and not mid-verification): go straight to the workspace.
  if (identity && phase === 'idle') {
    return <Navigate to={from && from !== '/sign-in' ? from : '/dashboard'} replace />
  }

  async function handleSubmit(event: React.FormEvent) {
    event.preventDefault()
    const u = userId.trim()
    const o = orgId.trim()
    if (!UUID_RE.test(u) || !UUID_RE.test(o)) {
      fail('Both IDs must be valid UUIDs (8-4-4-4-12 hexadecimal).')
      return
    }
    setPhase('checking')
    setError(null)
    // The API client reads identity from the store, so set it first, then prove it works.
    setIdentity({ userId: u, orgId: o }, remember)
    try {
      await dashboardApi.get()
      setPhase('success')
      window.setTimeout(() => navigate(from && from !== '/sign-in' ? from : '/dashboard', { replace: true }), successDelayMs)
    } catch (err) {
      clearIdentity()
      fail(`We couldn't verify this identity with the backend. ${toUserMessage(err)}`)
    }
  }

  function fail(message: string) {
    setError(message)
    setPhase('shake')
    window.setTimeout(() => setPhase((p) => (p === 'shake' ? 'idle' : p)), 500)
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
        <form className={`si-card${phase === 'shake' ? ' shake' : ''}`} onSubmit={handleSubmit} noValidate>
          <h2 id="si-title">Welcome back</h2>
          <p className="si-form-sub">Sign in to continue to WorkEasy360</p>

          <p className="si-note">
            This workspace identifies you by your <strong>User ID</strong> and <strong>Organization ID</strong>{' '}
            (sent as identity headers on every request). There is no password.
          </p>

          {error && (
            <div className="banner banner-error" role="alert">
              {error}
            </div>
          )}

          <label className="si-field">
            <span className="si-label">User ID</span>
            <span className="si-input">
              <Icon name="user" size={16} />
              <input
                type={showIds ? 'text' : 'password'}
                value={userId}
                onChange={(e) => setUserId(e.target.value)}
                placeholder="00000000-0000-0000-0000-000000000000"
                autoComplete="username"
                spellCheck={false}
                required
              />
            </span>
          </label>
          <label className="si-field">
            <span className="si-label">Organization ID</span>
            <span className="si-input">
              <Icon name="projects" size={16} />
              <input
                type={showIds ? 'text' : 'password'}
                value={orgId}
                onChange={(e) => setOrgId(e.target.value)}
                placeholder="00000000-0000-0000-0000-000000000000"
                spellCheck={false}
                required
              />
              <button
                type="button"
                className="si-eye"
                onClick={() => setShowIds((v) => !v)}
                aria-label={showIds ? 'Hide identifiers' : 'Show identifiers'}
              >
                <Icon name={showIds ? 'eyeOff' : 'eye'} size={16} />
              </button>
            </span>
          </label>

          <div className="si-row">
            <label className="checkbox-row">
              <input type="checkbox" checked={remember} onChange={(e) => setRemember(e.target.checked)} />
              Remember me
            </label>
            <span className="si-hint">{remember ? 'Kept on this device' : 'This tab only'}</span>
          </div>

          <button type="submit" className="btn btn-primary si-submit" disabled={phase === 'checking' || phase === 'success'}>
            {phase === 'checking' ? (
              <>
                <span className="spinner" style={{ width: 16, height: 16, borderWidth: 2 }} /> Verifying…
              </>
            ) : (
              <>
                Continue <Icon name="arrowRight" size={16} />
              </>
            )}
          </button>

          <p className="si-foot">
            Identity is verified against the backend before you enter. Nothing is stored unless it is accepted.
          </p>

          {phase === 'success' && (
            <div className="si-success" role="status">
              <span className="si-check">
                <Icon name="check" size={34} />
              </span>
              <strong>Welcome to WorkEasy360</strong>
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
            <stop offset="0" stopColor="#6366f1" />
            <stop offset="1" stopColor="#a855f7" />
          </linearGradient>
        </defs>
      </svg>
    </div>
  )
}
