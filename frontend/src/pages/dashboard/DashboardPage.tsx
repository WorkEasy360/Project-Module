import { useMemo, useState } from 'react'
import { Link } from 'react-router-dom'
import '../../styles/dashboard.css'
import { useAsyncData } from '../../hooks/useAsyncData'
import { useIdentity } from '../../context/IdentityContext'
import { dashboardApi } from '../../api/dashboard'
import { projectsApi } from '../../api/projects'
import { healthApi } from '../../api/health'
import { tasksApi } from '../../api/tasks'
import { milestonesApi } from '../../api/milestones'
import { ErrorState } from '../../components/common/ErrorState'
import { Skeleton } from '../../components/common/Skeleton'
import { Icon, type IconName } from '../../components/common/Icon'
import { IconButton } from '../../components/common/IconButton'
import { Badge, ProjectStatusBadge } from '../../components/common/Badge'
import { CreateProjectDialog } from '../projects/CreateProjectDialog'
import { formatDate, humanizeToken } from '../../utils/format'
import { PROJECT_STATUSES, type ProjectStatus } from '../../types/project'
import { HeroSection } from './components/HeroSection'
import { AttentionPanel } from './components/AttentionPanel'
import { MiniCalendarCard } from './components/MiniCalendarCard'
import { UpcomingCard } from './components/UpcomingCard'
import { AIRecommendationsCard } from './components/AIRecommendationsCard'
import { ProjectStatusCard, RecentActivityCard } from './components/LowerWidgets'
import {
  EMPTY_FILTERS,
  HEALTH_LABEL,
  SORT_OPTIONS,
  applyFilters,
  collectAttentionTasks,
  greetingForHour,
  hasActiveFilters,
  healthLevel,
  latestDueDate,
  progressPercent,
  todayIso,
  upcomingItems,
  type LoadedProject,
  type SortValue,
} from './dashboardUtils'

const LOADED_PAGE_SIZE = 10
const CARD_TONES = ['blue', 'purple', 'amber', 'green'] as const

/**
 * Home. Every number is real: /dashboard counts, the first page of /projects (sorted by the
 * backend) and, per loaded project, /health, /tasks and /milestones; the mini calendar and the
 * AI card add /calendar and /ai/recommendations per loaded project. Task-level widgets are
 * therefore scoped to the loaded projects and say so. No activity API exists, so that card is an
 * honest placeholder, and no trend data exists, so the stat cards show none. Data loads on mount
 * and after any action here; there is no polling.
 */
