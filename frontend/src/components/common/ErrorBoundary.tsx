import { Component, type ErrorInfo, type ReactNode } from 'react'

interface State {
  failed: boolean
}

/**
 * Last line of defence: an unexpected render error shows one plain message and a reload button
 * instead of a blank page. It never shows the error itself (that goes to the console).
 */
export class ErrorBoundary extends Component<{ children: ReactNode }, State> {
  state: State = { failed: false }

  static getDerivedStateFromError(): State {
    return { failed: true }
  }

  componentDidCatch(error: Error, info: ErrorInfo): void {
    console.error('Unexpected error', error, info.componentStack)
  }

  render() {
    if (!this.state.failed) return this.props.children
    return (
      <div role="alert" style={{ padding: '48px 24px', textAlign: 'center' }}>
        <h1 style={{ fontSize: '1.25rem' }}>Something went wrong</h1>
        <p>The page could not be shown. Reload it to try again.</p>
        <button type="button" className="btn btn-primary" onClick={() => window.location.reload()}>
          Reload
        </button>
      </div>
    )
  }
}
