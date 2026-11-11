import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { api, ApiError, NetworkError, readCsrfToken } from './client'
import { toUserMessage } from './errorMessage'
import { getSessionState, resetSession, setAuthenticated } from '../context/sessionStore'

const originalFetch = globalThis.fetch
const COOKIE_GONE = 'XSRF-TOKEN=; expires=Thu, 01 Jan 1970 00:00:00 GMT; path=/'

function problemResponse(status: number, body: Record<string, unknown>) {
  return new Response(
    JSON.stringify({ type: 'https://errors.projectmodule/x', title: 't', status, detail: 'd', ...body }),
    { status, headers: { 'Content-Type': 'application/problem+json' } },
  )
}

function signedIn() {
  setAuthenticated({
    user: { id: 'user-1', email: null, name: null },
    organization: { id: 'org-1' },
    workeasy360SignOut: false,
  })
}

describe('api client', () => {
  beforeEach(() => {
    document.cookie = COOKIE_GONE
    signedIn()
  })

  afterEach(() => {
    globalThis.fetch = originalFetch
    document.cookie = COOKIE_GONE
    resetSession()
    vi.restoreAllMocks()
  })

  it('sends the session cookie (same-origin) and a correlation id, and no identity headers', async () => {
    const fetchMock = vi.fn().mockResolvedValue(
      new Response(JSON.stringify({ ok: true }), { status: 200, headers: { 'Content-Type': 'application/json' } }),
    )
    globalThis.fetch = fetchMock as unknown as typeof fetch

    await api.get('/projects')

    expect(fetchMock).toHaveBeenCalledTimes(1)
    const [url, init] = fetchMock.mock.calls[0] as [string, RequestInit]
    expect(url).toBe('/api/v1/projects')
    expect(init.credentials).toBe('same-origin')
    const headers = init.headers as Record<string, string>
    expect(headers['X-Correlation-Id']).toBeTruthy()
    const names = Object.keys(headers).map((h) => h.toLowerCase())
    expect(names).not.toContain('x-user-id')
    expect(names).not.toContain('x-org-id')
  })

  it('also reads the __Host- prefixed CSRF cookie the backend uses over https', () => {
    // jsdom runs on http, where a __Host- cookie cannot be stored, so the cookie string is supplied directly.
    const spy = vi.spyOn(document, 'cookie', 'get').mockReturnValue('theme=dark; __Host-XSRF-TOKEN=hosted%2F1; other=x')
    expect(readCsrfToken()).toBe('hosted/1')
    spy.mockRestore()
  })

  it('does not send the CSRF header on reads, even when the cookie exists', async () => {
    document.cookie = 'XSRF-TOKEN=abc; path=/'
    const fetchMock = vi.fn().mockResolvedValue(new Response('{}', { status: 200 }))
    globalThis.fetch = fetchMock as unknown as typeof fetch
    await api.get('/projects')
    expect((fetchMock.mock.calls[0][1].headers as Record<string, string>)['X-XSRF-TOKEN']).toBeUndefined()
  })

  it('sends the URL-decoded XSRF-TOKEN cookie as X-XSRF-TOKEN on POST, PATCH and DELETE', async () => {
    document.cookie = 'XSRF-TOKEN=a%2Fb%3D%3D; path=/'
    expect(readCsrfToken()).toBe('a/b==')
    const fetchMock = vi.fn().mockImplementation(() => Promise.resolve(new Response(null, { status: 204 })))
    globalThis.fetch = fetchMock as unknown as typeof fetch

    await api.post('/projects', { name: 'x' })
    await api.patch('/projects/1', { name: 'y' })
    await api.del('/projects/1')

    expect(fetchMock).toHaveBeenCalledTimes(3)
    for (const call of fetchMock.mock.calls as [string, RequestInit][]) {
      expect((call[1].headers as Record<string, string>)['X-XSRF-TOKEN']).toBe('a/b==')
      expect(call[1].credentials).toBe('same-origin')
    }
  })

  it('fetches the session first to obtain the CSRF cookie when a write happens before any read', async () => {
    const fetchMock = vi.fn().mockImplementation((url: string) => {
      if (url === '/api/auth/session') {
        document.cookie = 'XSRF-TOKEN=fresh; path=/'
        return Promise.resolve(new Response('{}', { status: 200 }))
      }
      return Promise.resolve(new Response(null, { status: 204 }))
    })
    globalThis.fetch = fetchMock as unknown as typeof fetch

    await api.post('/projects', { name: 'x' })

    expect(fetchMock.mock.calls.map((c) => c[0])).toEqual(['/api/auth/session', '/api/v1/projects'])
    expect((fetchMock.mock.calls[0][1] as RequestInit).method).toBe('GET')
    expect((fetchMock.mock.calls[1][1].headers as Record<string, string>)['X-XSRF-TOKEN']).toBe('fresh')
  })

  it('resolves with parsed JSON on 2xx', async () => {
    globalThis.fetch = vi.fn().mockResolvedValue(
      new Response(JSON.stringify({ id: '1', name: 'Test' }), { status: 200 }),
    ) as unknown as typeof fetch

    const result = await api.get<{ id: string; name: string }>('/projects/1')
    expect(result).toEqual({ id: '1', name: 'Test' })
  })

  it('resolves undefined on 204 No Content', async () => {
    document.cookie = 'XSRF-TOKEN=t; path=/'
    globalThis.fetch = vi.fn().mockResolvedValue(new Response(null, { status: 204 })) as unknown as typeof fetch

    const result = await api.del('/projects/1')
    expect(result).toBeUndefined()
  })

  it('throws ApiError carrying the backend ProblemDetail on a 4xx response', async () => {
    document.cookie = 'XSRF-TOKEN=t; path=/'
    const problem = {
      type: 'https://errors.projectmodule/validation-failed',
      title: 'Validation failed',
      status: 400,
      detail: 'name must not be blank',
      traceId: 'corr-1',
    }
    globalThis.fetch = vi.fn().mockResolvedValue(
      new Response(JSON.stringify(problem), { status: 400 }),
    ) as unknown as typeof fetch

    await expect(api.post('/projects', { name: '' })).rejects.toMatchObject({
      status: 400,
      message: 'name must not be blank',
    })
    expect(getSessionState().status).toBe('authenticated')
  })

  it('exposes isConflict for a 409 response', async () => {
    document.cookie = 'XSRF-TOKEN=t; path=/'
    globalThis.fetch = vi.fn().mockResolvedValue(
      new Response(JSON.stringify({ type: 't', title: 't', status: 409, detail: 'stale' }), { status: 409 }),
    ) as unknown as typeof fetch

    try {
      await api.patch('/projects/1', { version: 0 })
      expect.unreachable('should have thrown')
    } catch (err) {
      expect(err).toBeInstanceOf(ApiError)
      expect((err as ApiError).isConflict).toBe(true)
    }
  })

  it('exposes isIntegrationUnavailable for a 503 response', async () => {
    document.cookie = 'XSRF-TOKEN=t; path=/'
    globalThis.fetch = vi.fn().mockResolvedValue(
      new Response(JSON.stringify({ type: 't', title: 't', status: 503, detail: 'AI unavailable' }), { status: 503 }),
    ) as unknown as typeof fetch

    try {
      await api.post('/projects/1/ai/recommendations', {})
      expect.unreachable('should have thrown')
    } catch (err) {
      expect(err).toBeInstanceOf(ApiError)
      expect((err as ApiError).isIntegrationUnavailable).toBe(true)
    }
  })

  it('throws NetworkError when fetch itself rejects', async () => {
    globalThis.fetch = vi.fn().mockRejectedValue(new TypeError('Failed to fetch')) as unknown as typeof fetch

    await expect(api.get('/projects')).rejects.toBeInstanceOf(NetworkError)
  })

  it('treats a 401 as the end of the session: the store becomes unauthenticated and the caller still gets an ApiError', async () => {
    globalThis.fetch = vi.fn().mockResolvedValue(
      problemResponse(401, { type: 'https://errors.projectmodule/identity-missing', status: 401 }),
    ) as unknown as typeof fetch

    await expect(api.get('/projects')).rejects.toMatchObject({ status: 401, isUnauthenticated: true })
    expect(getSessionState().status).toBe('unauthenticated')
    expect(getSessionState().user).toBeNull()
  })

  it('an API call is never redirected or retried on a 401: one request, the 401 as an error, no sign-in started', async () => {
    const fetchMock = vi.fn().mockImplementation(async () =>
      problemResponse(401, { type: 'https://errors.projectmodule/identity-missing', status: 401 }),
    )
    globalThis.fetch = fetchMock as unknown as typeof fetch
    const before = window.location.href

    await expect(api.get('/projects')).rejects.toMatchObject({ status: 401, isUnauthenticated: true })
    await expect(api.post('/projects', {})).rejects.toMatchObject({ status: 401 })

    // Only the page's route guard starts sign-in; the API client never navigates anywhere.
    expect(window.location.href).toBe(before)
    const apiCalls = fetchMock.mock.calls.filter(([url]) => String(url).startsWith('/api/v1/'))
    expect(apiCalls).toHaveLength(2)
  })

  it('reports a workeasy360_access_ended 403 with its reason, keeps the user signed in, and still throws', async () => {
    globalThis.fetch = vi.fn().mockResolvedValue(
      problemResponse(403, { code: 'workeasy360_access_ended', reason: 'no_seat', status: 403 }),
    ) as unknown as typeof fetch

    await expect(api.get('/projects')).rejects.toMatchObject({ status: 403 })
    const state = getSessionState()
    expect(state.status).toBe('authenticated')
    expect(state.accessProblem).toMatchObject({ code: 'workeasy360_access_ended', reason: 'no_seat' })
    expect(state.accessProblem?.message).toMatch(/no longer have an Easy Project seat/)
  })

  it.each([
    ['workeasy360_access_unverified', /can't confirm/i],
    ['workeasy360_read_only', /read-only/i],
  ])('reports a %s 403 to the session', async (code, message) => {
    document.cookie = 'XSRF-TOKEN=t; path=/'
    globalThis.fetch = vi.fn().mockResolvedValue(problemResponse(403, { code, status: 403 })) as unknown as typeof fetch
    await expect(api.post('/projects', {})).rejects.toBeInstanceOf(ApiError)
    expect(getSessionState().accessProblem?.code).toBe(code)
    expect(getSessionState().accessProblem?.message).toMatch(message)
  })

  it('a read-only notice never replaces an access-ended problem', async () => {
    document.cookie = 'XSRF-TOKEN=t; path=/'
    globalThis.fetch = vi
      .fn()
      .mockResolvedValueOnce(problemResponse(403, { code: 'workeasy360_access_ended', reason: 'expired', status: 403 }))
      .mockResolvedValueOnce(problemResponse(403, { code: 'workeasy360_read_only', status: 403 })) as unknown as typeof fetch
    await expect(api.get('/a')).rejects.toBeInstanceOf(ApiError)
    await expect(api.post('/b', {})).rejects.toBeInstanceOf(ApiError)
    expect(getSessionState().accessProblem?.code).toBe('workeasy360_access_ended')
  })

  it('leaves ordinary 403s (project authorization) and CSRF failures alone, with friendly messages', async () => {
    document.cookie = 'XSRF-TOKEN=t; path=/'
    globalThis.fetch = vi
      .fn()
      .mockResolvedValueOnce(problemResponse(403, { detail: 'Only a manager can do this', status: 403 }))
      .mockResolvedValueOnce(problemResponse(403, { code: 'csrf_invalid', detail: 'Invalid CSRF token', status: 403 })) as unknown as typeof fetch
    const first = await api.get('/a').catch((e: unknown) => e)
    expect(toUserMessage(first)).toBe('Only a manager can do this')
    const second = await api.post('/b', {}).catch((e: unknown) => e)
    expect(toUserMessage(second)).toBe('Your session security token was out of date. Reload the page and try again.')
    expect(getSessionState().accessProblem).toBeNull()
    expect(getSessionState().status).toBe('authenticated')
  })

  it('gives the failed write its own read-only message', async () => {
    document.cookie = 'XSRF-TOKEN=t; path=/'
    globalThis.fetch = vi.fn().mockResolvedValue(problemResponse(403, { code: 'workeasy360_read_only', status: 403 })) as unknown as typeof fetch
    const err = await api.patch('/projects/1', {}).catch((e: unknown) => e)
    expect(toUserMessage(err)).toMatch(/read-only: you can view but not change/)
  })
})
