import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { Modal } from '../../components/common/Modal'
import { TextField, TextareaField, SelectField, FormActions } from '../../components/forms/fields'
import { InlineError } from '../../components/common/ErrorState'
import { toUserMessage } from '../../api/errorMessage'
import { projectsApi } from '../../api/projects'
import { PROJECT_PRIORITIES, type Project, type ProjectPriority } from '../../types/project'

export function CreateProjectDialog({
  onClose,
  onCreated,
}: {
  onClose: () => void
  onCreated: (project: Project) => void
}) {
  const navigate = useNavigate()
  const [name, setName] = useState('')
  const [description, setDescription] = useState('')
  const [priority, setPriority] = useState<ProjectPriority>('MEDIUM')
  const [startDate, setStartDate] = useState('')
  const [targetEndDate, setTargetEndDate] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [submitting, setSubmitting] = useState(false)
  const [openAfterCreate, setOpenAfterCreate] = useState(true)

  async function handleSubmit(event: React.FormEvent) {
    event.preventDefault()
    setSubmitting(true)
    setError(null)
    try {
      const project = await projectsApi.create({
        name,
        description: description || null,
        priority,
        startDate: startDate || null,
        targetEndDate: targetEndDate || null,
      })
      onCreated(project)
      if (openAfterCreate) navigate(`/projects/${project.id}`)
    } catch (err) {
      setError(toUserMessage(err))
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <Modal title="New Project" onClose={onClose}>
      <form onSubmit={handleSubmit} className="form-grid">
        {error && <InlineError message={error} />}
        <TextField id="name" label="Name" value={name} onChange={setName} required maxLength={200} />
        <TextareaField id="description" label="Description" value={description} onChange={setDescription} />
        <SelectField
          id="priority"
          label="Priority"
          value={priority}
          onChange={setPriority}
          required
          options={PROJECT_PRIORITIES.map((p) => ({ value: p, label: p }))}
        />
        <div className="form-row">
          <TextField id="startDate" label="Start date" type="date" value={startDate} onChange={setStartDate} />
          <TextField
            id="targetEndDate"
            label="Target end date"
            type="date"
            value={targetEndDate}
            onChange={setTargetEndDate}
          />
        </div>
        <label className="checkbox-row">
          <input
            type="checkbox"
            checked={openAfterCreate}
            onChange={(e) => setOpenAfterCreate(e.target.checked)}
          />
          Open the project after creating it
        </label>
        <FormActions>
          <button type="button" className="btn" onClick={onClose} disabled={submitting}>
            Cancel
          </button>
          <button type="submit" className="btn btn-primary" disabled={submitting || !name.trim()}>
            {submitting ? 'Creating…' : 'Create Project'}
          </button>
        </FormActions>
      </form>
    </Modal>
  )
}
