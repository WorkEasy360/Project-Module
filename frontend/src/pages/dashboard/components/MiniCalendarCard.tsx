import { useMemo, useState } from 'react'
import { Icon } from '../../../components/common/Icon'
import { IconButton } from '../../../components/common/IconButton'
import { Modal } from '../../../components/common/Modal'
import { Skeleton } from '../../../components/common/Skeleton'
import { SelectField, FormActions } from '../../../components/forms/fields'
import { useAsyncData } from '../../../hooks/useAsyncData'
import { calendarApi } from '../../../api/calendar'
import { CreateTaskDialog } from '../../projects/CreateTaskDialog'
import {
  WEEKDAYS,
  addMonths,
  buildMonthGrid,
  longDateLabel,
  monthLabel,
  monthRange,
  yearMonthOf,
  type YearMonth,
} from '../../projects/tabs/calendar/calendarUtils'
import { calendarDayMarks, type LoadedProject } from '../dashboardUtils'

/**
 * Mini month calendar. Every dot comes from the real per-project /calendar window for the visible
 * month, fetched for each loaded project and merged client-side (a failed project contributes
 * nothing). Selecting a day and pressing "Add task" opens the existing task dialog with that day
 * as the due date — after picking which project the task belongs to, since tasks are per-project.
 */
export function MiniCalendarCard({
  projects,
  projectsLoading,
  today,
  onTaskCreated,
}: {
  projects: LoadedProject[]
  projectsLoading: boolean
  today: string
  onTaskCreated: () => void
}) {
  const [ym, setYm] = useState<YearMonth>(() => yearMonthOf(today))
  const [selected, setSelected] = useState(today)
  const [pickOpen, setPickOpen] = useState(false)
  const [projectId, setProjectId] = useState('')
  const [taskOpen, setTaskOpen] = useState(false)

  const { from, to } = monthRange(ym)
  const ids = projects.map((p) => p.project.id).join(',')
  const calendars = useAsyncData(
    () => Promise.all(projects.map((p) => calendarApi.get(p.project.id, from, to).catch(() => null))),
    [ids, from, to],
  )
  const marks = useMemo(() => calendarDayMarks(calendars.data ?? [], from, to), [calendars.data, from, to])
  const cells = useMemo(() => buildMonthGrid(ym, today), [ym, today])
  const loading = projectsLoading || calendars.loading

  function goTo(next: YearMonth) {
    setYm(next)
    setSelected(monthRange(next).from)
  }

  function startAddTask() {
    if (projects.length === 1) {
      setProjectId(projects[0].project.id)
      setTaskOpen(true)
    } else {
      setPickOpen(true)
    }
  }

  return (
    <section className="hb-panel hb-cal" aria-label="Calendar">
      <header className="hb-cal-head">
        <IconButton icon="chevronLeft" label="Previous month" size={16} onClick={() => goTo(addMonths(ym, -1))} />
        <h2 className="hb-cal-title" aria-live="polite">
          {monthLabel(ym)}
        </h2>
        <IconButton icon="chevronRight" label="Next month" size={16} onClick={() => goTo(addMonths(ym, 1))} />
      </header>

      <div className="hb-cal-grid" role="grid" aria-label={monthLabel(ym)} aria-busy={loading}>
        <div className="hb-cal-weekdays" role="row">
          {WEEKDAYS.map((d) => (
            <span key={d} role="columnheader" className="hb-cal-weekday" aria-label={d}>
              {d.charAt(0)}
            </span>
          ))}
        </div>
        <div className="hb-cal-cells" role="row">
          {cells.map((cell) => {
            const m = marks.get(cell.iso)
            const isSelected = cell.iso === selected
            const parts = [longDateLabel(cell.iso)]
            if (m?.tasks) parts.push(`${m.tasks} ${m.tasks === 1 ? 'task' : 'tasks'}`)
            if (m?.milestones) parts.push(`${m.milestones} ${m.milestones === 1 ? 'milestone' : 'milestones'}`)
            if (cell.isToday) parts.push('today')
            return (
              <button
                key={cell.iso}
                type="button"
                role="gridcell"
                aria-selected={isSelected}
                aria-label={parts.join(', ')}
                className={`hb-cal-day${cell.inMonth ? '' : ' outside'}${cell.isToday ? ' today' : ''}${isSelected ? ' selected' : ''}`}
                onClick={() => setSelected(cell.iso)}
              >
                <span className="hb-cal-daynum">{cell.day}</span>
                <span className="hb-cal-dots" aria-hidden="true">
                  {loading && cell.inMonth ? (
                    <span className="hb-cal-dot loading" />
                  ) : (
                    <>
                      {m?.tasks ? <span className="hb-cal-dot task" /> : null}
                      {m?.milestones ? <span className="hb-cal-dot milestone" /> : null}
                    </>
                  )}
                </span>
              </button>
            )
          })}
        </div>
      </div>

      <div className="hb-cal-foot">
        <ul className="hb-cal-legend" aria-label="Legend">
          <li>
            <span className="hb-cal-dot task" aria-hidden="true" /> Tasks
          </li>
          <li>
            <span className="hb-cal-dot milestone" aria-hidden="true" /> Milestones
          </li>
        </ul>
        <p className="hb-cal-selected">
          {loading ? <Skeleton width={120} height={12} /> : <SelectedSummary iso={selected} marks={marks.get(selected)} />}
        </p>
        <button
          type="button"
          className="hb-primary-btn"
          onClick={startAddTask}
          disabled={projects.length === 0}
          title={projects.length === 0 ? 'Load a project first' : `Create a task due ${longDateLabel(selected)}`}
        >
          <Icon name="plus" size={16} /> Add task
        </button>
      </div>

      {pickOpen && (
        <Modal title="Create a task in…" onClose={() => setPickOpen(false)}>
          <form
            className="form-grid"
            onSubmit={(e) => {
              e.preventDefault()
              if (!projectId) return
              setPickOpen(false)
              setTaskOpen(true)
            }}
          >
            <p className="hb-pick-hint">Due {longDateLabel(selected)}. Tasks belong to a project, so choose one first.</p>
            <SelectField
              id="hb-pick-project"
              label="Project"
              value={projectId}
              onChange={setProjectId}
              required
              allowEmpty
              emptyLabel="Choose a project"
              options={projects.map((p) => ({ value: p.project.id, label: p.project.name }))}
            />
            <FormActions>
              <button type="button" className="btn" onClick={() => setPickOpen(false)}>
                Cancel
              </button>
              <button type="submit" className="btn btn-primary" disabled={!projectId}>
                Continue
              </button>
            </FormActions>
          </form>
        </Modal>
      )}
      {taskOpen && projectId && (
        <CreateTaskDialog
          projectId={projectId}
          initialDueDate={selected}
          onClose={() => setTaskOpen(false)}
          onCreated={() => {
            setTaskOpen(false)
            calendars.reload()
            onTaskCreated()
          }}
        />
      )}
    </section>
  )
}

function SelectedSummary({ iso, marks }: { iso: string; marks: { tasks: number; milestones: number } | undefined }) {
  const label = longDateLabel(iso)
  if (!marks) return <>{label}: nothing scheduled</>
  const bits: string[] = []
  if (marks.tasks) bits.push(`${marks.tasks} ${marks.tasks === 1 ? 'task' : 'tasks'}`)
  if (marks.milestones) bits.push(`${marks.milestones} ${marks.milestones === 1 ? 'milestone' : 'milestones'}`)
  return (
    <>
      {label}: {bits.join(' · ')}
    </>
  )
}
