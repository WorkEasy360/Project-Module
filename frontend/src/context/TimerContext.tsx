import { createContext, useCallback, useContext, useEffect, useMemo, useState, type ReactNode } from 'react'
import { timeApi, type RunningTimer } from '../api/collaboration'

interface TimerContextValue {
  timer: RunningTimer | null
  start: (taskId: string) => Promise<void>
  /** Stops the timer and logs the time. */
  stop: () => Promise<void>
  cancel: () => Promise<void>
}

const FALLBACK: TimerContextValue = {
  timer: null,
  start: async () => undefined,
  stop: async () => undefined,
  cancel: async () => undefined,
}

const TimerContext = createContext<TimerContextValue>(FALLBACK)

/** The signed-in person's running timer. The server holds it, so it survives closing the browser. */
export function TimerProvider({ children }: { children: ReactNode }) {
  const [timer, setTimer] = useState<RunningTimer | null>(null)

  useEffect(() => {
    timeApi.currentTimer().then((current) => setTimer(current ?? null)).catch(() => undefined)
  }, [])

  const start = useCallback(async (taskId: string) => {
    setTimer(await timeApi.startTimer(taskId))
  }, [])
  const stop = useCallback(async () => {
    await timeApi.stopTimer()
    setTimer(null)
  }, [])
  const cancel = useCallback(async () => {
    await timeApi.cancelTimer()
    setTimer(null)
  }, [])

  const value = useMemo(() => ({ timer, start, stop, cancel }), [timer, start, stop, cancel])
  return <TimerContext.Provider value={value}>{children}</TimerContext.Provider>
}

export function useTimer(): TimerContextValue {
  return useContext(TimerContext)
}

/** A running time as 0:05:12, updated every second. */
export function useElapsed(startedAt: string | null): string {
  const [now, setNow] = useState(() => Date.now())
  useEffect(() => {
    if (!startedAt) return
    const tick = window.setInterval(() => setNow(Date.now()), 1000)
    return () => window.clearInterval(tick)
  }, [startedAt])
  if (!startedAt) return ''
  const seconds = Math.max(0, Math.floor((now - new Date(startedAt).getTime()) / 1000))
  const pad = (n: number) => String(n).padStart(2, '0')
  return `${Math.floor(seconds / 3600)}:${pad(Math.floor((seconds % 3600) / 60))}:${pad(seconds % 60)}`
}
