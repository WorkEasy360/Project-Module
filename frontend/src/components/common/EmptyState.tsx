import type { ReactNode } from 'react'

export function EmptyState({
  title,
  description,
  action,
}: {
  title: string
  description?: string
  action?: ReactNode
}) {
  return (
    <div className="state-block">
      <span className="state-title">{title}</span>
      {description && <span>{description}</span>}
      {action}
    </div>
  )
}
