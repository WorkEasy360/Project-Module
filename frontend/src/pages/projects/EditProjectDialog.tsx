import { useState } from 'react'
import { Modal } from '../../components/common/Modal'
import { TextField, TextareaField, SelectField, FormActions } from '../../components/forms/fields'
import { InlineError } from '../../components/common/ErrorState'
import { toUserMessage } from '../../api/errorMessage'
import { projectsApi } from '../../api/projects'
import { PROJECT_PRIORITIES, PROJECT_STATUSES, type ProjectPriority, type ProjectStatus } from '../../types/project'
import { useProjectWorkspace } from '../../context/ProjectWorkspaceContext'
import { ApiError } from '../../api/client'

export function EditProjectDialog({ onClose }: { onClose: () => void }) {
  const { project, reloadProject } = useProjectWorkspace()
  const [name, setName] = useState(project.name)
  const [description, setDescription] = useState(project.description ?? '')
  const [status, setStatus] = useState<ProjectStatus>(project.status)
  const [priority, setPriority] = useState<ProjectPriority>(project.priority)
  const [startDate, setStartDate] = useState(project.startDate ?? '')
  const [targetEndDate, setTargetEndDate] = useState(project.targetEndDate ?? '')
  const [error, setError] = useState<string | null>(null)
  const [conflict, setConflict] = useState(false)
  const [submitting, setSubmitting] = useState(false)

  async function handleSubmit(event: React.FormEvent) {
    event.preventDefault()
    setSubmitting(true)
    setError(null)
    setConflict(false)
    try {
      await projectsApi.update(project.id, {
        name,
        description: description || null,
        status,
        priority,
        startDate: startDate || null,
        targetEndDate: targetEndDate || null,
        version: project.version,
      })
      reloadProject()
      onClose()
    } catch (err) {
      if (err instanceof ApiError && err.isConflict) {
        setConflict(true)
      }
      setError(toUserMessage(err))
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <Modal title="Edit Project" onClose={onClose}>
      <form onSubmit={handleSubmit} className="form-grid">
        {error && <InlineError message={error} />}
        {conflict && (
          <div className="banner banner-warning">
            This project was modified elsewhere. <button type="button" className="btn btn-sm" onClick={() => { reloadProject(); onClose() }}>Reload it</button> before editing again.
          </div>
        )}
        <TextField id="edit-name" label="Name" value={name} onChange={setName} required maxLength={200} />
        <TextareaField id="edit-description" label="Description" value={description} onChange={setDescription} />
        <div className="form-row">
          <SelectField
            id="edit-status"
            label="Status"
            value={status}
            onChange={setStatus}
            required
            options={PROJECT_STATUSES.map((s) => ({ value: s, label: s }))}
          />
          <SelectField
            id="edit-priority"
            label="Priority"
            value={priority}
            onChange={setPriority}
            required
            options={PROJECT_PRIORITIES.map((p) => ({ value: p, label: p }))}
          />
        </div>
        <div className="form-row">
          <TextField id="edit-start" label="Start date" type="date" value={startDate} onChange={setStartDate} />
          <TextField
            id="edit-target"
            label="Target end date"
            type="date"
            value={targetEndDate}
            onChange={setTargetEndDate}
          />
        </div>
        <FormActions>
          <button type="button" className="btn" onClick={onClose} disabled={submitting}>
            Cancel
          </button>
          <button type="submit" className="btn btn-primary" disabled={submitting || !name.trim()}>
            {submitting ? 'Saving…' : 'Save Changes'}
          </button>
        </FormActions>
      </form>
    </Modal>
  )
}
