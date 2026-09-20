import { useState } from 'react'
import { Modal } from '../../components/common/Modal'
import { TextField, TextareaField, SelectField, FormActions } from '../../components/forms/fields'
import { InlineError } from '../../components/common/ErrorState'
import { toUserMessage } from '../../api/errorMessage'
import { milestonesApi } from '../../api/milestones'
import { phasesApi } from '../../api/phases'
import { useAsyncData } from '../../hooks/useAsyncData'
import { MILESTONE_STATUS_TRANSITIONS, type Milestone, type MilestoneStatus } from '../../types/work'

export interface MilestoneFormValues {
  name: string
  description: string | null
  dueDate: string | null
  phaseId: string | null
  status?: MilestoneStatus
}

/**
 * Shared create/edit form for milestones (used by the Milestones tab and the calendar). Only
 * the fields the backend accepts (name, description, due date, phase; status on edit, forward
 * transitions only). The caller decides which API call to make with the values.
 */
export function MilestoneFormDialog({
  title,
  initial,
  phaseOptions,
  initialDueDate = '',
  onClose,
  onSubmit,
}: {
  title: string
  initial: Milestone | null
  phaseOptions: { id: string; name: string }[]
  /** Pre-fills the due date on create (used by the calendar when adding on a selected day). */
  initialDueDate?: string
  onClose: () => void
  onSubmit: (values: MilestoneFormValues) => Promise<void>
}) {
  const [name, setName] = useState(initial?.name ?? '')
  const [description, setDescription] = useState(initial?.description ?? '')
  const [dueDate, setDueDate] = useState(initial?.dueDate ?? initialDueDate)
  const [phaseId, setPhaseId] = useState(initial?.phaseId ?? '')
  const [status, setStatus] = useState<MilestoneStatus | ''>(initial?.status ?? '')
  const [error, setError] = useState<string | null>(null)
  const [submitting, setSubmitting] = useState(false)

  const allowedStatuses = initial ? MILESTONE_STATUS_TRANSITIONS[initial.status] : []

  async function handleSubmit(event: React.FormEvent) {
    event.preventDefault()
    setSubmitting(true)
    setError(null)
    try {
      await onSubmit({
        name,
        description: description || null,
        dueDate: dueDate || null,
        phaseId: phaseId || null,
        ...(status ? { status: status as MilestoneStatus } : {}),
      })
      onClose()
    } catch (err) {
      setError(toUserMessage(err))
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <Modal title={title} onClose={onClose}>
      <form onSubmit={handleSubmit} className="form-grid">
        {error && <InlineError message={error} />}
        <TextField id="milestone-name" label="Name" value={name} onChange={setName} required maxLength={200} />
        <TextareaField id="milestone-description" label="Description" value={description} onChange={setDescription} />
        <div className="form-row">
          <TextField id="milestone-due" label="Due date" type="date" value={dueDate} onChange={setDueDate} />
          <SelectField
            id="milestone-phase"
            label="Phase"
            value={phaseId}
            onChange={setPhaseId}
            allowEmpty
            emptyLabel="No phase"
            options={phaseOptions.map((p) => ({ value: p.id, label: p.name }))}
          />
        </div>
        {initial && (
          <SelectField
            id="milestone-status"
            label={`Status (currently ${initial.status})`}
            value={status}
            onChange={(v) => setStatus(v as MilestoneStatus)}
            allowEmpty
            emptyLabel="Keep current status"
            options={allowedStatuses.map((s) => ({ value: s, label: s }))}
            hint={allowedStatuses.length === 0 ? 'No further transitions allowed.' : undefined}
          />
        )}
        <FormActions>
          <button type="button" className="btn" onClick={onClose} disabled={submitting}>
            Cancel
          </button>
          <button type="submit" className="btn btn-primary" disabled={submitting || !name.trim()}>
            {submitting ? 'Saving…' : 'Save'}
          </button>
        </FormActions>
      </form>
    </Modal>
  )
}

/**
 * Self-contained "New Milestone" dialog: loads the project's phases for the phase select and
 * creates through `POST /projects/{id}/milestones`. Used by the calendar's "+ Add" menu.
 */
export function CreateMilestoneDialog({
  projectId,
  onClose,
  onCreated,
  initialDueDate = '',
}: {
  projectId: string
  onClose: () => void
  onCreated: (milestone: Milestone) => void
  initialDueDate?: string
}) {
  const phases = useAsyncData(() => phasesApi.list(projectId), [projectId])
  return (
    <MilestoneFormDialog
      title="New Milestone"
      initial={null}
      phaseOptions={phases.data ?? []}
      initialDueDate={initialDueDate}
      onClose={onClose}
      onSubmit={async (values) => {
        const created = await milestonesApi.create(projectId, values)
        onCreated(created)
      }}
    />
  )
}
