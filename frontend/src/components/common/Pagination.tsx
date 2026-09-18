export function Pagination({
  page,
  totalPages,
  totalElements,
  onChange,
}: {
  page: number
  totalPages: number
  totalElements: number
  onChange: (page: number) => void
}) {
  if (totalPages <= 1) return null
  return (
    <div className="pagination">
      <button className="btn btn-sm" disabled={page <= 0} onClick={() => onChange(page - 1)}>
        Previous
      </button>
      <span>
        Page {page + 1} of {totalPages} ({totalElements} total)
      </span>
      <button
        className="btn btn-sm"
        disabled={page >= totalPages - 1}
        onClick={() => onChange(page + 1)}
      >
        Next
      </button>
    </div>
  )
}
