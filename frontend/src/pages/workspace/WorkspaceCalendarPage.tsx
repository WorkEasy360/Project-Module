import { useMemo, useState } from 'react'
import '../../styles/calendar.css'
import '../../styles/workspace-calendar.css'
import { calendarApi } from '../../api/calendar'
import { tasksApi } from '../../api/tasks'
import { milestonesApi } from '../../api/milestones'
import { useAllProjects, usePerProject } from '../../hooks/useWorkspaceData'
import { PageHeader } from '../../components/common/PageHeader'
import { ErrorState } from '../../components/common/ErrorState'
import { EmptyState } from '../../components/common/EmptyState'
import { Icon, type IconName } from '../../components/common/Icon'
import { IconButton } from '../../components/common/IconButton'
import { Modal } from '../../components/common/Modal'
import { Skeleton } from '../../components/common/Skeleton'
import { SelectField, FormActions } from '../../components/forms/fields'
import { CreateTaskDialog } from '../projects/CreateTaskDialog'
import { MonthGrid } from '../projects/tabs/calendar/MonthGrid'
import { EventCard } from '../projects/tabs/calendar/CalendarEvent'
import { EventDetailDialog } from '../projects/tabs/calendar/EventDetailDialog'
import {
  addMonths,
  buildMonthGrid,
  enrichEntries,
  entriesByDay,
  entryKey,
  longDateLabel,
  monthLabel,
  monthRange,
  summarize,
  todayIso,
  yearMonthOf,
  type EnrichedEntry,
  type YearMonth,
} from '../projects/tabs/calendar/calendarUtils'
import type { Milestone, Task } from '../../types/work'
import { FailedProjectsNotice, TruncationNote } from './WorkspaceNotices'

type Kind = 'TASK' | 'MILESTONE' | 'PHASE'

const KINDS: { kind: Kind; label: string; icon: IconName; tone: string }[] = [
  { kind: 'TASK', label: 'Tasks', icon: 'tasks', tone: 'info' },
  { kind: 'MILESTONE', label: 'Milestones', icon: 'milestone', tone: 'warning' },
  { kind: 'PHASE', label: 'Phases', icon: 'phase', tone: 'neutral' },
]

const LEGEND: { tone: string; label: string }[] = [
  { tone: 'info', label: 'Task' },
  { tone: 'warning', label: 'Milestone' },
  { tone: 'danger', label: 'Past due · blocked · at risk' },
  { tone: 'success', label: 'Completed' },
  { tone: 'neutral', label: 'Phase' },
]

interface ProjectRefs {
  tasks: Task[] | null
  milestones: Milestone[] | null
}

/**
 * Workspace Calendar: every project's read-only /calendar window for the visible month, laid out
 * on one grid. The backend has no cross-project calendar, so this loads a page of projects and
 * calls `/projects/{id}/calendar?from&to` for each (Promise.allSettled — one failing project
 * never blanks the month). Task/milestone lists are loaded the same way so chips show the real
 * stored status and assignee, exactly like the per-project calendar. The only write is creating
 * a task on the selected day in a project the user picks.
 */
