import { PriorityBadge } from '../../../components/common/Badge'
import { usePeople } from '../../../context/PeopleContext'
import { useState } from 'react'
import { Link } from 'react-router-dom'
import { useProjectWorkspace } from '../../../context/ProjectWorkspaceContext'
import { useAsyncData } from '../../../hooks/useAsyncData'
import { tasksApi } from '../../../api/tasks'
import { LoadingState } from '../../../components/common/LoadingState'
import { ErrorState, InlineError } from '../../../components/common/ErrorState'
import { formatDate } from '../../../utils/format'
import { TASK_STATUS_TRANSITIONS, type KanbanColumn, type Task, type TaskStatus, TASK_STATUSES } from '../../../types/work'
import { toUserMessage } from '../../../api/errorMessage'
import { TaskStatusControl } from '../../../components/task/TaskStatusControl'
import { ListBoardSwitch } from '../../../components/task/ListBoardSwitch'
import { taskStatusLabel } from '../../../components/common/Badge'
import { Avatar } from '../../../components/common/Avatar'

export function KanbanTab() {
  const { nameOf } = usePeople()
  const { project, can } = useProjectWorkspace()
  const { data, loading, error, reload } = useAsyncData(() => tasksApi.board(project.id), [project.id])
  const [draggingTask, setDraggingTask] = useState<Task | null>(null)
  const [dropTarget, setDropTarget] = useState<TaskStatus | null>(null)
  const [message, setMessage] = useState<string | null>(null)
  const [moving, setMoving] = useState(false)

  if (loading) return <LoadingState label="Loading board…" />
  if (error) return <ErrorState message={error} onRetry={reload} />
  if (!data) return null

  async function handleDrop(column: KanbanColumn) {
    setDropTarget(null)
    if (!draggingTask || !can('EDIT_TASKS')) return
    const task = draggingTask
    setDraggingTask(null)
    if (task.status === column.status) return

    const allowed = TASK_STATUS_TRANSITIONS[task.status].includes(column.status)
    if (!allowed) {
      setMessage(`Cannot move a task from ${taskStatusLabel(task.status)} to ${taskStatusLabel(column.status)}.`)
      return
    }

    setMoving(true)
    setMessage(null)
    try {
      await tasksApi.update(task.id, { status: column.status, version: task.version })
      reload()
    } catch (err) {
      setMessage(toUserMessage(err))
      reload()
    } finally {
      setMoving(false)
    }
  }

  return (
    <div>
      <ListBoardSwitch current="board" />
      {message && <InlineError message={message} />}
      {moving && <p className="text-muted">Updating…</p>}
      <div className="kanban-board">
        {data.columns.filter((column) => TASK_STATUSES.includes(column.status)).map((column) => (
          <div
            key={column.status}
            className={`kanban-column kanban-status-${column.status.toLowerCase()}${dropTarget === column.status ? ' drop-target' : ''}`}
            onDragOver={(e) => {
              e.preventDefault()
              setDropTarget(column.status)
            }}
            onDragLeave={() => setDropTarget((t) => (t === column.status ? null : t))}
            onDrop={(e) => {
              e.preventDefault()
              handleDrop(column)
            }}
          >
            <div className="kanban-column-header">
              <span>{taskStatusLabel(column.status)}</span>
              <span className="text-muted">{column.tasks.length}</span>
            </div>
            <div className="kanban-column-body">
              {column.tasks.map((task) => (
                <div
                  key={task.id}
                  className={`kanban-card${draggingTask?.id === task.id ? ' dragging' : ''}`}
                  draggable={can('EDIT_TASKS')}
                  onDragStart={() => setDraggingTask(task)}
                  onDragEnd={() => setDraggingTask(null)}
                >
                  <Link to={`../tasks/${task.id}`} relative="path">
                    {task.name}
                  </Link>
                  <div className="kanban-card-meta">
                    <PriorityBadge priority={task.priority} />
                    {task.dueDate && <span className="text-faint">{formatDate(task.dueDate)}</span>}
                    {task.assigneeId && (
                      <span title={nameOf(task.assigneeId)}>
                        <Avatar userId={task.assigneeId} name={nameOf(task.assigneeId)} className="task-avatar sm" />
                      </span>
                    )}
                  </div>
                  {can('EDIT_TASKS') && (
                    <div className="kanban-card-actions">
                      <TaskStatusControl task={task} onChanged={reload} compact />
                    </div>
                  )}
                </div>
              ))}
              {column.tasks.length === 0 && <div className="text-faint">No tasks</div>}
            </div>
          </div>
        ))}
      </div>
    </div>
  )
}
