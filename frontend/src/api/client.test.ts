import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { api, ApiError, NetworkError } from './client'
import { setIdentity } from '../context/identityStore'

const originalFetch = globalThis.fetch

describe('api client', () => {
  beforeEach(() => {
    setIdentity({ userId: 'user-1', orgId: 'org-1' })
  })

  afterEach(() => {
    globalThis.fetch = originalFetch
    setIdentity(null)
    vi.restoreAllMocks()
  })

  it('sends X-User-Id and X-Org-Id headers on every request', async () => {
    const fetchMock = vi.fn().mockResolvedValue(
      new Response(JSON.stringify({ ok: true }), { status: 200, headers: { 'Content-Type': 'application/json' } }),
    )
    globalThis.fetch = fetchMock as unknown as typeof fetch

    await api.get('/projects')

    expect(fetchMock).toHaveBeenCalledTimes(1)
    const [, init] = fetchMock.mock.calls[0] as [string, RequestInit]
    const headers = init.headers as Record<string, string>
    expect(headers['X-User-Id']).toBe('user-1')
    expect(headers['X-Org-Id']).toBe('org-1')
    expect(headers['X-Correlation-Id']).toBeTruthy()
  })

  it('resolves with parsed JSON on 2xx', async () => {
    globalThis.fetch = vi.fn().mockResolvedValue(
      new Response(JSON.stringify({ id: '1', name: 'Test' }), { status: 200 }),
    ) as unknown as typeof fetch

    const result = await api.get<{ id: string; name: string }>('/projects/1')
    expect(result).toEqual({ id: '1', name: 'Test' })
  })

  it('resolves undefined on 204 No Content', async () => {
    globalThis.fetch = vi.fn().mockResolvedValue(new Response(null, { status: 204 })) as unknown as typeof fetch

    const result = await api.del('/projects/1')
    expect(result).toBeUndefined()
  })

  it('throws ApiError carrying the backend ProblemDetail on a 4xx response', async () => {
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
  })

  it('exposes isConflict for a 409 response', async () => {
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
})
