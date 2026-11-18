import { useState } from 'react'
import { useSession } from '../context/SessionContext'
import { toUserMessage } from '../api/errorMessage'

/** Sign-out actions shared by the account dialog and the Settings page. */
export function useSignOutActions() {
  const { signOut, signOutEverywhere, workeasy360SignOut } = useSession()
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)

  async function run(action: () => Promise<void>) {
    setBusy(true)
    setError(null)
    try {
      await action()
    } catch (err) {
      setError(toUserMessage(err))
      setBusy(false)
    }
  }

  return {
    busy,
    error,
    workeasy360SignOut,
    signOut: () => run(signOut),
    signOutEverywhere: () => run(signOutEverywhere),
  }
}