export function DashboardPage() {
  const { identity } = useIdentity()
  const [createOpen, setCreateOpen] = useState(false)
  const [sort, setSort] = useState<SortValue>('updatedAt,DESC')
  const [filters, setFilters] = useState(EMPTY_FILTERS)
  const [view, setView] = useState<'grid' | 'list'>('grid')
  const today = todayIso()

  const summary = useAsyncData(() => dashboardApi.get(), [])
  const projects = useAsyncData(async (): Promise<LoadedProject[]> => {
    const page = await projectsApi.list(0, LOADED_PAGE_SIZE, sort)
    return Promise.all(
      page.content.map(async (project) => {
        const [health, tasks, milestones] = await Promise.all([
          healthApi.get(project.id).catch(() => null),
          tasksApi.list(project.id, { archived: false }).catch(() => null),
          milestonesApi.list(project.id).catch(() => null),
        ])
        return { project, health, tasks, milestones }
      }),
    )
  }, [sort])

  const loaded = useMemo(() => projects.data ?? [], [projects.data])
  const visible = useMemo(() => applyFilters(loaded, filters), [loaded, filters])
  const attention = useMemo(() => collectAttentionTasks(loaded, today), [loaded, today])
  const upcoming = useMemo(() => upcomingItems(loaded, today), [loaded, today])
  const filtersActive = hasActiveFilters(filters)

  if (summary.error) return <ErrorState message={summary.error} onRetry={summary.reload} />

  const isEmptyOrg = summary.data && summary.data.activeProjectCount === 0 && summary.data.archivedProjectCount === 0
  const reloadAll = () => {
    summary.reload()
    projects.reload()
  }

  return (
    <div className="hb">
      <HeroSection greeting={greetingForHour(new Date().getHours())} name={identity ? identity.userId.slice(0, 8) : null} />

      {/* ---------- Summary cards ---------- */}
      <section className="hb-stats rise-in" aria-label="Summary">
        {summary.loading &&
          Array.from({ length: 4 }).map((_, i) => (
            <div className="hb-stat" key={i} aria-hidden="true">
              <Skeleton width={48} height={48} />
              <Skeleton width={60} height={26} />
            </div>
          ))}
        {summary.data && (
          <>
            <StatCard icon="projects" tone="blue" value={summary.data.activeProjectCount} label="Total Projects" hint="All projects that are not archived, in any status. Opens the project list." to="/projects" />
            <StatCard icon="overview" tone="purple" value={summary.data.byStatus.ACTIVE} label="Active" hint="Projects whose status is Active. Filters the list below." active={filters.status === 'ACTIVE'} onClick={() => toggleStatus('ACTIVE')} />
            <StatCard icon="phase" tone="amber" value={summary.data.byStatus.PLANNING} label="Planning" hint="Projects still in planning. Filters the list below." active={filters.status === 'PLANNING'} onClick={() => toggleStatus('PLANNING')} />
            <StatCard icon="check" tone="green" value={summary.data.byStatus.COMPLETED} label="Completed" hint="Projects marked completed. Filters the list below." active={filters.status === 'COMPLETED'} onClick={() => toggleStatus('COMPLETED')} />
          </>
        )}
      </section>

      {!summary.loading && isEmptyOrg && (
        <section className="hb-panel hb-empty-org rise-in">
          <span className="hb-empty-icon tone-info">
            <Icon name="projects" size={20} />
          </span>
          <h2>Welcome — let&apos;s set up your first project</h2>
          <p>A project holds your tasks, team and dates. It only needs a name to get started.</p>
          <button type="button" className="hb-primary-btn" onClick={() => setCreateOpen(true)}>
            <Icon name="plus" size={16} /> Create your first project
          </button>
        </section>
      )}

      {summary.data && !isEmptyOrg && (
        <>
          {/* ---------- Attention + calendar / upcoming ---------- */}
          <div className="hb-main rise-in">
            <div className="hb-main-left">
              {projects.error ? (
                <section className="hb-panel">
                  <ErrorState message={projects.error} onRetry={projects.reload} />
                </section>
              ) : (
                <AttentionPanel items={loaded} attention={attention} loading={projects.loading} />
              )}
            {/* ---------- Your Projects ---------- */}
            <section className="hb-panel hb-projects rise-in" id="hb-projects" aria-labelledby="hb-projects-title">
              <header className="hb-panel-head hb-projects-head">
                <span className="hb-panel-icon tone-info">
                  <Icon name="projects" size={20} />
                </span>
                <div className="hb-panel-titles">
                  <h2 id="hb-projects-title">Your Projects</h2>
                  <p>
                    {projects.data
                      ? filtersActive
                        ? `Showing ${visible.length} of ${loaded.length} loaded · ${summary.data.activeProjectCount} active in total`
                        : `${loaded.length} most recent of ${summary.data.activeProjectCount} active`
                      : 'A quick overview of your projects'}
                  </p>
                </div>
                <div className="hb-filters hb-projects-filters" role="search" aria-label="Find projects">
                  <div className="hb-search">
                    <Icon name="search" size={15} className="hb-search-icon" />
                    <input
                      type="search"
                      aria-label="Search projects by name"
                      placeholder="Search projects…"
                      value={filters.query}
                      onChange={(e) => setFilters((f) => ({ ...f, query: e.target.value }))}
                    />
                    {filters.query && (
                      <IconButton icon="close" label="Clear search" size={13} className="hb-search-clear" onClick={() => setFilters((f) => ({ ...f, query: '' }))} />
                    )}
                  </div>
                  <select aria-label="Status" value={filters.status} onChange={(e) => setFilters((f) => ({ ...f, status: e.target.value as ProjectStatus | '' }))}>
                    <option value="">Status</option>
                    {PROJECT_STATUSES.map((s) => (
                      <option key={s} value={s}>
                        {humanizeToken(s)}
                      </option>
                    ))}
                  </select>
                  <select aria-label="Sort by" value={sort} onChange={(e) => setSort(e.target.value as SortValue)}>
                    {SORT_OPTIONS.map((o) => (
                      <option key={o.value} value={o.value}>
                        Sort by: {o.label}
                      </option>
                    ))}
                  </select>
                  <div className="hb-view" role="group" aria-label="Layout">
                    <IconButton icon="dashboard" label="Grid view" active={view === 'grid'} onClick={() => setView('grid')} />
                    <IconButton icon="list" label="List view" active={view === 'list'} onClick={() => setView('list')} />
                  </div>
                  <button type="button" className="hb-primary-btn hb-primary-btn-sm" onClick={() => setCreateOpen(true)}>
                    <Icon name="plus" size={15} /> New Project
                  </button>
                </div>
              </header>

              {filtersActive && (
                <div className="hb-active-filters" aria-live="polite">
                  {filters.query.trim() && <Badge tone="primary">name contains &ldquo;{filters.query.trim()}&rdquo;</Badge>}
                  {filters.status && <Badge tone="primary">status {humanizeToken(filters.status)}</Badge>}
                  <button type="button" className="btn btn-sm btn-ghost" onClick={() => setFilters(EMPTY_FILTERS)}>
                    <Icon name="close" size={13} /> Reset filters
                  </button>
                </div>
              )}

              {projects.loading && (
                <ul className="hb-grid" aria-hidden="true">
                  {Array.from({ length: 4 }).map((_, i) => (
                    <li className="hb-card" key={i}>
                      <Skeleton width={44} height={44} />
                      <Skeleton width="60%" height={16} />
                      <Skeleton width="90%" height={8} />
                    </li>
                  ))}
                </ul>
              )}
              {projects.data && visible.length === 0 && (
                <div className="hb-empty" role="status">
                  <span className="hb-empty-icon tone-neutral">
                    <Icon name="search" size={18} />
                  </span>
                  <p>{filtersActive ? 'No projects match these filters.' : 'No projects loaded.'}</p>
                  {filtersActive && (
                    <button type="button" className="btn btn-sm" onClick={() => setFilters(EMPTY_FILTERS)}>
                      Reset filters
                    </button>
                  )}
                </div>
              )}
              {visible.length > 0 && (
                <ul className={view === 'grid' ? 'hb-grid' : 'hb-list'}>
                  {visible.map(({ project, health, tasks, milestones }, i) => {
                    const level = healthLevel(health)
                    const progress = progressPercent(tasks)
                    const liveTasks = tasks ? tasks.filter((t) => !t.archived).length : null
                    const liveMilestones = milestones ? milestones.filter((m) => !m.archived).length : null
                    const latestDue = latestDueDate(tasks)
                    return (
                      <li key={project.id} className={`hb-card card-tone-${CARD_TONES[i % CARD_TONES.length]}`} style={{ animationDelay: `${Math.min(i, 8) * 40}ms` }}>
                        <div className="hb-card-top">
                          <span className="hb-card-icon" aria-hidden="true">
                            <Icon name="projects" size={18} />
                          </span>
                          <span className="hb-card-badges">
                            {(level === 'critical' || level === 'warning') && (
                              <Badge tone={level === 'critical' ? 'danger' : 'warning'}>{HEALTH_LABEL[level]}</Badge>
                            )}
                            <ProjectStatusBadge status={project.status} />
                          </span>
                        </div>
                        <Link to={`/projects/${project.id}/overview`} className="hb-card-title">
                          {project.name}
                        </Link>
                        {progress !== null ? (
                          <div className="hb-progress">
                            <div className="hb-progress-bar" role="progressbar" aria-label="Tasks completed" aria-valuenow={progress} aria-valuemin={0} aria-valuemax={100}>
                              <span style={{ width: `${progress}%` }} />
                            </div>
                            <span className="hb-progress-value">{progress}%</span>
                          </div>
                        ) : (
                          <p className="hb-card-noprogress text-faint">{tasks ? 'No tasks yet' : 'Progress unavailable'}</p>
                        )}
                        <div className="hb-card-meta">
                          <span title="Tasks">
                            <Icon name="tasks" size={13} /> {liveTasks ?? '–'} {liveTasks === 1 ? 'task' : 'tasks'}
                          </span>
                          <span title="Milestones">
                            <Icon name="flag" size={13} /> {liveMilestones ?? '–'} {liveMilestones === 1 ? 'milestone' : 'milestones'}
                          </span>
                          <span title="Latest task due date">
                            <Icon name="calendar" size={13} /> {latestDue ? formatDate(latestDue) : 'No due dates'}
                          </span>
                        </div>
                      </li>
                    )
                  })}
                </ul>
              )}
            </section>
            </div>
            <div className="hb-main-right">
              <MiniCalendarCard projects={loaded} projectsLoading={projects.loading} today={today} onTaskCreated={reloadAll} />
              <UpcomingCard items={upcoming} loading={projects.loading} />
            </div>
          </div>

          {/* ---------- Lower widgets ---------- */}
          <div className="hb-lower rise-in">
            <ProjectStatusCard byStatus={summary.data.byStatus} loading={summary.loading} />
            <RecentActivityCard />
            <AIRecommendationsCard projects={loaded} projectsLoading={projects.loading} />
          </div>
        </>
      )}

      {createOpen && (
        <CreateProjectDialog
          onClose={() => setCreateOpen(false)}
          onCreated={() => {
            setCreateOpen(false)
            reloadAll()
          }}
        />
      )}
    </div>
  )

  function toggleStatus(status: ProjectStatus) {
    setFilters((f) => ({ ...f, status: f.status === status ? '' : status }))
    document.getElementById('hb-projects')?.scrollIntoView?.({ behavior: 'smooth', block: 'start' })
  }
}

