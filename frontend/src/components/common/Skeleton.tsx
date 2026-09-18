export function Skeleton({ width, height = 14 }: { width?: number | string; height?: number | string }) {
  return (
    <span
      className="skeleton"
      style={{ width: width ?? '100%', height }}
      aria-hidden="true"
    />
  )
}

/** A grid of stat-card-shaped skeletons, matching the real .stat-grid layout while loading. */
export function StatGridSkeleton({ count = 4 }: { count?: number }) {
  return (
    <div className="stat-grid" aria-hidden="true">
      {Array.from({ length: count }).map((_, i) => (
        <div className="stat-card" key={i}>
          <Skeleton width={48} height={26} />
          <div style={{ marginTop: 8 }}>
            <Skeleton width="70%" height={12} />
          </div>
        </div>
      ))}
    </div>
  )
}

/** Row-shaped skeletons matching the real table layout while loading. */
export function TableSkeleton({ rows = 5, columns = 4 }: { rows?: number; columns?: number }) {
  return (
    <div className="table-wrap" aria-hidden="true">
      <table>
        <tbody>
          {Array.from({ length: rows }).map((_, r) => (
            <tr key={r}>
              {Array.from({ length: columns }).map((_, c) => (
                <td key={c}>
                  <Skeleton width={c === 0 ? '60%' : '80%'} />
                </td>
              ))}
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  )
}
