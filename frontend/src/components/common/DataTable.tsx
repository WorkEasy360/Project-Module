import type { ReactNode } from 'react'
import { LoadingState } from './LoadingState'
import { EmptyState } from './EmptyState'
import { ErrorState } from './ErrorState'

export interface Column<T> {
  header: string
  render: (item: T) => ReactNode
  key: string
}

export function DataTable<T>({
  items,
  loading,
  error,
  onRetry,
  columns,
  getRowKey,
  emptyTitle,
  emptyDescription,
  emptyAction,
}: {
  items: T[] | null
  loading: boolean
  error: string | null
  onRetry?: () => void
  columns: Column<T>[]
  getRowKey: (item: T) => string
  emptyTitle: string
  emptyDescription?: string
  emptyAction?: ReactNode
}) {
  if (loading) return <LoadingState />
  if (error) return <ErrorState message={error} onRetry={onRetry} />
  if (!items || items.length === 0) {
    return <EmptyState title={emptyTitle} description={emptyDescription} action={emptyAction} />
  }

  return (
    <div className="table-wrap">
      <table>
        <thead>
          <tr>
            {columns.map((col) => (
              <th key={col.key}>{col.header}</th>
            ))}
          </tr>
        </thead>
        <tbody>
          {items.map((item) => (
            <tr key={getRowKey(item)}>
              {columns.map((col) => (
                <td key={col.key}>{col.render(item)}</td>
              ))}
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  )
}
