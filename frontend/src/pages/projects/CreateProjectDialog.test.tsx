import { describe, expect, it, vi, beforeEach } from 'vitest'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import { CreateProjectDialog } from './CreateProjectDialog'
import { projectsApi } from '../../api/projects'
import { ApiError } from '../../api/client'

vi.mock('../../api/projects', () => ({
  projectsApi: {
    create: vi.fn(),
  },
}))

describe('CreateProjectDialog', () => {
  beforeEach(() => {
    vi.mocked(projectsApi.create).mockReset()
  })

  it('disables submit until a name is entered', async () => {
    render(
      <MemoryRouter>
        <CreateProjectDialog onClose={vi.fn()} onCreated={vi.fn()} />
      </MemoryRouter>,
    )

    const submit = screen.getByRole('button', { name: /create project/i })
    expect(submit).toBeDisabled()

    await userEvent.type(screen.getByLabelText(/^name/i), 'New Project')
    expect(submit).toBeEnabled()
  })

  it('submits the create request with the entered fields and calls onCreated', async () => {
    vi.mocked(projectsApi.create).mockResolvedValue({
      id: 'p1',
      organizationId: 'o1',
      name: 'New Project',
      description: null,
      status: 'PLANNING',
      priority: 'MEDIUM',
      startDate: null,
      targetEndDate: null,
      ownerId: 'u1',
      archived: false,
      archivedAt: null,
      archivedBy: null,
      createdAt: '2026-01-01T00:00:00Z',
      updatedAt: '2026-01-01T00:00:00Z',
      version: 0,
    })
    const onCreated = vi.fn()

    render(
      <MemoryRouter>
        <CreateProjectDialog onClose={vi.fn()} onCreated={onCreated} />
      </MemoryRouter>,
    )

    await userEvent.type(screen.getByLabelText(/^name/i), 'New Project')
    await userEvent.click(screen.getByRole('button', { name: /create project/i }))

    expect(projectsApi.create).toHaveBeenCalledWith(
      expect.objectContaining({ name: 'New Project', priority: 'MEDIUM' }),
    )
    expect(onCreated).toHaveBeenCalled()
  })

  it('shows the backend validation message on failure instead of a generic error', async () => {
    vi.mocked(projectsApi.create).mockRejectedValue(
      new ApiError(
        400,
        {
          type: 'https://errors.projectmodule/validation-failed',
          title: 'Validation failed',
          status: 400,
          detail: 'name must not be blank',
        },
        'fallback',
      ),
    )

    render(
      <MemoryRouter>
        <CreateProjectDialog onClose={vi.fn()} onCreated={vi.fn()} />
      </MemoryRouter>,
    )

    await userEvent.type(screen.getByLabelText(/^name/i), 'x')
    await userEvent.click(screen.getByRole('button', { name: /create project/i }))

    expect(await screen.findByText('name must not be blank')).toBeInTheDocument()
  })
})
