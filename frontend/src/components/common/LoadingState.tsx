export function Spinner() {
  return (
    <span className="inline-spinner" role="status" aria-label="Loading">
      <span className="spinner" />
    </span>
  )
}

export function LoadingState({ label = 'Loading…' }: { label?: string }) {
  return (
    <div className="state-block" role="status" aria-live="polite">
      <span className="spinner" />
      <span>{label}</span>
    </div>
  )
}
