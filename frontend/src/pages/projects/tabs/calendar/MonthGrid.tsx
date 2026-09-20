import { Icon } from '../../../../components/common/Icon'
import { EventChip } from './CalendarEvent'
import { WEEKDAYS, longDateLabel, type DayCell, type EnrichedEntry } from './calendarUtils'

const MAX_CHIPS = 3

export function MonthGrid({
  cells,
  byDay,
  selected,
  onSelectDay,
  onSelectEntry,
  animationKey,
  onAddToDay,
}: {
  cells: DayCell[]
  byDay: Map<string, EnrichedEntry[]>
  selected: string
  onSelectDay: (iso: string) => void
  onSelectEntry: (entry: EnrichedEntry) => void
  animationKey: string
  /** Optional: renders a small "+" on every day that lets the caller add something on it. */
  onAddToDay?: (iso: string) => void
}) {
  return (
    <div className="cal-grid" role="grid" aria-label="Month">
      <div className="cal-weekdays" role="row">
        {WEEKDAYS.map((d) => (
          <div key={d} role="columnheader" className="cal-weekday">
            {d}
          </div>
        ))}
      </div>
      <div className="cal-cells" key={animationKey}>
        {cells.map((cell) => {
          const entries = byDay.get(cell.iso) ?? []
          const overflow = entries.length - MAX_CHIPS
          const isSelected = cell.iso === selected
          return (
            <div
              key={cell.iso}
              role="gridcell"
              aria-selected={isSelected}
              className={`cal-cell${cell.inMonth ? '' : ' outside'}${cell.isToday ? ' today' : ''}${isSelected ? ' selected' : ''}${entries.length ? ' has-events' : ''}`}
              onClick={() => onSelectDay(cell.iso)}
            >
              <button
                type="button"
                className="cal-daynum"
                aria-label={`${longDateLabel(cell.iso)}${entries.length ? `, ${entries.length} item${entries.length === 1 ? '' : 's'}` : ''}${cell.isToday ? ', today' : ''}`}
                aria-pressed={isSelected}
                onClick={(e) => {
                  e.stopPropagation()
                  onSelectDay(cell.iso)
                }}
              >
                {cell.day}
              </button>
              {onAddToDay && (
                <button
                  type="button"
                  className="cal-add"
                  aria-label={`Add on ${longDateLabel(cell.iso)}`}
                  title="Add a task or milestone on this day"
                  onClick={(e) => {
                    e.stopPropagation()
                    onAddToDay(cell.iso)
                  }}
                >
                  <Icon name="plus" size={12} />
                </button>
              )}
              {entries.length > 0 && <span className="cal-dot" aria-hidden="true" />}
              <div className="cal-chips">
                {entries.slice(0, MAX_CHIPS).map((entry) => (
                  <EventChip key={`${entry.entityType}-${entry.id}`} entry={entry} onSelect={onSelectEntry} />
                ))}
                {overflow > 0 && (
                  <button
                    type="button"
                    className="cal-more"
                    onClick={(e) => {
                      e.stopPropagation()
                      onSelectDay(cell.iso)
                    }}
                  >
                    +{overflow} more
                  </button>
                )}
              </div>
            </div>
          )
        })}
      </div>
    </div>
  )
}