export function WorkspaceCalendarPage() {
  const today = todayIso()
  const [ym, setYm] = useState<YearMonth>(() => yearMonthOf(today))
  const [selected, setSelected] = useState(today)
  const [kinds, setKinds] = useState<Record<Kind, boolean>>({ TASK: true, MILESTONE: true, PHASE: false })
  const [projectFilter, setProjectFilter] = useState('')
  const [detail, setDetail] = useState<EnrichedEntry | null>(null)
  const [pickOpen, setPickOpen] = useState(false)
  const [pickProjectId, setPickProjectId] = useState('')
  const [createIn, setCreateIn] = useState<string | null>(null)

  const projects = useAllProjects()
  const projectList = projects.data?.content ?? null
  const { from, to } = monthRange(ym)
  const calendars = usePerProject(projectList, (id) => calendarApi.get(id, from, to), [from, to])
  const refs = usePerProject<ProjectRefs>(projectList, async (id) => {
    const [tasks, milestones] = await Promise.all([
      tasksApi.list(id, { archived: false }).catch(() => null),
      milestonesApi.list(id).catch(() => null),
    ])
    return { tasks, milestones }
  })

  const entries = useMemo(() => {
    const out: EnrichedEntry[] = []
    for (const project of projectList ?? []) {
      const calendar = calendars.byProject[project.id]
      if (!calendar) continue
      const r = refs.byProject[project.id]
      out.push(...enrichEntries(calendar.entries, r?.tasks ?? null, r?.milestones ?? null, { id: project.id, name: project.name }))
    }
    return out
  }, [projectList, calendars.byProject, refs.byProject])

  const inProject = useMemo(
    () => (projectFilter ? entries.filter((e) => e.projectId === projectFilter) : entries),
    [entries, projectFilter],
  )
  const kindCounts = useMemo(() => {
    const counts: Record<Kind, number> = { TASK: 0, MILESTONE: 0, PHASE: 0 }
    for (const e of inProject) if (e.entityType in counts) counts[e.entityType as Kind]++
    return counts
  }, [inProject])
  const visible = useMemo(() => inProject.filter((e) => kinds[e.entityType as Kind] ?? true), [inProject, kinds])
  const byDay = useMemo(() => entriesByDay(visible, from, to), [visible, from, to])
  const cells = useMemo(() => buildMonthGrid(ym, today), [ym, today])
  const summary = useMemo(() => summarize(visible, today), [visible, today])
  const selectedEntries = byDay.get(selected) ?? []

  // usePerProject keys its effect on the joined project ids, which is '' for both "not loaded yet"
  // and "no projects", so with zero projects its `loading` never settles — don't wait on it then.
  const hasProjects = (projectList?.length ?? 0) > 0
  const loading = projects.loading || (hasProjects && (calendars.loading || refs.loading))
  const monthKey = `${ym.year}-${ym.month}`

  function goTo(next: YearMonth) {
    setYm(next)
    setSelected(monthRange(next).from)
  }
  function goToday() {
    setYm(yearMonthOf(today))
    setSelected(today)
  }
  function startAddTask() {
    if (projectFilter) {
      setCreateIn(projectFilter)
      return
    }
    setPickProjectId('')
    setPickOpen(true)
  }
  function reloadAll() {
    calendars.reload()
    refs.reload()
  }

  if (projects.error) {
    return (
      <div className="wcal cal">
        <PageHeader title="Calendar" description="Every due date across your projects." />
        <ErrorState message={projects.error} onRetry={projects.reload} />
      </div>
    )
  }

  const dayLabel = longDateLabel(selected)

  return (
    <div className="wcal cal">
      <PageHeader
        title="Calendar"
        description="Every task and milestone due date across your projects, on their real dates. Select a day to see everything due on it."
        actions={
          <div className="cal-controls" role="toolbar" aria-label="Calendar navigation">
            <div className="cal-nav">
              <IconButton icon="chevronLeft" label="Previous month" onClick={() => goTo(addMonths(ym, -1))} />
              <button type="button" className="cal-period" aria-live="polite">
                {monthLabel(ym)}
              </button>
              <IconButton icon="chevronRight" label="Next month" onClick={() => goTo(addMonths(ym, 1))} />
            </div>
            <button type="button" className="btn btn-sm" onClick={goToday}>
              Today
            </button>
            <button type="button" className="btn btn-primary btn-sm" onClick={startAddTask} disabled={!hasProjects}>
              <Icon name="plus" size={14} /> Add task
            </button>
          </div>
        }
      />

      {/* ---------- Filters ---------- */}
      <div className="wcal-toolbar">
        <div className="wcal-kinds" role="group" aria-label="Show">
          {KINDS.map(({ kind, label, icon, tone }) => (
            <button
              key={kind}
              type="button"
              className={`wcal-kind tone-${tone}${kinds[kind] ? ' active' : ''}`}
              aria-pressed={kinds[kind]}
              onClick={() => setKinds((k) => ({ ...k, [kind]: !k[kind] }))}
            >
              <Icon name={icon} size={14} />
              {label}
              <span className="wcal-kind-count">{loading ? '…' : kindCounts[kind]}</span>
            </button>
          ))}
        </div>
        <select aria-label="Project" className="wcal-project" value={projectFilter} onChange={(e) => setProjectFilter(e.target.value)}>
          <option value="">All projects</option>
          {(projectList ?? []).map((p) => (
            <option key={p.id} value={p.id}>
              {p.name}
            </option>
          ))}
        </select>
        <ul className="wcal-legend list-plain" aria-label="Legend">
          {LEGEND.map((l) => (
            <li key={l.tone} className={`tone-${l.tone}`}>
              <span className="wcal-swatch" aria-hidden="true" />
              {l.label}
            </li>
          ))}
        </ul>
      </div>

      {/* ---------- Summary ---------- */}
      <div className="cal-summary" aria-label="This month at a glance">
        <SummaryCard icon="tasks" label="Tasks due this month" value={summary.tasks} tone="info" loading={loading} />
        <SummaryCard icon="delayed" label="Past due" value={summary.pastDue} tone={summary.pastDue > 0 ? 'danger' : 'neutral'} loading={loading} />
        <SummaryCard icon="milestone" label="Milestones" value={summary.milestones} tone="warning" loading={loading} />
        <SummaryCard icon="health" label="Completed" value={summary.completed} tone="success" loading={loading} />
      </div>

      {projects.data && <TruncationNote totalElements={projects.data.totalElements} what="due dates" />}
      {!loading && projectList && (
        <FailedProjectsNotice projects={projectList} errors={calendars.errors} what="the calendar" onRetry={calendars.reload} />
      )}

      {!loading && projectList && projectList.length === 0 ? (
        <section className="wcal-empty">
          <EmptyState
            title="No projects yet"
            description="Due dates come from tasks and milestones inside projects. Create a project to start planning."
          />
        </section>
      ) : (
        <div className="cal-body">
          <section className="cal-main" aria-busy={loading}>
            {loading ? (
              <div className="cal-skeleton" aria-hidden="true">
                {Array.from({ length: 42 }).map((_, i) => (
                  <Skeleton key={i} height={72} />
                ))}
              </div>
            ) : (
              <MonthGrid
                cells={cells}
                byDay={byDay}
                selected={selected}
                onSelectDay={setSelected}
                onSelectEntry={setDetail}
                animationKey={monthKey}
              />
            )}
          </section>

          <aside className="cal-side" aria-label="Selected day">
            <div className="cal-side-head">
              <span className="cal-side-date">{dayLabel}</span>
              {selected === today && <span className="cal-today-pill">Today</span>}
            </div>
            {loading ? (
              <Skeleton height={56} />
            ) : selectedEntries.length === 0 ? (
              <div className="cal-side-empty">
                <span className="cal-empty-art" aria-hidden="true" />
                <p>Nothing scheduled for this day.</p>
                {hasProjects && (
                  <button type="button" className="btn btn-sm" onClick={startAddTask}>
                    Add a task due {dayLabel.split(',')[0]}
                  </button>
                )}
              </div>
            ) : (
              <>
                <ul className="list-plain cal-side-list">
                  {selectedEntries.map((entry) => (
                    <li key={entryKey(entry)}>
                      <EventCard entry={entry} onSelect={setDetail} />
                    </li>
                  ))}
                </ul>
                {hasProjects && (
                  <button type="button" className="btn btn-sm btn-ghost wcal-side-add" onClick={startAddTask}>
                    <Icon name="plus" size={13} /> Add task on {dayLabel.split(',')[0]}
                  </button>
                )}
              </>
            )}
          </aside>
        </div>
      )}

      {detail && detail.projectId && <EventDetailDialog entry={detail} projectId={detail.projectId} onClose={() => setDetail(null)} />}

      {pickOpen && (
        <Modal title="Add a task in…" onClose={() => setPickOpen(false)}>
          <form
            className="form-grid"
            onSubmit={(e) => {
              e.preventDefault()
              if (!pickProjectId) return
              setPickOpen(false)
              setCreateIn(pickProjectId)
            }}
          >
            <p className="wcal-pick-hint">
              The task will be due <strong>{dayLabel}</strong>. Tasks belong to a project, so choose where it goes.
            </p>
            <SelectField
              id="wcal-pick-project"
              label="Project"
              value={pickProjectId}
              onChange={setPickProjectId}
              required
              allowEmpty
              emptyLabel="Choose a project"
              options={(projectList ?? []).map((p) => ({ value: p.id, label: p.name }))}
            />
            <FormActions>
              <button type="button" className="btn" onClick={() => setPickOpen(false)}>
                Cancel
              </button>
              <button type="submit" className="btn btn-primary" disabled={!pickProjectId}>
                Continue
              </button>
            </FormActions>
          </form>
        </Modal>
      )}
      {createIn && (
        <CreateTaskDialog
          projectId={createIn}
          initialDueDate={selected}
          onClose={() => setCreateIn(null)}
          onCreated={() => {
            setCreateIn(null)
            reloadAll()
          }}
        />
      )}
    </div>
  )
}

function SummaryCard({
  icon,
  label,
  value,
  tone,
  loading,
}: {
  icon: IconName
  label: string
  value: number
  tone: 'info' | 'danger' | 'warning' | 'success' | 'neutral'
  loading: boolean
}) {
  return (
    <div className={`cal-stat tone-${tone}`}>
      <span className="cal-stat-icon">
        <Icon name={icon} size={18} />
      </span>
      <span className="cal-stat-body">
        <span className="cal-stat-value">{loading ? <Skeleton width={28} height={22} /> : value}</span>
        <span className="cal-stat-label">{label}</span>
      </span>
    </div>
  )
}
