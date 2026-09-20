import { Link } from 'react-router-dom'
import { Icon } from '../../../components/common/Icon'
import { Skeleton } from '../../../components/common/Skeleton'
import { formatDate } from '../../../utils/format'
import type { UpcomingItem } from '../dashboardUtils'

/** Next dated tasks and milestones across the loaded projects (derived from their real lists). */
export function UpcomingCard({ items, loading }: { items: UpcomingItem[]; loading: boolean }) {
  return (
    <section className="hb-panel hb-upcoming" aria-labelledby="hb-upcoming-title">
      <header className="hb-panel-head compact">
        <span className="hb-panel-icon tone-info">
          <Icon name="clock" size={18} />
        </span>
        <div className="hb-panel-titles">
          <h2 id="hb-upcoming-title">Upcoming</h2>
          <p>Due today or later, in your loaded projects</p>
        </div>
        <Link to="/calendar" className="hb-link-all">
          View All <Icon name="arrowRight" size={14} />
        </Link>
      </header>
      {loading && (
        <ul className="hb-up-list" aria-hidden="true">
          {Array.from({ length: 3 }).map((_, i) => (
            <li key={i} className="hb-up-row">
              <Skeleton width={34} height={34} />
              <Skeleton width="55%" height={14} />
            </li>
          ))}
        </ul>
      )}
      {!loading && items.length === 0 && (
        <p className="text-muted hb-widget-empty">Nothing dated is coming up in your loaded projects.</p>
      )}
      {!loading && items.length > 0 && (
        <ul className="hb-up-list">
          {items.map((it) => (
            <li key={`${it.kind}-${it.id}`} className="hb-up-row">
              <span
                className={`hb-metric-icon ${it.kind === 'MILESTONE' ? (it.atRisk ? 'tone-danger' : 'tone-warning') : 'tone-info'}`}
                aria-hidden="true"
              >
                <Icon name={it.kind === 'MILESTONE' ? 'flag' : 'tasks'} size={15} />
              </span>
              <span className="hb-up-body">
                <Link to={it.href} className="hb-up-name">
                  {it.name}
                </Link>
                <span className="hb-up-project">
                  {it.kind === 'MILESTONE' ? 'Milestone' : 'Task'} · {it.projectName}
                </span>
              </span>
              <span className="hb-up-date">{formatDate(it.date)}</span>
            </li>
          ))}
        </ul>
      )}
    </section>
  )
}
