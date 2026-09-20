import { useCallback, useEffect, useSyncExternalStore, type ReactNode } from 'react'
import {
  applyTheme,
  getThemePreference,
  resolveTheme,
  setThemePreference,
  subscribeTheme,
  type ResolvedTheme,
  type ThemePreference,
} from './themeStore'

interface ThemeState {
  preference: ThemePreference
  resolved: ResolvedTheme
  setPreference: (next: ThemePreference) => void
  /** Flips between light and dark (a "system" preference resolves first, then flips). */
  toggle: () => void
}

/**
 * Theme state lives in a module store (themeStore.ts), so `useTheme` works anywhere without a
 * provider. `ThemeProvider` only adds the OS-preference listener for the "system" setting.
 */
export function useTheme(): ThemeState {
  const preference = useSyncExternalStore(subscribeTheme, getThemePreference, () => 'system' as const)
  const resolved = resolveTheme(preference)
  const setPreference = useCallback((next: ThemePreference) => setThemePreference(next), [])
  const toggle = useCallback(
    () => setThemePreference(resolveTheme(getThemePreference()) === 'dark' ? 'light' : 'dark'),
    [],
  )
  return { preference, resolved, setPreference, toggle }
}

export function ThemeProvider({ children }: { children: ReactNode }) {
  const { preference } = useTheme()

  useEffect(() => {
    if (preference !== 'system' || typeof window.matchMedia !== 'function') return
    const mq = window.matchMedia('(prefers-color-scheme: dark)')
    const onChange = () => applyTheme('system')
    mq.addEventListener?.('change', onChange)
    return () => mq.removeEventListener?.('change', onChange)
  }, [preference])

  return <>{children}</>
}
