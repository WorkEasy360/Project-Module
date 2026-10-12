export interface PageResponse<T> {
  content: T[]
  page: number
  size: number
  totalElements: number
  totalPages: number
}

export type SortDirection = 'ASC' | 'DESC'

export interface FieldViolation {
  field: string
  message: string
}

/** RFC 9457 ProblemDetail shape returned by GlobalExceptionHandler for every 4xx/5xx response. */
export interface ProblemDetail {
  type: string
  title: string
  status: number
  detail: string
  instance?: string
  traceId?: string
  errors?: FieldViolation[]
  /** Machine-readable code on some 403s: `workeasy360_*` (entitlement) or `csrf_invalid`. */
  code?: string
  /** Why access ended, alongside `workeasy360_access_ended` (expired, not_entitled, suspended, ...). */
  reason?: string
}
