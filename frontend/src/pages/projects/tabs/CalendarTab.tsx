import { useState } from 'react'
import { useProjectWorkspace } from '../../../context/ProjectWorkspaceContext'
import { useAsyncData } from '../../../hooks/useAsyncData'
import { calendarApi } from '../../../api/calendar'
import { LoadingState } from '../../../components/common/LoadingState'
import { ErrorState } from '../../../components/common/ErrorState'
import { EmptyState } from '../../../components/common/EmptyState'
import { Badge } from '../../../components/common/Badge'
import { formatDate } from '../../../utils/format'

export function CalendarTab() {
  const { project } = useProjectWorkspace()
  const [from, setFrom] = useState('')
  const [to, setTo] = useState('')
  const { data, loading, error, reload } = useAsyncData(
    () => calendarApi.get(project.id, from || undefined, to || undefined),
    [project.id, from, to],
  )

  const dated = data?.entries.filter((e) => e.date).sort((a, b) => (a.date! < b.date! ? -1 : 1)) ?? []
  const ranged = data?.entries.filter((e) => !e.date && (e.startDate || e.endDate)) ?? []

  return (
    <div>
      <div className="filter-bar">
        <div className="form-field">
          <label htmlFor="cal-from">From</label>
          <input id="cal-from" type="date" value={from} onChange={(e) => setFrom(e.target.value)} />
        </div>
        <div className="form-field">
          <label htmlFor="cal-to">To</label>
          <input id="cal-to" type="date" value={to} onChange={(e) => setTo(e.target.value)} />
        </div>
      </div>

      {loading && <LoadingState label="Loading calendar…" />}
      {error && <ErrorState message={error} onRetry={reload} />}
      {data && data.entries.length === 0 && <EmptyState title="No calendar entries in this range." />}

      {data && dated.length > 0 && (
        <div className="card">
          <h3>Tasks &amp; Milestones</h3>
          <ul className="list-plain">
            {dated.map((entry) => (
              <li key={`${entry.entityType}-${entry.id}`}>
                <Badge tone={entry.entityType === 'MILESTONE' ? 'primary' : 'neutral'}>
                  {entry.entityType}
                </Badge>{' '}
                {entry.name} — {formatDate(entry.date)}
              </li>
            ))}
          </ul>
        </div>
      )}

      {data && ranged.length > 0 && (
        <div className="card">
          <h3>Phases</h3>
          <ul className="list-plain">
            {ranged.map((entry) => (
              <li key={`${entry.entityType}-${entry.id}`}>
                <Badge tone="info">{entry.entityType}</Badge> {entry.name} — {formatDate(entry.startDate)} to{' '}
                {formatDate(entry.endDate)}
              </li>
            ))}
          </ul>
        </div>
      )}
    </div>
  )
}
