import type { ChartBucket, StatusSlice } from '../../pages/dashboard/opsDashboard'

/** The task charts, shown on Reports (V3 keeps analytics charts off Home). */
export function Donut({ slices, total }: { slices: StatusSlice[]; total: number }) {
  const r = 50
  const c = 2 * Math.PI * r
  let offset = 0
  return (
    <svg className="ops-donut" viewBox="0 0 140 140" role="img" aria-label={`${total} tasks by status`}>
      <circle cx="70" cy="70" r={r} fill="none" stroke="var(--color-border, #e5e7eb)" strokeWidth="22" />
      {total > 0 &&
        slices.map((s) => {
          const len = (s.count / total) * c
          const el = (
            <circle key={s.key} cx="70" cy="70" r={r} fill="none" stroke={s.color} strokeWidth="22"
              strokeDasharray={`${len} ${c - len}`} strokeDashoffset={-offset} transform="rotate(-90 70 70)" />
          )
          offset += len
          return el
        })}
      <text x="70" y="68" textAnchor="middle" className="ops-donut-num">{total}</text>
      <text x="70" y="88" textAnchor="middle" className="ops-donut-label">Tasks</text>
    </svg>
  )
}

export function BarChart({ buckets, height = 134 }: { buckets: ChartBucket[]; height?: number }) {
  const max = Math.max(10, ...buckets.flatMap((b) => [b.created, b.completed]))
  const top = Math.ceil(max / 20) * 20 || 20
  const ticks = [0, 1, 2, 3, 4, 5].map((i) => Math.round((top / 5) * i))
  const W = 640
  const H = height
  const left = 34
  const bottom = 24
  const plotW = W - left - 6
  const plotH = H - bottom - 8
  const step = plotW / Math.max(1, buckets.length)
  const bar = Math.min(20, step / 3)
  const y = (v: number) => 8 + plotH - (v / top) * plotH
  return (
    <svg className="ops-chart" viewBox={`0 0 ${W} ${H}`} role="img" aria-label="Tasks completed and created over time">
      <defs>
        <linearGradient id="ops-bar-green" x1="0" x2="0" y1="0" y2="1">
          <stop offset="0" stopColor="#22c55e" />
          <stop offset="1" stopColor="#15803d" />
        </linearGradient>
        <linearGradient id="ops-bar-blue" x1="0" x2="0" y1="0" y2="1">
          <stop offset="0" stopColor="#7cb6ff" />
          <stop offset="1" stopColor="#3b82f6" />
        </linearGradient>
      </defs>
      {ticks.map((t) => (
        <g key={t}>
          <line x1={left} x2={W - 6} y1={y(t)} y2={y(t)} className="ops-chart-grid" />
          <text x={left - 6} y={y(t) + 4} textAnchor="end" className="ops-chart-axis">{t}</text>
        </g>
      ))}
      {buckets.map((b, i) => {
        const cx = left + step * i + step / 2
        return (
          <g key={b.label + i}>
            <rect x={cx - bar - 1} y={y(b.completed)} width={bar} height={y(0) - y(b.completed)} rx="4" fill="url(#ops-bar-green)">
              <title>{`${b.label}: ${b.completed} completed`}</title>
            </rect>
            <rect x={cx + 1} y={y(b.created)} width={bar} height={y(0) - y(b.created)} rx="4" fill="url(#ops-bar-blue)">
              <title>{`${b.label}: ${b.created} created`}</title>
            </rect>
            <text x={cx} y={H - 6} textAnchor="middle" className="ops-chart-axis">{b.label}</text>
          </g>
        )
      })}
    </svg>
  )
}
