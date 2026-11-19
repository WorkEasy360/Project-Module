import { useMemo, useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import '../../styles/ops.css'
import { useIdentity } from '../../context/IdentityContext'
import type { Identity } from '../../context/identityStore'
import { usePeople } from '../../context/PeopleContext'
import { type ActivityEntry } from '../../api/collaboration'
import { ErrorState } from '../../components/common/ErrorState'
import { Icon, type IconName } from '../../components/common/Icon'
import { Modal } from '../../components/common/Modal'
import { PriorityBadge } from '../../components/common/Badge'
import { CardHead, CountTile } from '../../components/ops/OpsBits'
import { CreateProjectDialog } from '../projects/CreateProjectDialog'
import { AddTaskButton } from '../workspace/AddTaskButton'
import { formatDate, todayIso } from '../../utils/format'
import { greeting, timeAgo } from './opsDashboard'
import { isOverdue, myWork, projectProgress, teamOverview, useWorkData } from '../workspace/workRules'

/**
 * The greeting's name: a first name, else the part of the email before @ (a Workeasy360 account may
 * share an email but no name), else none.
 */
function greetingName(identity: Identity | null): string {
  const first = identity?.name?.trim().split(/\s+/)[0]
  if (first) return first
  if (identity?.email?.includes('@')) return identity.email.split('@')[0]
  return ''
}

/**
 * V3 Home: actionable work first. My Work Summary (my open, due today, overdue and upcoming tasks),
 * a Project & Team Overview for people who manage projects, project progress from task completion,
 * recent activity and quick actions. Every count opens its filtered list; charts live on Reports.
 */
export function DashboardPage() {
  const { identity } = useIdentity()
  const { isAdmin } = usePeople()
  const navigate = useNavigate()
  const [createProjectOpen, setCreateProjectOpen] = useState(false)
  const [activityOpen, setActivityOpen] = useState(false)
  const today = todayIso()
  const data = useWorkData(8)
  const me = identity?.userId ?? null

  const projects = useMemo(() => data.data?.projects ?? [], [data.data])
  const tasks = useMemo(() => data.data?.tasks ?? [], [data.data])
  const mine = useMemo(() => myWork(tasks, me, today), [tasks, me, today])
  const team = useMemo(() => teamOverview(projects, tasks, me, isAdmin, today), [projects, tasks, me, isAdmin, today])
  const progress = useMemo(
    () => projects.slice(0, 5).map((p) => ({ project: p, ...projectProgress(p.id, tasks, today) })),
    [projects, tasks, today],
  )
  const firstName = greetingName(identity)
  const projectName = (id: string) => projects.find((p) => p.id === id)?.name ?? ''

  if (data.error) return <ErrorState message={data.error} onRetry={data.reload} />
  const loading = data.loading && !data.data

  return (
    <div className="ops-page ops-home">
      <header className="ops-page-head">
        <div>
          <h1>
            {greeting(new Date().getHours())}
            {firstName ? `, ${firstName}` : ''}! <span aria-hidden="true">👋</span>
          </h1>
          <p>Here&apos;s your work for today.</p>
        </div>
      </header>

      <section className={`ops-stats ${team ? 'ops-stats-7' : 'ops-stats-4'}`} aria-label="My work summary">
        <CountTile icon="tasks" tone="green" label="My Open Tasks" value={mine.open} loading={loading} to="/tasks" />
        <CountTile icon="calendar" tone="orange" label="Due Today" value={mine.dueToday} loading={loading} to="/tasks?view=today" />
        <CountTile icon="warning" tone="red" label="Overdue" value={mine.overdue} loading={loading} to="/tasks?view=overdue" />
        <CountTile icon="calendar" tone="blue" label="Upcoming" value={mine.upcoming} loading={loading} to="/tasks?view=upcoming" />
        {team && (
          <>
            <CountTile icon="folder" tone="purple" label="Active Projects" value={team.activeProjects} loading={loading} to="/projects" />
            <CountTile icon="team" tone="red" label="Team Overdue" value={team.overdue} loading={loading} to="/tasks/all?view=overdue" />
            <CountTile icon="user" tone="orange" label="Unassigned" value={team.unassigned} loading={loading} to="/tasks/all?assignee=none" />
          </>
        )}
      </section>

      <div className="ops-grid ops-grid-2">
        <section className="ops-card">
          <CardHead icon="user" title="My Tasks" to="/tasks" />
          {!loading && mine.next.length === 0 && <p className="ops-empty">Nothing is assigned to you right now.</p>}
          <ul className="ops-home-tasks">
            {mine.next.map((t) => (
              <li key={t.id}>
                <Link to={`/projects/${t.projectId}/tasks/${t.id}`} className="ops-home-task-name">{t.name}</Link>
                <span className="ops-home-task-project">{projectName(t.projectId)}</span>
                <PriorityBadge priority={t.priority} />
                <span className={`ops-home-task-due${isOverdue(t, today) ? ' overdue' : t.dueDate === today ? ' today' : ''}`}>
                  {t.dueDate ? (t.dueDate === today ? 'Today' : formatDate(t.dueDate)) : 'No due date'}
                </span>
              </li>
            ))}
          </ul>
        </section>

        <section className="ops-card">
          <CardHead icon="barChart" title="Project Progress" to="/reports/projects" />
          {!loading && progress.length === 0 && <p className="ops-empty">No active projects yet.</p>}
          <ul className="ops-progress-list">
            {progress.map((p) => (
              <li key={p.project.id}>
                <Link to={`/projects/${p.project.id}/overview`}>{p.project.name}</Link>
                {p.percent === null ? (
                  <span className="ops-no-tasks">No tasks yet</span>
                ) : (
                  <>
                    <span className="ops-bar"><span style={{ width: `${p.percent}%` }} /></span>
                    <strong>{p.percent}%</strong>
                  </>
                )}
              </li>
            ))}
          </ul>
        </section>
      </div>

      <div className="ops-grid ops-grid-2">
        <section className="ops-card">
          <CardHead icon="bolt" title="Quick Actions" />
          <div className="ops-quick ops-quick-3">
            <AddTaskButton variant="quick" projects={projects} onCreated={() => data.reload()} />
            <button type="button" className="ops-quick-btn tone-blue" onClick={() => setCreateProjectOpen(true)}>
              <Icon name="folder" size={34} />
              Create Project
            </button>
            {isAdmin && (
              <button type="button" className="ops-quick-btn tone-purple" onClick={() => navigate('/team', { state: { invite: true } })}>
                <Icon name="userPlus" size={34} />
                Invite Member
              </button>
            )}
          </div>
        </section>

        <section className="ops-card">
          <header className="ops-card-head">
            <h2>
              <Icon name="clock" size={22} /> Recent Activity
            </h2>
            <button type="button" className="ops-view-all" onClick={() => setActivityOpen(true)}>
              View All <Icon name="arrowRight" size={16} />
            </button>
          </header>
          {(data.data?.activity.length ?? 0) === 0 && !loading && <p className="ops-empty">Nothing has happened yet.</p>}
          <ul className="ops-activity">
            {(data.data?.activity ?? []).slice(0, 4).map((a) => (
              <ActivityRow key={a.id} entry={a} />
            ))}
          </ul>
        </section>
      </div>

      {createProjectOpen && (
        <CreateProjectDialog
          onClose={() => setCreateProjectOpen(false)}
          onCreated={(p) => {
            setCreateProjectOpen(false)
            navigate(`/projects/${p.id}`)
          }}
        />
      )}
      {activityOpen && (
        <Modal title="Recent Activity" onClose={() => setActivityOpen(false)}>
          <ul className="ops-activity ops-activity-full">
            {(data.data?.activity ?? []).slice(0, 40).map((a) => (
              <ActivityRow key={a.id} entry={a} />
            ))}
          </ul>
        </Modal>
      )}
    </div>
  )
}

function activityLook(entry: ActivityEntry): { icon: IconName; tone: string } {
  const t = `${entry.type} ${entry.summary}`.toLowerCase()
  if (t.includes('complet') || t.includes('done')) return { icon: 'checkCircle', tone: 'green' }
  if (t.includes('member') || t.includes('added') || t.includes('joined')) return { icon: 'team', tone: 'blue' }
  if (t.includes('project') && t.includes('creat')) return { icon: 'folder', tone: 'purple' }
  if (t.includes('creat')) return { icon: 'plus', tone: 'purple' }
  return { icon: 'comment', tone: 'orange' }
}

function ActivityRow({ entry }: { entry: ActivityEntry & { projectId: string } }) {
  const look = activityLook(entry)
  return (
    <li>
      <Icon name={look.icon} size={20} className={`tone-text-${look.tone}`} />
      <Link to={`/projects/${entry.projectId}/activity`} className="ops-activity-text">
        {entry.summary}
      </Link>
      <span className="ops-activity-time">{timeAgo(entry.occurredAt)}</span>
    </li>
  )
}
