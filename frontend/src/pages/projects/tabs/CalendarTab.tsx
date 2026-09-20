import { useEffect, useMemo, useRef, useState } from 'react'
import '../../../styles/calendar.css'
import { useProjectWorkspace } from '../../../context/ProjectWorkspaceContext'
import { useAsyncData } from '../../../hooks/useAsyncData'
import { calendarApi } from '../../../api/calendar'
import { tasksApi } from '../../../api/tasks'
import { milestonesApi } from '../../../api/milestones'
import { ErrorState } from '../../../components/common/ErrorState'
import { Icon, type IconName } from '../../../components/common/Icon'
import { IconButton } from '../../../components/common/IconButton'
import { Skeleton } from '../../../components/common/Skeleton'
import { CreateTaskDialog } from '../CreateTaskDialog'
import { CreateMilestoneDialog } from '../CreateMilestoneDialog'
import { MonthGrid } from './calendar/MonthGrid'
import { EventCard } from './calendar/CalendarEvent'
import { EventDetailDialog } from './calendar/EventDetailDialog'
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
} from './calendar/calendarUtils'

type View = 'month' | 'agenda'
type CreateKind = 'task' | 'milestone'

/**
 * Project Calendar. Reads the read-only /calendar window for the visible month and joins it
 * with the project's task and milestone lists so chips can show real status/assignee. Writes go
 * through the existing task and milestone APIs: create on a selected day ("+ Add" -> Task or
 * Milestone) and, from the detail dialog, change a due date or a task's status. Every write is
 * followed by a reload so the grid shows what the database holds.
 */
