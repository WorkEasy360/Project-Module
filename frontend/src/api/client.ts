import { setAccessProblem, setUnauthenticated } from '../context/sessionStore'
import type { ProblemDetail } from '../types/common'
import { buildAccessProblem } from './sessionMessages'

/**
 * Where the API lives, resolved at build time.
 *
 * Sign-in is a Workeasy360 session carried by an HttpOnly cookie, and writes carry a CSRF token
 * read from a cookie, so the UI and the API must share one origin. That is true in the packaged
 * deployment (the UI is served by the Spring Boot jar) and in development (the Vite dev server
 * proxies `/api` to the backend, see `vite.config.ts`). The base is therefore empty by default.
 *
 * `VITE_API_BASE_URL` can still override it, but a cross-origin base cannot carry the session
 * cookie (requests use `credentials: 'same-origin'`), so only use it behind a same-origin proxy.
 * It holds a public address only — never a secret, because it is embedded in the browser bundle.
 */
export const API_BASE_URL = (import.meta.env.VITE_API_BASE_URL ?? '').replace(/\/$/, '')

const BASE_URL = API_BASE_URL
const API_PREFIX = '/api/v1'
const SESSION_PATH = '/api/auth/session'
// The backend names the cookie `__Host-XSRF-TOKEN` when cookies are Secure (https) and `XSRF-TOKEN`
// otherwise (the local profile over plain http); either one is the token.
const CSRF_COOKIES = ['__Host-XSRF-TOKEN', 'XSRF-TOKEN']
const CSRF_HEADER = 'X-XSRF-TOKEN'

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

/** Reads the CSRF token the backend placed in a script-readable cookie (URL-decoded), or null. */
export function readCsrfToken(): string | null {
  if (typeof document === 'undefined') return null
  for (const part of document.cookie.split(';')) {
    const eq = part.indexOf('=')
    if (eq < 0) continue
    if (!CSRF_COOKIES.includes(part.slice(0, eq).trim())) continue
    const raw = part.slice(eq + 1).trim()
    if (!raw) return null
    try {
      return decodeURIComponent(raw)
    } catch {
      return raw
    }
  }
  return null
}

/**
 * Returns the CSRF token for a state-changing request. If the cookie is not there yet (nothing
 * has been read since the page loaded), a `GET /api/auth/session` makes the backend issue it.
 */
export async function ensureCsrfToken(): Promise<string | null> {
  const existing = readCsrfToken()
  if (existing) return existing
  try {
    await fetch(`${BASE_URL}${SESSION_PATH}`, {
      method: 'GET',
      credentials: 'same-origin',
      cache: 'no-store',
      headers: { Accept: 'application/json' },
    })
  } catch {
    return null
  }
  return readCsrfToken()
}

/**
 * Tells the session store what an error response means for the whole app: a 401 means the session
 * is gone, and a `workeasy360_*` 403 means the organization's access to Easy Project has changed.
 * Everything else is left to the caller as an ordinary error.
 */
function reportToSession(status: number, problem: ProblemDetail | null): void {
  if (status === 401) {
    setUnauthenticated()
    return
  }
  const code = problem?.code
  if (status === 403 && typeof code === 'string' && code.startsWith('workeasy360_')) {
    setAccessProblem(buildAccessProblem(code, problem?.reason))
  }
}

function parseProblem(text: string): ProblemDetail | null {
  if (!text) return null
  try {
    const data = JSON.parse(text) as unknown
    return data && typeof data === 'object' ? (data as ProblemDetail) : null
  } catch {
    return null
  }
}

async function request<T>(path: string, options: RequestOptions = {}): Promise<T> {
  const method = options.method ?? 'GET'
  const headers: Record<string, string> = {
    'X-Correlation-Id': newCorrelationId(),
  }
  if (options.body !== undefined) {
    headers['Content-Type'] = 'application/json'
  }
  if (method !== 'GET') {
    const token = await ensureCsrfToken()
    if (token) headers[CSRF_HEADER] = token
  }

  let response: Response
  try {
    response = await fetch(`${BASE_URL}${API_PREFIX}${path}${buildQuery(options.query)}`, {
      method,
      headers,
      credentials: 'same-origin',
      body: options.body !== undefined ? JSON.stringify(options.body) : undefined,
    })
  } catch (cause) {
    throw new NetworkError(cause)
  }

  if (response.status === 204) {
    return undefined as T
  }

  const text = await response.text()

  if (!response.ok) {
    const problem = parseProblem(text)
    reportToSession(response.status, problem)
    throw new ApiError(response.status, problem, `Request failed with status ${response.status}`)
  }

  return (text ? (JSON.parse(text) as unknown) : undefined) as T
}

/**
 * Calls an `/api/auth/*` endpoint (session check, logout). These live outside `/api/v1`, so they
 * have their own small helper. A 401 here is an answer ("not signed in"), not a failure.
 */
export async function authRequest<T>(path: string, method: 'GET' | 'POST'): Promise<{ status: number; data: T | null }> {
  const headers: Record<string, string> = { Accept: 'application/json', 'X-Correlation-Id': newCorrelationId() }
  if (method !== 'GET') {
    const token = await ensureCsrfToken()
    if (token) headers[CSRF_HEADER] = token
  }
  let response: Response
  try {
    response = await fetch(`${BASE_URL}${path}`, {
      method,
      headers,
      credentials: 'same-origin',
      cache: 'no-store',
    })
  } catch (cause) {
    throw new NetworkError(cause)
  }
  const text = response.status === 204 ? '' : await response.text()
  if (response.status === 401) return { status: 401, data: null }
  if (!response.ok) {
    throw new ApiError(response.status, parseProblem(text), `Request failed with status ${response.status}`)
  }
  return { status: response.status, data: text ? (JSON.parse(text) as T) : null }
}

export const api = {
  get: <T>(path: string, query?: Record<string, unknown>) =>
    request<T>(path, { method: 'GET', query: query as QueryParams | undefined }),
  post: <T>(path: string, body?: unknown, query?: Record<string, unknown>) =>
    request<T>(path, { method: 'POST', body, query: query as QueryParams | undefined }),
  patch: <T>(path: string, body?: unknown) => request<T>(path, { method: 'PATCH', body }),
  del: (path: string) => request<void>(path, { method: 'DELETE' }),
}
