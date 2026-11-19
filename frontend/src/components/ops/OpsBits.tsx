import { Link } from 'react-router-dom'
import { Icon, type IconName } from '../common/Icon'

/** A count tile in the Operations style. With `to`, it opens the matching filtered list (V3: every count opens its view). */
export function CountTile({
  icon,
  tone,
  label,
  value,
  to,
  active = false,
  onClick,
  loading = false,
}: {
  icon: IconName
  tone: string
  label: string
  value: number
  to?: string
  active?: boolean
  onClick?: () => void
  loading?: boolean
}) {
  const body = (
    <>
      <span className={`ops-tile-icon tone-${tone}`}>
        <Icon name={icon} size={30} />
      </span>
      <span className="ops-tile-text">
        <span className="ops-tile-label">{label}</span>
        <strong className="ops-tile-value">{loading ? '–' : value}</strong>
      </span>
    </>
  )
  if (to) {
    return (
      <Link to={to} className={`ops-tile${active ? ' on' : ''}`} aria-current={active ? 'page' : undefined}>
        {body}
      </Link>
    )
  }
  return (
    <button type="button" className={`ops-tile${active ? ' on' : ''}`} onClick={onClick} aria-pressed={active}>
      {body}
    </button>
  )
}

export function FilterSelect({
  label,
  value,
  onChange,
  options,
  icon,
}: {
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
          <option key={o.value} value={o.value}>
            {o.label}
          </option>
        ))}
      </select>
      <Icon name="chevronDown" size={18} className="ops-select-chevron" />
    </label>
  )
}

export function CardHead({ icon, title, to, linkLabel = 'View All' }: { icon: IconName; title: string; to?: string; linkLabel?: string }) {
  return (
    <header className="ops-card-head">
      <h2>
        <Icon name={icon} size={22} /> {title}
      </h2>
      {to && (
        <Link to={to} className="ops-view-all">
          {linkLabel} <Icon name="arrowRight" size={16} />
        </Link>
      )}
    </header>
  )
}