export function CalendarTab() {
  const { project, can } = useProjectWorkspace()
  const today = todayIso()
  const [ym, setYm] = useState<YearMonth>(() => yearMonthOf(today))
  const [selected, setSelected] = useState(today)
  const [view, setView] = useState<View>('month')
  const [detailKey, setDetailKey] = useState<string | null>(null)
  const [createKind, setCreateKind] = useState<CreateKind | null>(null)
  const [addMenuOpen, setAddMenuOpen] = useState(false)
  const addMenuRef = useRef<HTMLDivElement>(null)

  const { from, to } = monthRange(ym)
  const calendar = useAsyncData(() => calendarApi.get(project.id, from, to), [project.id, from, to])
  const tasks = useAsyncData(() => tasksApi.list(project.id, { archived: false }), [project.id])
  const milestones = useAsyncData(() => milestonesApi.list(project.id), [project.id])

  const entries = useMemo(
    () => enrichEntries(calendar.data?.entries ?? [], tasks.data, milestones.data),
    [calendar.data, tasks.data, milestones.data],
  )
  const byDay = useMemo(() => entriesByDay(entries, from, to), [entries, from, to])
  const cells = useMemo(() => buildMonthGrid(ym, today), [ym, today])
  const summary = useMemo(() => summarize(entries, today), [entries, today])
  const selectedEntries = byDay.get(selected) ?? []
  // The open detail is derived from the freshest entries so a reload after an edit updates it.
  const detail = useMemo(
    () => (detailKey ? (entries.find((e) => entryKey(e) === detailKey) ?? null) : null),
    [entries, detailKey],
  )
  const detailTask = detail?.entityType === 'TASK' ? (tasks.data?.find((t) => t.id === detail.id) ?? null) : null
  const detailMilestone =
    detail?.entityType === 'MILESTONE' ? (milestones.data?.find((m) => m.id === detail.id) ?? null) : null
  const canEdit = can('EDIT_PROJECT') && !project.archived

  useEffect(() => {
    if (!addMenuOpen) return
    function onPointerDown(event: MouseEvent) {
      if (addMenuRef.current && !addMenuRef.current.contains(event.target as Node)) setAddMenuOpen(false)
    }
    function onKeyDown(event: KeyboardEvent) {
      if (event.key === 'Escape') setAddMenuOpen(false)
    }
    document.addEventListener('mousedown', onPointerDown)
    document.addEventListener('keydown', onKeyDown)
    return () => {
      document.removeEventListener('mousedown', onPointerDown)
      document.removeEventListener('keydown', onKeyDown)
    }
  }, [addMenuOpen])

  function reloadAll() {
    calendar.reload()
    tasks.reload()
    milestones.reload()
  }
  function openCreate(kind: CreateKind) {
    setAddMenuOpen(false)
    setCreateKind(kind)
  }
  const setDetail = (entry: EnrichedEntry) => setDetailKey(entryKey(entry))
  const agendaDays = useMemo(() => [...byDay.keys()].filter((d) => d >= from && d <= to).sort(), [byDay, from, to])

  function goTo(next: YearMonth) {
    setYm(next)
    setSelected(monthRange(next).from)
  }
  function goToday() {
    setYm(yearMonthOf(today))
    setSelected(today)
  }

  const loading = calendar.loading
  const monthKey = `${ym.year}-${ym.month}`

  return (
    <div className="cal">
      <header className="cal-header">
        <div className="cal-header-title">
          <h1>Project Calendar</h1>
          <p>Tasks, milestones and phases placed on their real dates. Select a day to see everything due on it.</p>
        </div>
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
          <div className="cal-views" role="group" aria-label="View">
            {(['month', 'agenda'] as View[]).map((v) => (
              <button
                key={v}
                type="button"
                className={`cal-view${view === v ? ' active' : ''}`}
                aria-pressed={view === v}
                onClick={() => setView(v)}
              >
                {v === 'month' ? 'Month' : 'Agenda'}
              </button>
            ))}
          </div>
          {canEdit && (
            <button type="button" className="btn btn-primary btn-sm" onClick={() => openCreate('task')}>
              <Icon name="plus" size={14} /> Add task
            </button>
          )}
        </div>
      </header>

      <div className="cal-summary" aria-label="This month at a glance">
        <SummaryCard icon="tasks" label="Tasks due this month" value={summary.tasks} tone="info" loading={loading} />
        <SummaryCard icon="delayed" label="Past due" value={summary.pastDue} tone={summary.pastDue > 0 ? 'danger' : 'neutral'} loading={loading} />
        <SummaryCard icon="milestone" label="Milestones" value={summary.milestones} tone="warning" loading={loading} />
        <SummaryCard icon="health" label="Completed" value={summary.completed} tone="success" loading={loading} />
      </div>

      {calendar.error && <ErrorState message={calendar.error} onRetry={calendar.reload} />}

      {!calendar.error && (
        <div className={`cal-body${view === 'agenda' ? ' agenda' : ''}`}>
          <section className="cal-main" aria-busy={loading}>
            {loading ? (
              <div className="cal-skeleton" aria-hidden="true">
                {Array.from({ length: 42 }).map((_, i) => (
                  <Skeleton key={i} height={72} />
                ))}
              </div>
            ) : view === 'month' ? (
              <MonthGrid
                cells={cells}
                byDay={byDay}
                selected={selected}
                onSelectDay={setSelected}
                onSelectEntry={setDetail}
                animationKey={monthKey}
                onAddToDay={
                  canEdit
                    ? (iso) => {
                        setSelected(iso)
                        setAddMenuOpen(true)
                      }
                    : undefined
                }
              />
            ) : (
              <Agenda days={agendaDays} byDay={byDay} onSelect={setDetail} />
            )}
          </section>

          {view === 'month' && (
            <aside className="cal-side" aria-label="Selected day">
              <div className="cal-side-head">
                <span className="cal-side-date">{longDateLabel(selected)}</span>
                {selected === today && <span className="cal-today-pill">Today</span>}
                {canEdit && (
                  <div className="cal-add-wrap" ref={addMenuRef}>
                    <button
                      type="button"
                      className="btn btn-sm cal-add-btn"
                      aria-haspopup="menu"
                      aria-expanded={addMenuOpen}
                      onClick={() => setAddMenuOpen((o) => !o)}
                    >
                      <Icon name="plus" size={13} /> Add
                    </button>
                    {addMenuOpen && (
                      <div className="cal-add-menu" role="menu" aria-label={`Add on ${longDateLabel(selected)}`}>
                        <button type="button" role="menuitem" onClick={() => openCreate('task')}>
                          <Icon name="tasks" size={14} /> Task
                        </button>
                        <button type="button" role="menuitem" onClick={() => openCreate('milestone')}>
                          <Icon name="milestone" size={14} /> Milestone
                        </button>
                      </div>
                    )}
                  </div>
                )}
              </div>
              {loading ? (
                <Skeleton height={56} />
              ) : selectedEntries.length === 0 ? (
                <div className="cal-side-empty">
                  <span className="cal-empty-art" aria-hidden="true" />
                  <p>Nothing scheduled for this day.</p>
                  {canEdit && (
                    <button type="button" className="btn btn-sm" onClick={() => openCreate('task')}>
                      Add a task due {longDateLabel(selected).split(',')[0]}
                    </button>
                  )}
                </div>
              ) : (
                <ul className="list-plain cal-side-list">
                  {selectedEntries.map((entry) => (
                    <li key={entryKey(entry)}>
                      <EventCard entry={entry} onSelect={setDetail} />
                    </li>
                  ))}
                </ul>
              )}
            </aside>
          )}
        </div>
      )}

      {detail && (
        <EventDetailDialog
          entry={detail}
          projectId={project.id}
          onClose={() => setDetailKey(null)}
          editable={canEdit}
          task={detailTask}
          milestone={detailMilestone}
          onChanged={reloadAll}
        />
      )}
      {createKind === 'task' && (
        <CreateTaskDialog
          projectId={project.id}
          initialDueDate={selected}
          onClose={() => setCreateKind(null)}
          onCreated={() => {
            setCreateKind(null)
            reloadAll()
          }}
        />
      )}
      {createKind === 'milestone' && (
        <CreateMilestoneDialog
          projectId={project.id}
          initialDueDate={selected}
          onClose={() => setCreateKind(null)}
          onCreated={() => {
            setCreateKind(null)
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

function Agenda({
  days,
  byDay,
  onSelect,
}: {
  days: string[]
  byDay: Map<string, EnrichedEntry[]>
  onSelect: (e: EnrichedEntry) => void
}) {
  if (days.length === 0) {
    return (
      <div className="cal-side-empty cal-agenda-empty">
        <span className="cal-empty-art" aria-hidden="true" />
        <p>Nothing is scheduled this month.</p>
        <p className="text-faint">Tasks and milestones appear here once they have a due date; phases once they have start and end dates.</p>
      </div>
    )
  }
  return (
    <div className="cal-agenda">
      {days.map((day) => (
        <section key={day} className="cal-agenda-day" aria-label={longDateLabel(day)}>
          <h3 className="cal-agenda-date">{longDateLabel(day)}</h3>
          <ul className="list-plain cal-side-list">
            {(byDay.get(day) ?? []).map((entry) => (
              <li key={entryKey(entry)}>
                <EventCard entry={entry} onSelect={onSelect} />
              </li>
            ))}
          </ul>
        </section>
      ))}
    </div>
  )
}
