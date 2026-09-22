import { getIdentity } from '../context/identityStore'
import type { ProblemDetail } from '../types/common'

/**
 * Where the API lives, resolved at build time.
 *
 * In a production build the UI is packaged inside the Spring Boot jar and served from the same
 * origin as the API, so an empty base is correct: requests go to `/api/v1/...` on whatever host
 * served the page, and no CORS exchange is involved.
 *
 * In development the Vite server (:5173) is a different origin from the backend (:8080), so the
 * absolute address is needed; the backend's CORS allowlist already permits that origin.
 *
 * `VITE_API_BASE_URL` overrides both, for the case where the UI is hosted apart from the API.
 * It holds a public address only — never a secret, because it is embedded in the browser bundle.
 */
export const API_BASE_URL = (
  import.meta.env.VITE_API_BASE_URL ?? (import.meta.env.DEV ? 'http://localhost:8080' : '')
).replace(/\/$/, '')

const BASE_URL = API_BASE_URL
const API_PREFIX = '/api/v1'

/**
 * Thrown for every non-2xx response. Carries the backend's RFC 9457 ProblemDetail so callers can
 * show the exact validation/authorization/conflict message the server produced — never a raw
 * stack trace, and never a guessed message.
 */
export class ApiError extends Error {
  readonly status: number
  readonly problem: ProblemDetail | null

  constructor(status: number, problem: ProblemDetail | null, fallbackMessage: string) {
    super(problem?.detail || fallbackMessage)
    this.name = 'ApiError'
    this.status = status
    this.problem = problem
  }

  /** True when this is an optimistic-locking conflict (409) — caller should offer a reload. */
  get isConflict(): boolean {
    return this.status === 409
  }

  get isForbidden(): boolean {
    return this.status === 403
  }

  get isUnauthenticated(): boolean {
    return this.status === 401
  }

  get isNotFound(): boolean {
    return this.status === 404
  }

  /** True when a downstream integration (e.g. the AI provider) is not configured/available. */
  get isIntegrationUnavailable(): boolean {
    return this.status === 503
  }
}

/** Thrown when the request never reached the server (offline, DNS, CORS, server down). */
export class NetworkError extends Error {
  constructor(cause: unknown) {
    super('Could not reach the server. Check your connection and that the backend is running.')
    this.name = 'NetworkError'
    this.cause = cause
  }
}

export interface QueryParams {
  [key: string]: string | number | boolean | undefined | null
}

function buildQuery(params?: QueryParams): string {
  if (!params) return ''
  const search = new URLSearchParams()
  for (const [key, value] of Object.entries(params)) {
    if (value === undefined || value === null || value === '') continue
    search.set(key, String(value))
  }
  const query = search.toString()
  return query ? `?${query}` : ''
}

interface RequestOptions {
  method?: 'GET' | 'POST' | 'PATCH' | 'DELETE'
  body?: unknown
  query?: QueryParams
}

function newCorrelationId(): string {
  if (typeof crypto !== 'undefined' && 'randomUUID' in crypto) {
    return crypto.randomUUID()
  }
  return `corr-${Date.now()}-${Math.random().toString(16).slice(2)}`
}

async function request<T>(path: string, options: RequestOptions = {}): Promise<T> {
  const identity = getIdentity()
  const headers: Record<string, string> = {
    'X-Correlation-Id': newCorrelationId(),
  }
  if (identity) {
    headers['X-User-Id'] = identity.userId
    headers['X-Org-Id'] = identity.orgId
  }
  if (options.body !== undefined) {
    headers['Content-Type'] = 'application/json'
  }

  let response: Response
  try {
    response = await fetch(`${BASE_URL}${API_PREFIX}${path}${buildQuery(options.query)}`, {
      method: options.method ?? 'GET',
      headers,
      body: options.body !== undefined ? JSON.stringify(options.body) : undefined,
    })
  } catch (cause) {
    throw new NetworkError(cause)
  }

  if (response.status === 204) {
    return undefined as T
  }

  const text = await response.text()
  const data = text ? (JSON.parse(text) as unknown) : undefined

  if (!response.ok) {
    const problem = (data as ProblemDetail | undefined) ?? null
    throw new ApiError(response.status, problem, `Request failed with status ${response.status}`)
  }

  return data as T
}

export const api = {
  get: <T>(path: string, query?: Record<string, unknown>) =>
    request<T>(path, { method: 'GET', query: query as QueryParams | undefined }),
  post: <T>(path: string, body?: unknown, query?: Record<string, unknown>) =>
    request<T>(path, { method: 'POST', body, query: query as QueryParams | undefined }),
  patch: <T>(path: string, body?: unknown) => request<T>(path, { method: 'PATCH', body }),
  del: (path: string) => request<void>(path, { method: 'DELETE' }),
}
