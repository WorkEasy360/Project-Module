import { useState } from 'react'
import { splitTags } from './CreateProjectDialog'
import { Modal } from '../../components/common/Modal'
import { TextField, TextareaField, SelectField, FormActions } from '../../components/forms/fields'
import { InlineError } from '../../components/common/ErrorState'
import { toUserMessage } from '../../api/errorMessage'
import { projectsApi } from '../../api/projects'
import { tasksApi } from '../../api/tasks'
import { PROJECT_STATUSES, type ProjectStatus } from '../../types/project'
import { useProjectWorkspace } from '../../context/ProjectWorkspaceContext'
import { usePeople } from '../../context/PeopleContext'
import { ApiError } from '../../api/client'
import { humanizeToken } from '../../utils/format'

/**
 * Edit a project. V3: statuses are Active, On Hold and Completed (archiving is separate); marking a
 * project Completed while tasks are unfinished asks first and never completes those tasks.
 */
export function EditProjectDialog({ onClose }: { onClose: () => void }) {
  const { project, reloadProject } = useProjectWorkspace()
  const { nameOf } = usePeople()
  const [name, setName] = useState(project.name)
  const [description, setDescription] = useState(project.description ?? '')
  const [status, setStatus] = useState<ProjectStatus>(PROJECT_STATUSES.includes(project.status) ? project.status : 'ACTIVE')
  const [startDate, setStartDate] = useState(project.startDate ?? '')
  const [targetEndDate, setTargetEndDate] = useState(project.targetEndDate ?? '')
  const [customerContact, setCustomerContact] = useState(project.customerContact ?? '')
  const [tags, setTags] = useState((project.tags ?? []).join(', '))
  const [error, setError] = useState<string | null>(null)
  const [conflict, setConflict] = useState(false)
  const [submitting, setSubmitting] = useState(false)
  const [unfinished, setUnfinished] = useState<number | null>(null)

  async function save() {
    setSubmitting(true)
    setError(null)
    setConflict(false)
    try {
      await projectsApi.update(project.id, {
        name,
        description: description || null,
        status,
        startDate: startDate || null,
        targetEndDate: targetEndDate || null,
        version: project.version,
        customerContact: customerContact.trim(),
        tags: splitTags(tags),
      })
      reloadProject()
      onClose()
    } catch (err) {
      if (err instanceof ApiError && err.isConflict) setConflict(true)
      setError(toUserMessage(err))
    } finally {
      setSubmitting(false)
    }
  }

  async function handleSubmit(event: React.FormEvent) {
    event.preventDefault()
    if (status === 'COMPLETED' && project.status !== 'COMPLETED' && unfinished === null) {
      try {
        const tasks = await tasksApi.list(project.id, { archived: false })
        const open = tasks.filter((t) => t.status !== 'COMPLETED').length
        if (open > 0) {
          setUnfinished(open)
          return
        }
      } catch (err) {
        setError(toUserMessage(err))
        return
      }
    }
    await save()
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
        {unfinished !== null && (
          <div className="banner banner-warning" role="alert">
            This project still has {unfinished} unfinished {unfinished === 1 ? 'task' : 'tasks'}. Marking it Completed will not
            complete them; they stay as they are. Press <strong>Save Changes</strong> again to continue, or change the status.
          </div>
        )}
        <TextField id="edit-name" label="Project Name" value={name} onChange={setName} required maxLength={200} />
        <div className="form-row">
          <div className="field">
            <span className="field-label">Project Owner</span>
            <span className="field-static">{nameOf(project.ownerId)}</span>
          </div>
          <SelectField
            id="edit-status"
            label="Status"
            value={status}
            onChange={(v) => {
              setStatus(v)
              setUnfinished(null)
            }}
            required
            options={PROJECT_STATUSES.map((s) => ({ value: s, label: humanizeToken(s) }))}
          />
        </div>
        <TextareaField id="edit-description" label="Description" value={description} onChange={setDescription} />
        <div className="form-row">
          <TextField id="edit-start" label="Start date" type="date" value={startDate} onChange={setStartDate} />
          <TextField id="edit-target" label="Due date" type="date" value={targetEndDate} onChange={setTargetEndDate} />
        </div>
        <div className="form-row">
          <TextField id="edit-customer" label="Customer / Contact" value={customerContact} onChange={setCustomerContact} maxLength={200} />
          <TextField id="edit-tags" label="Tags" value={tags} onChange={setTags} placeholder="Comma separated" />
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
