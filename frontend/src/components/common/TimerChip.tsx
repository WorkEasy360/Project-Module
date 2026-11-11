import { useElapsed, useTimer } from '../../context/TimerContext'
import { Icon } from './Icon'

/** Shown in the header only while a timer is running, so it can be stopped from any screen. */
export function TimerChip() {
  const { timer, stop } = useTimer()
  const elapsed = useElapsed(timer?.startedAt ?? null)
  if (!timer) return null
  return (
    <button type="button" className="timer-chip" onClick={() => stop()} title="Stop the timer and log this time">
      <span className="timer-dot" aria-hidden="true" />
      <span className="timer-time">{elapsed}</span>
      <Icon name="check" size={14} />
      <span className="sr-only">Stop timer and log time</span>
    </button>
  )
}