function StatCard({
  icon,
  tone,
  value,
  label,
  hint,
  to,
  onClick,
  active,
}: {
  icon: IconName
  tone: (typeof CARD_TONES)[number]
  value: number
  label: string
  hint: string
  to?: string
  onClick?: () => void
  active?: boolean
}) {
  const body = (
    <>
      <span className="hb-stat-icon">
        <Icon name={icon} size={22} />
      </span>
      <span className="hb-stat-text">
        <span className="hb-stat-value">{value}</span>
        <span className="hb-stat-label">{label}</span>
      </span>
      {/* Decorative only — the backend has no trend data, so there is no sparkline or delta. */}
      <svg className="hb-stat-wave" viewBox="0 0 200 60" preserveAspectRatio="none" aria-hidden="true" focusable="false">
        <path d="M0 42 C 28 18, 58 58, 96 34 S 160 8, 200 30 V60 H0 Z" fill="currentColor" />
        <path d="M0 52 C 40 30, 70 62, 110 44 S 172 22, 200 40 V60 H0 Z" fill="currentColor" opacity="0.6" />
      </svg>
    </>
  )
  const cls = `hb-stat card-tone-${tone}${active ? ' active' : ''}`
  if (to) {
    return (
      <Link to={to} className={cls} title={hint}>
        {body}
      </Link>
    )
  }
  return (
    <button type="button" className={cls} title={hint} aria-pressed={active} onClick={onClick}>
      {body}
    </button>
  )
}
