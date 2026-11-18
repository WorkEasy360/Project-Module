import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { Modal } from '../../components/common/Modal'
import { TextField, TextareaField, FormActions } from '../../components/forms/fields'
import { InlineError } from '../../components/common/ErrorState'
import { toUserMessage } from '../../api/errorMessage'
import { projectsApi } from '../../api/projects'
import { useIdentity } from '../../context/IdentityContext'
import type { Project } from '../../types/project'

/**
 * V3 Create Project: Project Name and Owner first (the creator is the owner); description, dates,
 * customer/contact and tags sit behind More Details.
 */
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
  const { identity } = useIdentity()
  const [moreOpen, setMoreOpen] = useState(false)
  const [startDate, setStartDate] = useState('')
  const [targetEndDate, setTargetEndDate] = useState('')
  const [customerContact, setCustomerContact] = useState('')
  const [tags, setTags] = useState('')
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
        priority: 'MEDIUM',
        startDate: startDate || null,
        targetEndDate: targetEndDate || null,
        customerContact: customerContact.trim() || null,
        tags: splitTags(tags),
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
        <TextField id="name" label="Project Name" value={name} onChange={setName} required maxLength={200} />
        <div className="field">
          <span className="field-label">Project Owner</span>
          <span className="field-static">{identity?.name ? `${identity.name} (you)` : 'You'}</span>
        </div>
        <button type="button" className="btn btn-sm btn-ghost more-details-toggle" aria-expanded={moreOpen} onClick={() => setMoreOpen(!moreOpen)}>
          {moreOpen ? 'Fewer details' : 'More Details'}
        </button>
        {moreOpen && (
          <>
            <TextareaField id="description" label="Description" value={description} onChange={setDescription} />
            <div className="form-row">
              <TextField id="startDate" label="Start date" type="date" value={startDate} onChange={setStartDate} />
              <TextField id="targetEndDate" label="Due date" type="date" value={targetEndDate} onChange={setTargetEndDate} />
            </div>
            <div className="form-row">
              <TextField id="customer" label="Customer / Contact" value={customerContact} onChange={setCustomerContact} maxLength={200} placeholder="e.g. Rajni Machinery Store" />
              <TextField id="tags" label="Tags" value={tags} onChange={setTags} placeholder="e.g. Website, Marketing" />
            </div>
          </>
        )}
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

/** "Website, Marketing" becomes ["Website", "Marketing"]. */
export function splitTags(text: string): string[] {
  return text.split(',').map((t) => t.trim()).filter(Boolean)
}
