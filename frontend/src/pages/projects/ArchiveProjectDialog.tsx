import { useNavigate } from 'react-router-dom'
import { ConfirmDialog } from '../../components/common/ConfirmDialog'
import { projectsApi } from '../../api/projects'
import { useProjectWorkspace } from '../../context/ProjectWorkspaceContext'

export function ArchiveProjectDialog({ onClose }: { onClose: () => void }) {
  const { project } = useProjectWorkspace()
  const navigate = useNavigate()

  return (
    <ConfirmDialog
      title="Archive project"
      message={`Archive "${project.name}"? It will be hidden from the active project list. This does not delete any data.`}
      confirmLabel="Archive"
      onConfirm={async () => {
        await projectsApi.archive(project.id)
        navigate('/projects')
      }}
      onClose={onClose}
    />
  )
}
