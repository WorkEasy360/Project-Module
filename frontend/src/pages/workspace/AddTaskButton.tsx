import { useState } from 'react'
import { Icon } from '../../components/common/Icon'
import { Modal } from '../../components/common/Modal'
import { CreateTaskDialog } from '../projects/CreateTaskDialog'
import type { ProjectSummary } from '../../types/project'
import type { Task } from '../../types/work'

/**
 * "Add Task" from outside a project: asks which project the task belongs to, then opens the one
 * shared task form. Rendered as a quick-action tile or as a plain primary button.
 */
export function AddTaskButton({
  projects,
  onCreated,
  variant = 'button',
}: {
  projects: ProjectSummary[]
  onCreated: (task: Task) => void
  variant?: 'button' | 'quick'
}) {
  const [picking, setPicking] = useState(false)
  const [projectId, setProjectId] = useState<string | null>(null)
  return (
    <>
      {variant === 'quick' ? (
        <button type="button" className="ops-quick-btn tone-green" onClick={() => setPicking(true)}>
          <Icon name="plus" size={34} />
          Add Task
        </button>
      ) : (
        <button type="button" className="ops-primary" onClick={() => setPicking(true)}>
          <Icon name="plus" size={22} /> Add Task
        </button>
      )}
      {picking && (
        <Modal title="Add a task" onClose={() => setPicking(false)}>
          {projects.length === 0 ? (
            <p>Create a project first; tasks belong to a project.</p>
          ) : (
            <>
              <p>Which project is the task for?</p>
              <ul className="ops-pick-list">
                {projects.map((p) => (
                  <li key={p.id}>
                    <button
                      type="button"
                      onClick={() => {
                        setPicking(false)
                        setProjectId(p.id)
                      }}
                    >
                      <Icon name="folder" size={18} /> {p.name}
                    </button>
                  </li>
                ))}
              </ul>
            </>
          )}
        </Modal>
      )}
      {projectId && (
        <CreateTaskDialog
          projectId={projectId}
          onClose={() => setProjectId(null)}
          onCreated={(task) => {
            setProjectId(null)
            onCreated(task)
          }}
        />
      )}
    </>
  )
}
