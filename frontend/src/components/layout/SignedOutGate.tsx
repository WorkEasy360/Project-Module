import { useEffect, useState } from 'react'
import { Navigate } from 'react-router-dom'
import { useSession } from '../../context/SessionContext'
import { decideAutoStart, recordAutoStart } from '../../api/autoSignIn'
import { replaceTo, safeNextRoute, workeasy360StartUrl } from '../../api/session'
import { SigningInScreen } from './SigningInScreen'

/**
 * What a page that needs a signed-in person shows when the browser has no Easy Project session.
 *
 * Opening Easy Project directly, with no session of its own, goes straight to
 * `/api/auth/workeasy360/start?next=<this route>` (no organization hint): Workeasy360 completes it
 * silently when it has a session and the person's preference is Automatic, asks when it is "Ask me each
 * time", and shows its own sign-in otherwise. The person never sees Easy Project's sign-in page, and never
 * a password. Only when starting is not allowed (`decideAutoStart`: signed out of Easy Project in this
 * browser, started less than a minute ago, or the session could not be checked) does it send the person to
 * the sign-in page, which offers "Continue with Workeasy360" as a choice.
 *
 * Rendered by `AppLayout` only when the session check found no session. A session whose access ended is
 * never here: it keeps its own explanation screen.
 */
export function SignedOutGate({ from }: { from: string }) {
  const { loadError } = useSession()
  // Decided once, when the gate first appears: recording the start below must not flip the decision
  // (and send the person to the sign-in page) before the browser has left.
  const [decision] = useState(() => decideAutoStart(loadError !== null))
  const next = safeNextRoute(from)
  const startUrl = workeasy360StartUrl(next)
  const starting = decision === 'start'

  useEffect(() => {
    if (!starting) return
    recordAutoStart()
    // replace, not assign: Back from Workeasy360's page must not land on this screen and bounce forward again.
    replaceTo(startUrl)
  }, [starting, startUrl])

  if (!starting) {
    return <Navigate to="/sign-in" replace state={{ from }} />
  }
  return <SigningInScreen startUrl={startUrl} from={from} />
}
