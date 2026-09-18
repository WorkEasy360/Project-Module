export function ErrorState({ message, onRetry }: { message: string; onRetry?: () => void }) {
  return (
    <div className="state-block state-error" role="alert">
      <span className="state-title">Something went wrong</span>
      <span>{message}</span>
      {onRetry && (
        <button type="button" className="btn btn-sm" onClick={onRetry}>
          Try again
        </button>
      )}
    </div>
  )
}

export function InlineError({ message }: { message: string }) {
  return (
    <div className="banner banner-error" role="alert">
      {message}
    </div>
  )
}
