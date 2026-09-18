import { describe, expect, it, vi, beforeEach } from 'vitest'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { RequestRecommendationDialog } from './AITab'
import { aiApi } from '../../../api/ai'
import { tasksApi } from '../../../api/tasks'
import { ApiError } from '../../../api/client'

vi.mock('../../../api/ai', () => ({
  aiApi: { request: vi.fn() },
}))
vi.mock('../../../api/tasks', () => ({
  tasksApi: { list: vi.fn().mockResolvedValue([]) },
}))

describe('RequestRecommendationDialog — AI provider unavailable', () => {
  beforeEach(() => {
    vi.mocked(aiApi.request).mockReset()
    vi.mocked(tasksApi.list).mockClear()
  })

  it('shows a clear unavailable message and never displays fabricated AI content when the backend returns 503', async () => {
    vi.mocked(aiApi.request).mockRejectedValue(
      new ApiError(
        503,
        {
          type: 'https://errors.projectmodule/integration-unavailable',
          title: 'Integration unavailable',
          status: 503,
          detail: 'The AI provider is not configured.',
        },
        'fallback',
      ),
    )

    render(<RequestRecommendationDialog projectId="proj-1" onClose={vi.fn()} onRequested={vi.fn()} />)

    // PROJECT_SUMMARY is the default type and needs neither a task nor a question.
    await userEvent.click(screen.getByRole('button', { name: /^request$/i }))

    expect(
      await screen.findByText(/AI service is currently unavailable/i),
    ).toBeInTheDocument()

    // No recommendation content of any kind should be rendered — the dialog only ever shows the
    // unavailable banner, never a generated title/rationale.
    expect(screen.queryByText(/rationale/i)).not.toBeInTheDocument()
  })

  it('does not call onRequested when the request fails', async () => {
    vi.mocked(aiApi.request).mockRejectedValue(
      new ApiError(503, { type: 't', title: 't', status: 503, detail: 'unavailable' }, 'fallback'),
    )
    const onRequested = vi.fn()

    render(<RequestRecommendationDialog projectId="proj-1" onClose={vi.fn()} onRequested={onRequested} />)
    await userEvent.click(screen.getByRole('button', { name: /^request$/i }))

    await screen.findByText(/AI service is currently unavailable/i)
    expect(onRequested).not.toHaveBeenCalled()
  })
})
