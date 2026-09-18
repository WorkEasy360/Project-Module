import { describe, expect, it, vi, beforeEach } from 'vitest'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { TaskStatusControl } from './TaskStatusControl'
import { tasksApi } from '../../api/tasks'
import { ApiError } from '../../api/client'
import type { Task } from '../../types/work'

vi.mock('../../api/tasks', () => ({ tasksApi: { update: vi.fn() } }))

const task: Task = {
  id: 't1', projectId: 'p1', name: 'Repro task', description: null, dueDate: null, assigneeId: null,
  status: 'TODO', archived: false, createdAt: '', updatedAt: '', version: 3,
}

describe('TaskStatusControl', () => {
  beforeEach(() => vi.clearAllMocks())

  it('offers only the transitions the backend allows from the current status', () => {
    render(<TaskStatusControl task={task} onChanged={vi.fn()} />)
    const options = Array.from((screen.getByRole('combobox') as HTMLSelectElement).options).map((o) => o.value)
    expect(options).toEqual(['', 'BLOCKED', 'OVERDUE', 'COMPLETED'])
  })

  it('renders nothing for a COMPLETED task (no transitions)', () => {
    const { container } = render(<TaskStatusControl task={{ ...task, status: 'COMPLETED' }} onChanged={vi.fn()} />)
    expect(container).toBeEmptyDOMElement()
  })

  it('PATCHes the chosen status with the task version, then asks the parent to reload', async () => {
    vi.mocked(tasksApi.update).mockImplementation(() => Promise.resolve({ ...task, status: 'BLOCKED', version: 4 }))
    const onChanged = vi.fn()
    render(<TaskStatusControl task={task} onChanged={onChanged} />)

    await userEvent.selectOptions(screen.getByRole('combobox'), 'BLOCKED')

    expect(tasksApi.update).toHaveBeenCalledWith('t1', { status: 'BLOCKED', version: 3 })
    await waitFor(() => expect(onChanged).toHaveBeenCalled())
  })

  it('shows the backend message and still reloads when the update is rejected', async () => {
    vi.mocked(tasksApi.update).mockImplementation(() =>
      Promise.reject(new ApiError(422, { type: 't', title: 't', status: 422, detail: 'Task is already blocked' }, 'x')),
    )
    const onChanged = vi.fn()
    render(<TaskStatusControl task={task} onChanged={onChanged} />)

    await userEvent.selectOptions(screen.getByRole('combobox'), 'BLOCKED')

    await waitFor(() => expect(onChanged).toHaveBeenCalled())
    expect(await screen.findByRole('alert')).toHaveTextContent('Task is already blocked')
  })
})
