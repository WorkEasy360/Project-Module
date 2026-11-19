import { useMemo, useState } from 'react'
import { Link } from 'react-router-dom'
import { usePeople } from '../../context/PeopleContext'
import { ErrorState } from '../../components/common/ErrorState'
import { Icon } from '../../components/common/Icon'
import { Avatar } from '../../components/common/Avatar'
import { CardHead, CountTile, FilterSelect } from '../../components/ops/OpsBits'
import { BarChart, Donut } from '../../components/ops/Charts'
import { formatDate, humanizeToken, todayIso } from '../../utils/format'
import { TASK_PRIORITIES, type Task } from '../../types/work'
import type { ProjectSummary } from '../../types/project'
import { PERIODS, longDate, periodFor, statusBreakdown, tasksInPeriod, tasksOverview, type Period, type PeriodKind } from '../dashboard/opsDashboard'
import { countBy, isOpen, isOverdue, projectProgress, useWorkData, workloadByPerson } from './workRules'

type ReportView = 'tasks' | 'projects' | 'workload'

const TITLES: Record<ReportView, { title: string; text: string }> = {
  tasks: { title: 'Task Progress', text: 'How work is moving: tasks by status, created against completed, and open work by priority.' },
  projects: { title: 'Project Progress', text: 'Each project’s progress, worked out from its completed tasks.' },
  workload: { title: 'Team Workload', text: 'Open, due and overdue work for each person.' },
}

/** V3 Reports: Task Progress, Project Progress and Team Workload, from the same saved tasks as everywhere else. */
export function ReportsPage({ view }: { view: ReportView }) {
  const data = useWorkData()
  const [kind, setKind] = useState<PeriodKind>('fy')
  const today = todayIso()
  const period = useMemo(() => periodFor(kind, today), [kind, today])
  if (data.error) return <ErrorState message={data.error} onRetry={data.reload} />
  const loading = data.loading && !data.data
  const projects = data.data?.projects ?? []
  const tasks = data.data?.tasks ?? []

  return (
    <div className={`ops-page ops-reports ops-reports-${view}`}>
      <header className="ops-page-head">
        <div>
          <h1>{TITLES[view].title}</h1>
          <p>{TITLES[view].text}</p>
        </div>
        {view === 'tasks' && (
          <div className="ops-period">
            <FilterSelect label="Period" value={kind} onChange={(v) => setKind(v as PeriodKind)} icon="calendar"
              options={PERIODS.map((p) => ({ value: p.value, label: p.label }))} />
            <span className="ops-range">{period.from && period.to ? `${longDate(period.from)} – ${longDate(period.to)}` : 'All time'}</span>
          </div>
        )}
      </header>
      {view === 'tasks' && <TaskProgress tasks={tasksInPeriod(tasks, period)} allTasks={tasks} period={period} today={today} loading={loading} />}
      {view === 'projects' && <ProjectProgress projects={projects} tasks={tasks} today={today} loading={loading} />}
      {view === 'workload' && <TeamWorkload tasks={tasks} today={today} loading={loading} />}
    </div>
  )
}

