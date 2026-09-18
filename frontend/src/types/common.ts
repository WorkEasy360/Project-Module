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
}
