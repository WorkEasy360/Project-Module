import { Icon } from '../../../components/common/Icon'
import { Skeleton } from '../../../components/common/Skeleton'
import type { ProjectStatus } from '../../../types/project'
import { donutGradient, donutSegments } from '../dashboardUtils'

/** Project Status donut from the real /dashboard byStatus counts (CSS conic-gradient). */
export function ProjectStatusCard({ byStatus, loading }: { byStatus: Record<ProjectStatus, number> | null; loading: boolean }) {
  const segments = byStatus ? donutSegments(byStatus) : []
  const total = segments.reduce((a, s) => a + s.count, 0)
  return (
    <section className="hb-panel hb-widget" aria-labelledby="hb-status-title">
      <header className="hb-panel-head compact">
        <span className="hb-panel-icon tone-warning">
          <Icon name="overview" size={18} />
        </span>
        <div className="hb-panel-titles">
          <h2 id="hb-status-title">Project Status</h2>
          <p>All non-archived projects by status</p>
        </div>
      </header>
      {loading ? (
        <div className="hb-donut-wrap">
          <Skeleton width={130} height={130} />
        </div>
      ) : (
        <div className="hb-donut-wrap">
          <div
            className="hb-donut"
            role="img"
            aria-label={
              total === 0
                ? 'No active projects'
                : segments.map((s) => `${s.count} ${s.label} (${s.percent}%)`).join(', ')
            }
            style={{ background: donutGradient(segments) }}
          >
            <span className="hb-donut-hole">
              <strong>{total}</strong>
              <small>projects</small>
            </span>
          </div>
          <ul className="hb-legend">
            {segments.length === 0 && <li className="text-muted">No active projects yet.</li>}
            {segments.map((s) => (
              <li key={s.status}>
                <span className="hb-legend-dot" style={{ background: s.color }} aria-hidden="true" />
                <span className="hb-legend-label">{s.label}</span>
                <span className="hb-legend-value">
                  {s.count} <span className="text-faint">({s.percent}%)</span>
                </span>
              </li>
            ))}
          </ul>
        </div>
      )}
    </section>
  )
}

/** The backend records activity but exposes no API for it — an honest placeholder keeps the layout. */
export function RecentActivityCard() {
  return (
    <section className="hb-panel hb-widget" aria-labelledby="hb-activity-title">
      <header className="hb-panel-head compact">
        <span className="hb-panel-icon tone-success">
          <Icon name="activity" size={18} />
        </span>
        <div className="hb-panel-titles">
          <h2 id="hb-activity-title">Recent Activity</h2>
          <p>Latest changes across your projects</p>
        </div>
      </header>
      <div className="hb-widget-unavailable">
        <span className="hb-empty-icon tone-neutral">
          <Icon name="inbox" size={18} />
        </span>
        <p>
          <strong>Not available yet.</strong> Changes are recorded on the server, but no activity API is exposed
          in this version. This card will fill in automatically once it is.
        </p>
      </div>
    </section>
  )
}
