import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import '../../styles/signin.css'

/** How long the browser gets to leave before the screen offers a way out. */
export const STALLED_AFTER_MS = 5000

/**
 * The neutral screen while Easy Project hands the browser to Workeasy360 (automatic sign-in). It is not the
 * sign-in page: at first there is nothing to choose or type. One heading, text in the theme's own text
 * colours on the solid card colour (so contrast holds in both themes), and no animation on the text.
 *
 * If the browser has not left after a few seconds (a stalled or blocked navigation), it must not be a dead
 * end: the same start URL as a plain link, and a link to the sign-in page. `aria-busy` is true while waiting.
 */
export function SigningInScreen({ startUrl, from }: { startUrl: string; from: string }) {
  const [stalled, setStalled] = useState(false)

  useEffect(() => {
    const timer = window.setTimeout(() => setStalled(true), STALLED_AFTER_MS)
    return () => window.clearTimeout(timer)
  }, [])

  return (
    <main className="signing-in" aria-busy={!stalled}>
      <div className="signing-in-card">
        <span className="spinner" aria-hidden="true" />
        <h1 className="signing-in-title">Signing you in with Workeasy360…</h1>
        <p className="signing-in-text" role="status">
          {stalled ? 'This is taking longer than expected.' : 'One moment. Easy Project uses your Workeasy360 account.'}
        </p>
        {stalled && (
          <p className="signing-in-text signing-in-links">
            <a className="signing-in-link" href={startUrl}>
              Continue with Workeasy360
            </a>
            <Link className="signing-in-link" to="/sign-in" replace state={{ from }}>
              Go to the sign-in page
            </Link>
          </p>
        )}
      </div>
    </main>
  )
}
