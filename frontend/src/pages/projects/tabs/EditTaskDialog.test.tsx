import { describe, expect, it, vi, beforeEach } from 'vitest'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { EditTaskDialog } from './TaskDetailPage'
import { tasksApi } from '../../../api/tasks'
import type { Task } from '../../../types/work'

vi.mock('../../../api/tasks', () => ({
  tasksApi: { update: vi.fn() },
}))

const todoTask: Task = {
  id: 't1',
  projectId: 'p1',
  name: 'Repro task',
  description: null,
  dueDate: null,
  assigneeId: null,
  status: 'TODO',
  archived: false,
  createdAt: '2026-01-01T00:00:00Z',
  updatedAt: '2026-01-01T00:00:00Z',
  version: 0,
}

describe('EditTaskDialog — status change reaches the API', () => {
  beforeEach(() => {
    vi.mocked(tasksApi.update).mockReset()
    vi.mocked(tasksApi.update).mockResolvedValue({ ...todoTask, status: 'BLOCKED', version: 1 })
  })

  it('sends status=BLOCKED with the task version when Blocked is selected and saved', async () => {
    const onSaved = vi.fn()
    render(<EditTaskDialog task={todoTask} onClose={vi.fn()} onSaved={onSaved} />)

    await userEvent.selectOptions(screen.getByLabelText(/^status/i), 'BLOCKED')
    await userEvent.click(screen.getByRole('button', { name: /^save$/i }))

    expect(tasksApi.update).toHaveBeenCalledWith('t1', expect.objectContaining({ status: 'BLOCKED', version: 0 }))
    expect(onSaved).toHaveBeenCalled()
  })

  it('omits status entirely when the user keeps the current status', async () => {
    render(<EditTaskDialog task={todoTask} onClose={vi.fn()} onSaved={vi.fn()} />)
    await userEvent.click(screen.getByRole('button', { name: /^save$/i }))
    const body = vi.mocked(tasksApi.update).mock.calls[0][1]
    expect(body).not.toHaveProperty('status')
  })
})
