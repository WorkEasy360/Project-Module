import { Link } from 'react-router-dom'

export function NotFoundPage() {
  return (
    <div className="state-block">
      <span className="state-title">Page not found</span>
      <Link to="/dashboard">Go to dashboard</Link>
    </div>
  )
}
