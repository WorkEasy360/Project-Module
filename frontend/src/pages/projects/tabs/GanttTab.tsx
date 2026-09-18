import { useMemo } from 'react'
import { useProjectWorkspace } from '../../../context/ProjectWorkspaceContext'
import { useAsyncData } from '../../../hooks/useAsyncData'
import { ganttApi } from '../../../api/gantt'
import { LoadingState } from '../../../components/common/LoadingState'
import { ErrorState } from '../../../components/common/ErrorState'
import { EmptyState } from '../../../components/common/EmptyState'
import { formatDate } from '../../../utils/format'
import type { GanttBar, GanttPoint } from '../../../types/work'

function toTime(value: string | null): number | null {
  if (!value) return null
  const t = new Date(value).getTime()
  return Number.isNaN(t) ? null : t
}

export function GanttTab() {
  const { project } = useProjectWorkspace()
  const { data, loading, error, reload } = useAsyncData(() => ganttApi.get(project.id), [project.id])

  const range = useMemo(() => {
    if (!data) return null
    const times: number[] = []
    for (const bar of data.phases) {
      const s = toTime(bar.startDate)
      const e = toTime(bar.endDate)
      if (s !== null) times.push(s)
      if (e !== null) times.push(e)
    }
    for (const point of [...data.tasks, ...data.milestones]) {
      const d = toTime(point.date)
      if (d !== null) times.push(d)
    }
    if (times.length === 0) return null
    const min = Math.min(...times)
    const max = Math.max(...times)
    return { min, max: max === min ? min + 1 : max }
  }, [data])

  if (loading) return <LoadingState label="Loading Gantt…" />
  if (error) return <ErrorState message={error} onRetry={reload} />
  if (!data) return null

  const hasContent = data.phases.length > 0 || data.tasks.length > 0 || data.milestones.length > 0
  if (!hasContent) {
    return (
      <EmptyState
        title="No Gantt data yet."
        description="Add phase dates, task due dates, or milestone due dates to see them here."
      />
    )
  }

  function percent(time: number): number {
    if (!range) return 0
    return ((time - range.min) / (range.max - range.min)) * 100
  }

  return (
    <div>
      <p className="text-muted">
        Phases render as date-range bars. Tasks and milestones (no task start date exists in this
        backend) render as single points on their due date.
      </p>
      <div className="gantt-scroll">
        <div className="gantt-inner">
          {data.phases.map((bar) => (
            <GanttBarRow key={bar.id} bar={bar} percent={percent} />
          ))}
          {data.tasks.map((point) => (
            <GanttPointRow key={point.id} point={point} percent={percent} kind="Task" />
          ))}
          {data.milestones.map((point) => (
            <GanttPointRow key={point.id} point={point} percent={percent} kind="Milestone" />
          ))}
        </div>
      </div>

      {data.dependencies.length > 0 && (
        <div className="card">
          <h3>Dependencies</h3>
          <ul className="list-plain">
            {data.dependencies.map((d) => (
              <li key={d.id}>
                <code>{d.dependentTaskId}</code> depends on <code>{d.prerequisiteTaskId}</code>{' '}
                {d.broken && <span className="badge badge-danger">Broken</span>}
              </li>
            ))}
          </ul>
        </div>
      )}
    </div>
  )
}

function GanttBarRow({ bar, percent }: { bar: GanttBar; percent: (t: number) => number }) {
  const s = toTime(bar.startDate)
  const e = toTime(bar.endDate)
  return (
    <div className="gantt-row">
      <div className="gantt-row-label">{bar.name}</div>
      <div className="gantt-row-track">
        {s !== null && e !== null && (
          <div
            className="gantt-bar"
            style={{ left: `${percent(s)}%`, width: `${Math.max(percent(e) - percent(s), 0.5)}%` }}
            title={`${formatDate(bar.startDate)} – ${formatDate(bar.endDate)}`}
          />
        )}
      </div>
    </div>
  )
}

function GanttPointRow({
  point,
  percent,
  kind,
}: {
  point: GanttPoint
  percent: (t: number) => number
  kind: string
}) {
  const d = toTime(point.date)
  return (
    <div className="gantt-row">
      <div className="gantt-row-label">
        {point.name} <span className="text-faint">({kind})</span>
      </div>
      <div className="gantt-row-track">
        {d !== null && (
          <div className="gantt-point" style={{ left: `${percent(d)}%` }} title={formatDate(point.date)} />
        )}
      </div>
    </div>
  )
}
