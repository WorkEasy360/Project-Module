import { Icon, type IconName } from '../../../../components/common/Icon'
import { Badge } from '../../../../components/common/Badge'
import { humanizeToken, formatDate } from '../../../../utils/format'
import { entryTone, type EnrichedEntry } from './calendarUtils'

const TYPE_ICON: Record<string, IconName> = { TASK: 'tasks', MILESTONE: 'milestone', PHASE: 'phase' }

/** Compact chip inside a month-grid cell. Type icon + tone bar + text, never color alone. */
export function EventChip({ entry, onSelect }: { entry: EnrichedEntry; onSelect: (e: EnrichedEntry) => void }) {
  const tone = entryTone(entry)
  const label = `${humanizeToken(entry.entityType)}: ${entry.name}${entry.projectName ? ` (${entry.projectName})` : ''}`
  return (
    <button
      type="button"
      className={`cal-chip tone-${tone}`}
      onClick={(ev) => {
        ev.stopPropagation()
        onSelect(entry)
      }}
      aria-label={label}
      title={label}
    >
      <Icon name={TYPE_ICON[entry.entityType] ?? 'calendar'} size={12} />
      <span className="cal-chip-text">{entry.name}</span>
    </button>
  )
}

/** Fuller card used in the day panel and agenda view. */
export function EventCard({ entry, onSelect }: { entry: EnrichedEntry; onSelect: (e: EnrichedEntry) => void }) {
  const tone = entryTone(entry)
  const when =
    entry.entityType === 'PHASE'
      ? `${formatDate(entry.startDate)} – ${formatDate(entry.endDate)}`
      : formatDate(entry.date)
  return (
    <button type="button" className={`cal-card tone-${tone}`} onClick={() => onSelect(entry)}>
      <span className="cal-card-icon">
        <Icon name={TYPE_ICON[entry.entityType] ?? 'calendar'} size={16} />
      </span>
      <span className="cal-card-body">
        <span className="cal-card-title">{entry.name}</span>
        <span className="cal-card-meta">
          {entry.projectName && (
            <>
              <span className="cal-card-project">{entry.projectName}</span>
              <span aria-hidden="true">·</span>
            </>
          )}
          <span>{humanizeToken(entry.entityType)}</span>
          <span aria-hidden="true">·</span>
          <span>{when}</span>
          {entry.assigneeId && (
            <>
              <span aria-hidden="true">·</span>
              <span className="mono">{entry.assigneeId.slice(0, 8)}…</span>
            </>
          )}
        </span>
      </span>
      {entry.status && <Badge tone={tone}>{humanizeToken(entry.status)}</Badge>}
      <Icon name="chevronRight" size={14} className="cal-card-chevron" />
    </button>
  )
}
