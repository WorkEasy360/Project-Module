import { describe, expect, it, vi, beforeEach } from 'vitest'
import { render, screen, fireEvent, waitFor } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { KanbanTab } from './KanbanTab'
import { tasksApi } from '../../../api/tasks'
import { ProjectWorkspaceContext, type ProjectWorkspaceValue } from '../../../context/ProjectWorkspaceContext'
import type { Task } from '../../../types/work'
import type { Project } from '../../../types/project'

vi.mock('../../../api/tasks', () => ({
  tasksApi: { board: vi.fn(), update: vi.fn() },
}))

const project: Project = {
  id: 'p1', organizationId: 'o1', name: 'P', description: null, status: 'ACTIVE', priority: 'MEDIUM',
  startDate: null, targetEndDate: null, ownerId: 'u1', archived: false, archivedAt: null, archivedBy: null,
  createdAt: '', updatedAt: '', version: 0,
}
const task: Task = {
  id: 't1', projectId: 'p1', name: 'Repro task', description: null, dueDate: null, assigneeId: null,
  status: 'TODO', archived: false, createdAt: '', updatedAt: '', version: 0,
}

function board(status: Task['status']) {
  return {
    columns: (['TODO', 'BLOCKED', 'OVERDUE', 'COMPLETED'] as const).map((s) => ({
      status: s,
      tasks: s === status ? [{ ...task, status }] : [],
    })),
  }
}

function renderBoard() {
  const value: ProjectWorkspaceValue = {
    project,
    reloadProject: vi.fn(),
    currentMember: { id: 'm', projectId: 'p1', userId: 'u1', role: 'OWNER', joinedAt: '', version: 0 },
    can: () => true,
  }
  return render(
    <MemoryRouter initialEntries={['/projects/p1/kanban']}>
      <ProjectWorkspaceContext.Provider value={value}>
        <KanbanTab />
      </ProjectWorkspaceContext.Provider>
    </MemoryRouter>,
  )
}

describe('KanbanTab', () => {
  beforeEach(() => {
    vi.mocked(tasksApi.board).mockReset()
    vi.mocked(tasksApi.update).mockReset()
  })

  it('renders the task in the column the backend put it in', async () => {
    vi.mocked(tasksApi.board).mockResolvedValue(board('BLOCKED'))
    const { container } = renderBoard()
    await screen.findByText('Repro task')
    const columns = container.querySelectorAll('.kanban-column')
    expect(columns[1]).toHaveTextContent('Repro task')
    expect(columns[0]).not.toHaveTextContent('Repro task')
  })

  it('dropping a card on Blocked PATCHes status=BLOCKED and re-fetches the board', async () => {
    vi.mocked(tasksApi.board).mockResolvedValueOnce(board('TODO')).mockResolvedValueOnce(board('BLOCKED'))
    vi.mocked(tasksApi.update).mockResolvedValue({ ...task, status: 'BLOCKED', version: 1 })
    const { container } = renderBoard()
    const card = (await screen.findByText('Repro task')).closest('.kanban-card')!
    const blockedColumn = container.querySelectorAll('.kanban-column')[1]

    fireEvent.dragStart(card)
    fireEvent.dragOver(blockedColumn)
    fireEvent.drop(blockedColumn)

    await waitFor(() =>
      expect(tasksApi.update).toHaveBeenCalledWith('t1', { status: 'BLOCKED', version: 0 }),
    )
    await waitFor(() => expect(container.querySelectorAll('.kanban-column')[1]).toHaveTextContent('Repro task'))
  })
})
