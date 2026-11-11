import { useMemo, useState } from 'react'
import { Link } from 'react-router-dom'
import '../../styles/workspace.css'
import { timeApi, type TimeEntry } from '../../api/collaboration'
import { tasksApi } from '../../api/tasks'
import { Badge } from '../../components/common/Badge'
import { EmptyState } from '../../components/common/EmptyState'
import { ErrorState } from '../../components/common/ErrorState'
import { Icon } from '../../components/common/Icon'
import { LoadingState } from '../../components/common/LoadingState'
import { PageHeader } from '../../components/common/PageHeader'
import { useAsyncData } from '../../hooks/useAsyncData'
import { useAllProjects } from '../../hooks/useWorkspaceData'
import { downloadCsv, toCsv } from '../../utils/csv'
import { formatMinutes, toIsoDate } from '../../utils/format'

/** Monday of the week containing the date. */
function startOfWeek(date: Date): Date {
  const start = new Date(date.getFullYear(), date.getMonth(), date.getDate())
  start.setDate(start.getDate() - ((start.getDay() + 6) % 7))
  return start
}

function addDays(date: Date, days: number): Date {
  const next = new Date(date)
  next.setDate(next.getDate() + days)
  return next
}

const DAY_FORMAT = new Intl.DateTimeFormat(undefined, { weekday: 'short', day: 'numeric', month: 'short' })

/** One person's time for a week, a day at a time. */
export function TimesheetPage() {
  const [weekStart, setWeekStart] = useState(() => startOfWeek(new Date()))
  const days = useMemo(() => Array.from({ length: 7 }, (_, i) => addDays(weekStart, i)), [weekStart])
  const from = toIsoDate(days[0])
  const to = toIsoDate(days[6])

  const { data, loading, error, reload } = useAsyncData(() => timeApi.listMine(from, to), [from, to])
  const projects = useAllProjects()
  const taskIds = useMemo(() => [...new Set((data ?? []).map((entry) => entry.taskId))], [data])
  const taskNames = useAsyncData(
    async () => {
      const found = await Promise.all(taskIds.map((id) => tasksApi.get(id).catch(() => null)))
      return new Map(found.filter((task) => task !== null).map((task) => [task.id, task.name]))
    },
    [taskIds.join(',')],
  )

  const projectName = (id: string) => projects.data?.content.find((project) => project.id === id)?.name ?? ''
  const taskName = (id: string) => taskNames.data?.get(id) ?? 'Task'
  const total = (data ?? []).reduce((sum, entry) => sum + entry.minutes, 0)
  const billable = (data ?? []).filter((entry) => entry.billable).reduce((sum, entry) => sum + entry.minutes, 0)

  function exportWeek() {
    const rows = (data ?? []).map((entry) => [
      entry.workDate,
      projectName(entry.projectId),
      taskName(entry.taskId),
      (entry.minutes / 60).toFixed(2),
      entry.billable ? 'Yes' : 'No',
      entry.note,
    ])
    downloadCsv(`timesheet-${from}.csv`, toCsv(['Date', 'Project', 'Task', 'Hours', 'Billable', 'Note'], rows))
  }

  async function remove(entry: TimeEntry) {
    await timeApi.remove(entry.id)
    reload()
  }

  return (
    <div className="ws-page rise-in">
      <PageHeader
        title="Timesheet"
        description="The time you've logged, week by week."
        actions={
          data && data.length > 0 ? (
            <button type="button" className="btn" onClick={exportWeek}>
              <Icon name="report" size={15} /> Export CSV
            </button>
          ) : undefined
        }
      />
      <div className="ws-toolbar ts-toolbar">
        <div className="ws-seg" role="group" aria-label="Week">
          <button type="button" onClick={() => setWeekStart(addDays(weekStart, -7))}>
            Previous
          </button>
          <button type="button" onClick={() => setWeekStart(startOfWeek(new Date()))}>
            This week
          </button>
          <button type="button" onClick={() => setWeekStart(addDays(weekStart, 7))}>
            Next
          </button>
        </div>
        <strong className="ts-range">
          {DAY_FORMAT.format(days[0])} – {DAY_FORMAT.format(days[6])}
        </strong>
        <span className="ts-total">
          <strong>{formatMinutes(total)}</strong> total · {formatMinutes(billable)} billable
        </span>
      </div>

      {loading && <LoadingState label="Loading your time…" />}
      {error && <ErrorState message={error} onRetry={reload} />}
      {data && data.length === 0 && (
        <EmptyState title="No time logged this week" description="Log time, or start a timer, from any task's page." />
      )}
      {data && data.length > 0 && (
        <div className="ts-days">
          {days.map((day) => {
            const iso = toIsoDate(day)
            const entries = data.filter((entry) => entry.workDate === iso)
            if (entries.length === 0) return null
            return (
              <section key={iso} className="ts-day" aria-label={DAY_FORMAT.format(day)}>
                <header>
                  <span>{DAY_FORMAT.format(day)}</span>
                  <strong>{formatMinutes(entries.reduce((sum, entry) => sum + entry.minutes, 0))}</strong>
                </header>
                <ul>
                  {entries.map((entry) => (
                    <li key={entry.id}>
                      <span className="ts-entry-name">
                        <Link to={`/projects/${entry.projectId}/tasks/${entry.taskId}`}>{taskName(entry.taskId)}</Link>
                        <span className="text-faint">
                          {projectName(entry.projectId)}
                          {entry.note ? ` · ${entry.note}` : ''}
                        </span>
                      </span>
                      {entry.billable && <Badge tone="info">Billable</Badge>}
                      <strong>{formatMinutes(entry.minutes)}</strong>
                      <button type="button" className="btn-link" onClick={() => remove(entry)}>
                        Remove
                      </button>
                    </li>
                  ))}
                </ul>
              </section>
            )
          })}
        </div>
      )}
    </div>
  )
}
