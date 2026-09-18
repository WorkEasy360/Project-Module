import { useCallback, useEffect, useRef, useState } from 'react'
import { toUserMessage } from '../api/errorMessage'

export interface AsyncDataState<T> {
  data: T | null
  loading: boolean
  error: string | null
  reload: () => void
}

/**
 * Loads data from an async source (typically an API call) on mount and whenever `deps` change.
 * Exposes loading/error/data plus a manual `reload` — the standard shape every list/detail page
 * in this app uses so loading/empty/error states stay consistent.
 */
export function useAsyncData<T>(load: () => Promise<T>, deps: React.DependencyList): AsyncDataState<T> {
  const [data, setData] = useState<T | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [reloadToken, setReloadToken] = useState(0)
  const loadRef = useRef(load)

  useEffect(() => {
    loadRef.current = load
  })

  useEffect(() => {
    let cancelled = false
    setLoading(true)
    setError(null)
    loadRef
      .current()
      .then((result) => {
        if (!cancelled) setData(result)
      })
      .catch((err: unknown) => {
        if (!cancelled) setError(toUserMessage(err))
      })
      .finally(() => {
        if (!cancelled) setLoading(false)
      })
    return () => {
      cancelled = true
    }
  }, [...deps, reloadToken])

  const reload = useCallback(() => setReloadToken((t) => t + 1), [])

  return { data, loading, error, reload }
}