function TaskProgress({ tasks, allTasks, period, today, loading }: {
  tasks: Task[]
  allTasks: Task[]
  period: Period
  today: string
  loading: boolean
}) {
  const slices = statusBreakdown(tasks)
  const chart = tasksOverview(allTasks, period, today)
  const open = tasks.filter(isOpen)
  const done = tasks.length - open.length
  const byPriority = countBy(open, (t) => t.priority, TASK_PRIORITIES)
  return (
    <>
      <section className="ops-stats ops-stats-5" aria-label="Task totals">
        <CountTile icon="tasks" tone="green" label="Total Tasks" value={tasks.length} loading={loading} to="/tasks/all?status=" />
        <CountTile icon="play" tone="blue" label="Open" value={open.length} loading={loading} to="/tasks/all" />
        <CountTile icon="checkCircle" tone="green" label={`Done (${tasks.length ? Math.round((done / tasks.length) * 100) : 0}%)`} value={done} loading={loading} to="/tasks/all?view=completed" />
        <CountTile icon="warning" tone="red" label="Overdue" value={tasks.filter((t) => isOverdue(t, today)).length} loading={loading} to="/tasks/all?view=overdue" />
        <CountTile icon="pause" tone="orange" label="Blocked" value={tasks.filter((t) => t.status === 'BLOCKED').length} loading={loading} to="/tasks/all?status=BLOCKED" />
      </section>
      <div className="ops-grid ops-report-grid">
        <section className="ops-card">
          <CardHead icon="activity" title="Tasks by Status" />
          <div className="ops-donut-wrap">
            <Donut slices={slices} total={tasks.length} />
            <ul className="ops-legend">
              {slices.map((s) => (
                <li key={s.key}>
                  <span className="ops-dot" style={{ background: s.color }} />
                  <span>{s.label}</span>
                  <span className="ops-legend-num">{s.count} ({s.percent}%)</span>
                </li>
              ))}
            </ul>
          </div>
          <h3 className="ops-subhead">Open tasks by priority</h3>
          <ul className="ops-mytasks ops-priority-list">
            {TASK_PRIORITIES.slice().reverse().map((p) => (
              <li key={p}>
                <Link to={`/tasks/all?priority=${p}`}>{humanizeToken(p)}</Link>
                <span className={`ops-count tone-${p === 'HIGH' ? 'red' : p === 'MEDIUM' ? 'blue' : 'green'}`}>{byPriority[p]}</span>
              </li>
            ))}
          </ul>
        </section>
        <section className="ops-card">
          <header className="ops-card-head">
            <h2>
              <Icon name="trendUp" size={22} /> Tasks Created and Completed
            </h2>
            <div className="ops-chart-legend">
              <span><span className="ops-dot" style={{ background: '#16a34a' }} /> Completed</span>
              <span><span className="ops-dot" style={{ background: '#60a5fa' }} /> Created</span>
            </div>
          </header>
          <BarChart buckets={chart} height={250} />
        </section>
      </div>
    </>
  )
}

