import { useState } from 'react'
import { useSession } from '../../context/SessionContext'
import { toUserMessage } from '../../api/errorMessage'
import type { AccessProblem } from '../../context/sessionStore'
import { BrandMark } from './Sidebar'
import '../../styles/signin.css'

/**
 * Full-screen replacement for the app when the organization's Easy Project access has ended or
 * cannot be confirmed. "Try again" drops the notice and re-checks the session; the app then
 * remounts and reloads its data, and this screen returns if the server still says no.
 */
export function AccessEndedScreen({ problem }: { problem: AccessProblem }) {
  const { dismissAccessProblem, refresh, signOut } = useSession()
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const unverified = problem.code === 'workeasy360_access_unverified'

  async function tryAgain() {
    setBusy(true)
    dismissAccessProblem()
    await refresh()
    setBusy(false)
  }

  async function handleSignOut() {
    setBusy(true)
    setError(null)
    try {
      await signOut()
    } catch (err) {
      setError(toUserMessage(err))
      setBusy(false)
    }
  }

  return (
    <div className="si-page si-page-single">
      <section className="si-form-wrap" aria-labelledby="access-ended-title">
        <div className="si-card">
          <div className="si-brand-head" style={{ justifyContent: 'center', marginBottom: 12 }}>
            <BrandMark size={36} />
          </div>
          <h1 id="access-ended-title" className="si-access-title">
            {unverified ? "Workeasy360 can't confirm access right now" : 'Easy Project access has ended'}
          </h1>
          <p className="si-form-sub" role="alert">
            {problem.message}
          </p>
          {error && (
            <div className="banner banner-error" role="alert">
              {error}
            </div>
          )}
          <div className="si-access-actions">
            <button type="button" className="btn btn-primary" onClick={tryAgain} disabled={busy}>
              Try again
            </button>
            <button type="button" className="btn" onClick={handleSignOut} disabled={busy}>
              Sign out
            </button>
          </div>
        </div>
      </section>
    </div>
  )
}
