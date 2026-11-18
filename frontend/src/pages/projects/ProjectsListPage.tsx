import { useEffect, useMemo, useRef, useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import '../../styles/ops.css'
import { projectsApi } from '../../api/projects'
import { membersApi } from '../../api/members'
import { useAsyncData } from '../../hooks/useAsyncData'
import { usePeople } from '../../context/PeopleContext'
import { ErrorState } from '../../components/common/ErrorState'
import { Icon, type IconName } from '../../components/common/Icon'
import { CreateProjectDialog } from './CreateProjectDialog'
import { todayIso } from '../../utils/format'
import { periodFor, projectProgress, tableDate, type PeriodKind } from '../dashboard/opsDashboard'
import type { ProjectStatus, ProjectSummary } from '../../types/project'
import { Avatar } from '../../components/common/Avatar'

type View = 'list' | 'board'
type StatusFilter = '' | ProjectStatus
type TimeFilter = '' | 'month' | 'quarter' | 'fy' | 'last30'

const PER_PAGE_KEY = 'projectmodule.projectsPerPage'
const VIEW_KEY = 'projectmodule.projectsView2'
const PER_PAGE_OPTIONS = [5, 10, 20, 50]

const STATUS_LABEL: Record<ProjectStatus, string> = {
  ACTIVE: 'Active',
  ON_HOLD: 'On Hold',
  COMPLETED: 'Completed',
}
const BOARD_COLUMNS: ProjectStatus[] = ['ACTIVE', 'ON_HOLD', 'COMPLETED']

function readStored<T>(key: string, fallback: T, valid: (v: unknown) => boolean): T {
  try {
    const raw = window.localStorage.getItem(key)
    const value = raw === null ? null : JSON.parse(raw)
    return value !== null && valid(value) ? (value as T) : fallback
  } catch {
    return fallback
  }
}

function store(key: string, value: unknown) {
  try {
    window.localStorage.setItem(key, JSON.stringify(value))
  } catch {
    // Remembered for this visit only.
  }
}

/** A project's picture: chosen from what its name is about, with a colour of its own. */
export function projectLook(name: string): { icon: IconName; tone: string } {
  const n = name.toLowerCase()
  if (/web|site|portal|app|design|ui/.test(n)) return { icon: 'monitor', tone: 'purple' }
  if (/launch|product|release|sale/.test(n)) return { icon: 'megaphone', tone: 'orange' }
  if (/market|campaign|social|ads|brand/.test(n)) return { icon: 'team', tone: 'blue' }
  if (/operation|setup|process|ops|maintenance/.test(n)) return { icon: 'settings', tone: 'green' }
  if (/event|meeting|conference|calendar|schedule/.test(n)) return { icon: 'calendar', tone: 'blue' }
  const tones = ['green', 'blue', 'purple', 'orange', 'pink']
  return { icon: 'folder', tone: tones[[...n].reduce((sum, ch) => sum + ch.charCodeAt(0), 0) % tones.length] }
}

/** Tags alternate blue and purple, as in the design. */
const TAG_TONES = ['blue', 'purple', 'pink']

/**
 * Projects: summary cards, search and filters, then a list or board of projects with their
 * customer, owner, progress and dates. With {@code archived}, the same page lists archived
 * projects, read only.
 */
export function ProjectsListPage({ archived = false }: { archived?: boolean }) {
  const navigate = useNavigate()
  const { people, nameOf } = usePeople()
  const [view, setView] = useState<View>(() => readStored<View>(VIEW_KEY, 'list', (v) => v === 'list' || v === 'board'))
  const [perPage, setPerPage] = useState<number>(() => readStored<number>(PER_PAGE_KEY, 5, (v) => PER_PAGE_OPTIONS.includes(v as number)))
  const [page, setPage] = useState(0)
  const [query, setQuery] = useState('')
  const [status, setStatus] = useState<StatusFilter>('')
  const [member, setMember] = useState('')
  const [time, setTime] = useState<TimeFilter>('')
  const [createOpen, setCreateOpen] = useState(false)

  const live = useAsyncData(() => projectsApi.list(0, 200, 'updatedAt,DESC'), [])
  const archivedList = useAsyncData(() => projectsApi.list(0, 200, 'updatedAt,DESC', true), [])
  const rows = useMemo(() => (archived ? archivedList.data?.content : live.data?.content) ?? [], [archived, archivedList.data, live.data])

  // Who is on each project, for the "All Members" filter.
  const memberships = useAsyncData(async () => {
    const entries = await Promise.all(
      rows.map(async (p) => [p.id, await membersApi.list(p.id).then((m) => m.map((x) => x.userId)).catch(() => [p.ownerId])] as const),
    )
    return new Map(entries)
  }, [rows])

  useEffect(() => setPage(0), [query, status, member, time, perPage, archived])

  const filtered = useMemo(() => {
    const q = query.trim().toLowerCase()
    const range = time ? periodFor(time as PeriodKind, todayIso()) : null
    return rows.filter((p) => {
      if (q && ![p.name, p.customerContact ?? '', ...(p.tags ?? [])].some((v) => v.toLowerCase().includes(q))) return false
      if (status && p.status !== status) return false
      if (member) {
        const team = memberships.data?.get(p.id) ?? [p.ownerId]
        if (!team.includes(member)) return false
      }
      if (range && range.from && range.to) {
        const day = p.startDate ?? p.createdAt?.slice(0, 10) ?? ''
        if (!day || day < range.from || day > range.to) return false
      }
      return true
    })
  }, [rows, query, status, member, time, memberships.data])

  const liveRows = live.data?.content ?? []
  const counts = {
    total: liveRows.length,
    active: liveRows.filter((p) => p.status === 'ACTIVE').length,
    onHold: liveRows.filter((p) => p.status === 'ON_HOLD').length,
    completed: liveRows.filter((p) => p.status === 'COMPLETED').length,
    archived: archivedList.data?.totalElements ?? 0,
  }

  const pages = Math.max(1, Math.ceil(filtered.length / perPage))
  const current = Math.min(page, pages - 1)
  const shown = filtered.slice(current * perPage, current * perPage + perPage)
  const error = archived ? archivedList.error : live.error
  const loading = archived ? archivedList.loading : live.loading

  function pickStatus(next: StatusFilter) {
    if (archived) navigate('/projects')
    setStatus((s) => (s === next ? '' : next))
  }

  return (
    <div className="ops-page ops-projects">
      <header className="ops-page-head">
        <div>
          <h1>{archived ? 'Archived Projects' : 'Projects'}</h1>
          <p>{archived ? 'Projects that were archived. They are kept, read only.' : 'Create, manage and track all your business projects.'}</p>
        </div>
        <div className="ops-head-actions">
          <div className="ops-toggle" role="group" aria-label="Layout">
            <button type="button" className={view === 'list' ? 'on' : ''} aria-pressed={view === 'list'} onClick={() => { setView('list'); store(VIEW_KEY, 'list') }}>
              <Icon name="list" size={22} /> List
            </button>
            <button type="button" className={view === 'board' ? 'on' : ''} aria-pressed={view === 'board'} onClick={() => { setView('board'); store(VIEW_KEY, 'board') }}>
              <Icon name="kanban" size={22} /> Board
            </button>
          </div>
          {!archived && (
            <button type="button" className="ops-primary" onClick={() => setCreateOpen(true)}>
              <Icon name="plus" size={22} /> Create Project
            </button>
          )}
        </div>
      </header>

      <section className="ops-stats ops-stats-5" aria-label="Project counts">
        <CountTile icon="folder" tone="green" label="Total Projects" value={counts.total} active={false} onClick={() => pickStatus('')} />
        <CountTile icon="play" tone="green" label="Active" value={counts.active} active={!archived && status === 'ACTIVE'} onClick={() => pickStatus('ACTIVE')} />
        <CountTile icon="pause" tone="orange" label="On Hold" value={counts.onHold} active={!archived && status === 'ON_HOLD'} onClick={() => pickStatus('ON_HOLD')} />
        <CountTile icon="checkCircle" tone="blue" label="Completed" value={counts.completed} active={!archived && status === 'COMPLETED'} onClick={() => pickStatus('COMPLETED')} />
        <CountTile icon="archive" tone="purple" label="Archived" value={counts.archived} active={archived} onClick={() => navigate(archived ? '/projects' : '/projects/archived')} />
      </section>

      <div className="ops-filters" role="search" aria-label="Find projects">
        <label className="ops-search">
          <Icon name="search" size={22} />
          <input type="search" placeholder="Search projects by name, customer or tag..." aria-label="Search projects by name, customer or tag" value={query} onChange={(e) => setQuery(e.target.value)} />
        </label>
        <FilterSelect label="Status" value={status} onChange={(v) => setStatus(v as StatusFilter)}
          options={[{ value: '', label: 'All Status' }, ...(Object.keys(STATUS_LABEL) as ProjectStatus[]).map((s) => ({ value: s, label: STATUS_LABEL[s] }))]} />
        <FilterSelect label="Members" value={member} onChange={setMember}
          options={[{ value: '', label: 'All Members' }, ...people.map((p) => ({ value: p.id, label: p.name }))]} />
        <FilterSelect label="Time" value={time} onChange={(v) => setTime(v as TimeFilter)} icon="calendar"
          options={[
            { value: '', label: 'All Time' },
            { value: 'month', label: 'This Month' },
            { value: 'quarter', label: 'This Quarter' },
            { value: 'fy', label: 'This Financial Year' },
            { value: 'last30', label: 'Last 30 Days' },
          ]} />
      </div>

      {error ? (
        <ErrorState message={error} onRetry={archived ? archivedList.reload : live.reload} />
      ) : view === 'list' ? (
        <section className="ops-card ops-table-card">
          <div className="ops-table-scroll">
            <table className="ops-table">
              <thead>
                <tr>
                  <th>Project Name</th>
                  <th>Customer / Contact</th>
                  <th>Owner</th>
                  <th>Progress</th>
                  <th>Tasks</th>
                  <th>Start / Due</th>
                  <th>Status</th>
                  <th className="ops-th-actions">Actions</th>
                </tr>
              </thead>
              <tbody>
                {loading && rows.length === 0 && (
                  <tr><td colSpan={8} className="ops-empty">Loading projects…</td></tr>
                )}
                {!loading && shown.length === 0 && (
                  <tr><td colSpan={8} className="ops-empty">{rows.length === 0 ? (archived ? 'No archived projects.' : 'No projects yet. Create your first project.') : 'No projects match these filters.'}</td></tr>
                )}
                {shown.map((p) => (
                  <ProjectRow key={p.id} project={p} ownerName={nameOf(p.ownerId)} archived={archived} />
                ))}
              </tbody>
            </table>
          </div>
          <footer className="ops-table-foot">
            <span>
              {filtered.length === 0
                ? 'Showing 0 projects'
                : `Showing ${current * perPage + 1} – ${Math.min(filtered.length, current * perPage + perPage)} of ${filtered.length} projects`}
            </span>
            <div className="ops-pager">
              <label className="ops-select ops-select-sm">
                <select aria-label="Projects per page" value={perPage} onChange={(e) => { const n = Number(e.target.value); setPerPage(n); store(PER_PAGE_KEY, n) }}>
                  {PER_PAGE_OPTIONS.map((n) => (
                    <option key={n} value={n}>{n} per page</option>
                  ))}
                </select>
                <Icon name="chevronDown" size={18} className="ops-select-chevron" />
              </label>
              <button type="button" className="ops-page-btn" aria-label="Previous page" disabled={current === 0} onClick={() => setPage(current - 1)}>
                <Icon name="chevronLeft" size={18} />
              </button>
              {pageNumbers(current, pages).map((n, i) =>
                n === null ? (
                  <span key={`gap${i}`} className="ops-page-gap">…</span>
                ) : (
                  <button key={n} type="button" className={`ops-page-btn${n === current ? ' on' : ''}`} aria-current={n === current ? 'page' : undefined} onClick={() => setPage(n)}>
                    {n + 1}
                  </button>
                ),
              )}
              <button type="button" className="ops-page-btn" aria-label="Next page" disabled={current >= pages - 1} onClick={() => setPage(current + 1)}>
                <Icon name="chevronRight" size={18} />
              </button>
            </div>
          </footer>
        </section>
      ) : (
        <div className="ops-board">
          {(archived ? ['ARCHIVED' as const] : BOARD_COLUMNS).map((col) => {
            const items = col === 'ARCHIVED' ? filtered : filtered.filter((p) => p.status === col)
            return (
              <section key={col} className="ops-board-col">
                <header>
                  <span className={`ops-status status-${col.toLowerCase()}`}>{col === 'ARCHIVED' ? 'Archived' : STATUS_LABEL[col]}</span>
                  <span className="ops-board-count">{items.length}</span>
                </header>
                {items.length === 0 && <p className="ops-empty">No projects</p>}
                {items.map((p) => (
                  <BoardCard key={p.id} project={p} ownerName={nameOf(p.ownerId)} archived={archived} />
                ))}
              </section>
            )
          })}
        </div>
      )}

      {createOpen && (
        <CreateProjectDialog
          onClose={() => setCreateOpen(false)}
          onCreated={() => {
            setCreateOpen(false)
            live.reload()
          }}
        />
      )}
    </div>
  )
}

/** Page numbers to show: all when few, otherwise the ends and the neighbours of the current page. */
function pageNumbers(current: number, total: number): (number | null)[] {
  if (total <= 7) return Array.from({ length: total }, (_, i) => i)
  const keep = new Set([0, 1, total - 2, total - 1, current - 1, current, current + 1].filter((n) => n >= 0 && n < total))
  const sorted = [...keep].sort((a, b) => a - b)
  const out: (number | null)[] = []
  sorted.forEach((n, i) => {
    if (i > 0 && n - sorted[i - 1] > 1) out.push(null)
    out.push(n)
  })
  return out
}

function CountTile({ icon, tone, label, value, active, onClick }: { icon: IconName; tone: string; label: string; value: number; active: boolean; onClick: () => void }) {
  return (
    <button type="button" className={`ops-tile${active ? ' on' : ''}`} onClick={onClick} aria-pressed={active}>
      <span className={`ops-tile-icon tone-${tone}`}>
        <Icon name={icon} size={30} />
      </span>
      <span className="ops-tile-text">
        <span className="ops-tile-label">{label}</span>
        <strong className="ops-tile-value">{value}</strong>
      </span>
    </button>
  )
}

function FilterSelect({ label, value, onChange, options, icon }: {
  label: string
  value: string
  onChange: (v: string) => void
  options: { value: string; label: string }[]
  icon?: IconName
}) {
  return (
    <label className={`ops-select${icon ? ' ops-select-icon' : ''}`}>
      {icon && <Icon name={icon} size={20} />}
      <select aria-label={label} value={value} onChange={(e) => onChange(e.target.value)}>
        {options.map((o) => (
          <option key={o.value} value={o.value}>{o.label}</option>
        ))}
      </select>
      <Icon name="chevronDown" size={18} className="ops-select-chevron" />
    </label>
  )
}

function StatusPill({ project, archived }: { project: ProjectSummary; archived: boolean }) {
  if (archived) return <span className="ops-status status-archived">Archived</span>
  return <span className={`ops-status status-${project.status.toLowerCase()}`}>{STATUS_LABEL[project.status]}</span>
}

function Tags({ tags }: { tags: string[] }) {
  if (tags.length === 0) return null
  return (
    <span className="ops-tags">
      {tags.slice(0, 3).map((t, i) => (
        <span key={t} className={`ops-tag tone-${TAG_TONES[i % TAG_TONES.length]}`}>{t}</span>
      ))}
      {tags.length > 3 && <span className="ops-tag tone-gray">+{tags.length - 3}</span>}
    </span>
  )
}

function ProjectRow({ project: p, ownerName, archived }: { project: ProjectSummary; ownerName: string; archived: boolean }) {
  const look = projectLook(p.name)
  const pct = projectProgress(p)
  return (
    <tr>
      <td>
        <div className="ops-project-cell">
          <span className={`ops-project-icon tone-${look.tone}`}>
            <Icon name={look.icon} size={26} />
          </span>
          <span>
            {archived ? <strong>{p.name}</strong> : <Link to={`/projects/${p.id}`} className="ops-project-name">{p.name}</Link>}
            <Tags tags={p.tags ?? []} />
          </span>
        </div>
      </td>
      <td>{p.customerContact || <span className="ops-muted">—</span>}</td>
      <td>
        <span className="ops-owner">
          <Avatar userId={p.ownerId} name={ownerName} />
          {ownerName}
        </span>
      </td>
      <td>
        <span className="ops-progress-cell">
          <span className="ops-bar"><span style={{ width: `${pct}%` }} /></span>
          <span>{pct}%</span>
        </span>
      </td>
      <td>{p.completedTaskCount ?? 0} / {p.taskCount ?? 0}</td>
      <td>
        <span className="ops-dates">
          <span>{p.startDate ? tableDate(p.startDate) : '—'}</span>
          <span>{p.targetEndDate ? tableDate(p.targetEndDate) : '—'}</span>
        </span>
      </td>
      <td><StatusPill project={p} archived={archived} /></td>
      <td className="ops-actions">
        {archived ? (
          <span className="ops-muted" title="Archived projects can't be opened">—</span>
        ) : (
          <>
            <Link to={`/projects/${p.id}`} className="ops-icon-btn" aria-label={`Open ${p.name}`} title="Open">
              <Icon name="eye" size={22} />
            </Link>
            <RowMenu project={p} />
          </>
        )}
      </td>
    </tr>
  )
}

function RowMenu({ project }: { project: ProjectSummary }) {
  const [open, setOpen] = useState(false)
  const ref = useRef<HTMLDivElement>(null)
  useEffect(() => {
    if (!open) return
    const close = (e: MouseEvent) => ref.current && !ref.current.contains(e.target as Node) && setOpen(false)
    document.addEventListener('mousedown', close)
    return () => document.removeEventListener('mousedown', close)
  }, [open])
  const base = `/projects/${project.id}`
  return (
    <div className="ops-menu" ref={ref}>
      <button type="button" className="ops-icon-btn" aria-label={`More actions for ${project.name}`} aria-expanded={open} onClick={() => setOpen((v) => !v)}>
        <Icon name="more" size={22} />
      </button>
      {open && (
        <div className="ops-menu-list" role="menu">
          <Link role="menuitem" to={`${base}/overview`}>Overview</Link>
          <Link role="menuitem" to={`${base}/tasks`}>Tasks</Link>
          <Link role="menuitem" to={`${base}/kanban`}>Board</Link>
          <Link role="menuitem" to={`${base}/members`}>Team</Link>
          <Link role="menuitem" to={`${base}/reports`}>Reports</Link>
          <Link role="menuitem" to={`${base}/overview`} state={{ edit: true }}>Edit or archive</Link>
        </div>
      )}
    </div>
  )
}

function BoardCard({ project: p, ownerName, archived }: { project: ProjectSummary; ownerName: string; archived: boolean }) {
  const look = projectLook(p.name)
  const pct = projectProgress(p)
  const body = (
    <>
      <div className="ops-board-card-top">
        <span className={`ops-project-icon ops-project-icon-sm tone-${look.tone}`}>
          <Icon name={look.icon} size={20} />
        </span>
        <strong>{p.name}</strong>
      </div>
      <Tags tags={p.tags ?? []} />
      {p.customerContact && <span className="ops-muted">{p.customerContact}</span>}
      <span className="ops-progress-cell">
        <span className="ops-bar"><span style={{ width: `${pct}%` }} /></span>
        <span>{pct}%</span>
      </span>
      <div className="ops-board-card-foot">
        <span className="ops-owner">
          <Avatar userId={p.ownerId} name={ownerName} className="ops-avatar ops-avatar-sm" />
          {ownerName}
        </span>
        <span className="ops-muted">{p.targetEndDate ? tableDate(p.targetEndDate) : ''}</span>
      </div>
    </>
  )
  return archived ? <div className="ops-board-card">{body}</div> : <Link to={`/projects/${p.id}`} className="ops-board-card">{body}</Link>
}