function ProjectProgress({ projects, tasks, today, loading }: {
  projects: ProjectSummary[]
  tasks: Task[]
  today: string
  loading: boolean
}) {
  const { nameOf } = usePeople()
  const rows = projects.map((p) => ({ project: p, progress: projectProgress(p.id, tasks, today) }))
  const noTasks = rows.filter((r) => r.progress.total === 0).length
  return (
    <>
      <section className="ops-stats ops-stats-4" aria-label="Project totals">
        <CountTile icon="play" tone="green" label="Active" value={projects.filter((p) => p.status === 'ACTIVE').length} loading={loading} to="/projects" />
        <CountTile icon="pause" tone="orange" label="On Hold" value={projects.filter((p) => p.status === 'ON_HOLD').length} loading={loading} to="/projects" />
        <CountTile icon="checkCircle" tone="blue" label="Completed" value={projects.filter((p) => p.status === 'COMPLETED').length} loading={loading} to="/projects" />
        <CountTile icon="folder" tone="purple" label="Projects With No Tasks" value={noTasks} loading={loading} to="/projects" />
      </section>
      <section className="ops-card ops-table-card">
        <div className="ops-table-scroll">
          <table className="ops-table">
            <thead>
              <tr>
                <th>Project</th>
                <th>Owner</th>
                <th>Progress</th>
                <th>Done / Total</th>
                <th>Open</th>
                <th>Overdue</th>
                <th>Due Date</th>
              </tr>
            </thead>
            <tbody>
              {loading && <tr><td colSpan={7} className="ops-empty">Loading projects…</td></tr>}
              {!loading && rows.length === 0 && <tr><td colSpan={7} className="ops-empty">No projects yet.</td></tr>}
              {rows.map(({ project, progress }) => (
                <tr key={project.id}>
                  <td><Link to={`/projects/${project.id}/overview`} className="ops-project-name">{project.name}</Link></td>
                  <td>
                    <span className="ops-owner">
                      <Avatar userId={project.ownerId} name={nameOf(project.ownerId)} />
                      {nameOf(project.ownerId)}
                    </span>
                  </td>
                  <td>
                    {progress.percent === null ? (
                      <span className="text-faint">No tasks yet</span>
                    ) : (
                      <span className="ops-progress-cell">
                        <span className="ops-bar"><span style={{ width: `${progress.percent}%` }} /></span>
                        {progress.percent}%
                      </span>
                    )}
                  </td>
                  <td>{progress.done} / {progress.total}</td>
                  <td><Link to={`/tasks/all?project=${project.id}`}>{progress.open}</Link></td>
                  <td>
                    {progress.overdue > 0 ? (
                      <Link to={`/tasks/all?project=${project.id}&view=overdue`} className="ops-overdue-tag">{progress.overdue}</Link>
                    ) : (
                      0
                    )}
                  </td>
                  <td>{project.targetEndDate ? formatDate(project.targetEndDate) : <span className="text-faint">—</span>}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </section>
    </>
  )
}

function TeamWorkload({ tasks, today, loading }: {
  tasks: Task[]
  today: string
  loading: boolean
}) {
  const { nameOf } = usePeople()
  const rows = workloadByPerson(tasks, today)
  const maxOpen = Math.max(1, ...rows.map((r) => r.open))
  const unassigned = tasks.filter((t) => isOpen(t) && !t.assigneeId).length
  return (
    <>
      <section className="ops-stats ops-stats-4" aria-label="Workload totals">
        <CountTile icon="team" tone="green" label="People With Work" value={rows.filter((r) => r.open > 0).length} loading={loading} to="/team" />
        <CountTile icon="tasks" tone="blue" label="Open Assigned Tasks" value={rows.reduce((n, r) => n + r.open, 0)} loading={loading} to="/tasks/all" />
        <CountTile icon="warning" tone="red" label="Overdue" value={rows.reduce((n, r) => n + r.overdue, 0)} loading={loading} to="/tasks/all?view=overdue" />
        <CountTile icon="user" tone="purple" label="Unassigned Open Tasks" value={unassigned} loading={loading} to="/tasks/all?assignee=none" />
      </section>
      <section className="ops-card ops-table-card">
        <div className="ops-table-scroll">
          <table className="ops-table">
            <thead>
              <tr>
                <th>Person</th>
                <th>Open Tasks</th>
                <th>Due in 7 Days</th>
                <th>Overdue</th>
                <th>Done</th>
                <th>Workload</th>
              </tr>
            </thead>
            <tbody>
              {loading && <tr><td colSpan={6} className="ops-empty">Loading workload…</td></tr>}
              {!loading && rows.length === 0 && <tr><td colSpan={6} className="ops-empty">No tasks are assigned yet.</td></tr>}
              {rows.map((r) => (
                <tr key={r.userId}>
                  <td>
                    <span className="ops-owner">
                      <Avatar userId={r.userId} name={nameOf(r.userId)} />
                      {nameOf(r.userId)}
                    </span>
                  </td>
                  <td><Link to={`/tasks/all?assignee=${r.userId}`}>{r.open}</Link></td>
                  <td>{r.dueSoon}</td>
                  <td>{r.overdue > 0 ? <Link to={`/tasks/all?assignee=${r.userId}&view=overdue`} className="ops-overdue-tag">{r.overdue}</Link> : 0}</td>
                  <td>{r.done}</td>
                  <td>
                    <span className="ops-bar ops-bar-wide"><span style={{ width: `${Math.round((r.open / maxOpen) * 100)}%` }} /></span>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </section>
    </>
  )
}
