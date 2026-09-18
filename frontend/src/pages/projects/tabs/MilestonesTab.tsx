import { useState } from 'react'
import { useProjectWorkspace } from '../../../context/ProjectWorkspaceContext'
import { useAsyncData } from '../../../hooks/useAsyncData'
import { milestonesApi } from '../../../api/milestones'
import { phasesApi } from '../../../api/phases'
import { DataTable, type Column } from '../../../components/common/DataTable'
import { Modal } from '../../../components/common/Modal'
import { ConfirmDialog } from '../../../components/common/ConfirmDialog'
import { TextField, TextareaField, SelectField, FormActions } from '../../../components/forms/fields'
import { InlineError } from '../../../components/common/ErrorState'
import { toUserMessage } from '../../../api/errorMessage'
import { formatDate } from '../../../utils/format'
import { MilestoneStatusBadge } from '../../../components/common/Badge'
import { MILESTONE_STATUS_TRANSITIONS, type Milestone, type MilestoneStatus } from '../../../types/work'

export function MilestonesTab() {
  const { project, can } = useProjectWorkspace()
  const { data, loading, error, reload } = useAsyncData(() => milestonesApi.list(project.id), [project.id])
  const phases = useAsyncData(() => phasesApi.list(project.id), [project.id])
  const [createOpen, setCreateOpen] = useState(false)
  const [editMilestone, setEditMilestone] = useState<Milestone | null>(null)
  const [archiveMilestone, setArchiveMilestone] = useState<Milestone | null>(null)

  const phaseName = (id: string | null) => phases.data?.find((p) => p.id === id)?.name ?? '—'

  const columns: Column<Milestone>[] = [
    { key: 'name', header: 'Name', render: (m) => m.name },
    { key: 'phase', header: 'Phase', render: (m) => phaseName(m.phaseId) },
    { key: 'dueDate', header: 'Due', render: (m) => formatDate(m.dueDate) },
    { key: 'status', header: 'Status', render: (m) => <MilestoneStatusBadge status={m.status} /> },
    {
      key: 'actions',
      header: '',
      render: (m) =>
        can('EDIT_PROJECT') ? (
          <div className="page-actions">
            <button className="btn btn-sm" onClick={() => setEditMilestone(m)}>
              Edit
            </button>
            <button className="btn btn-sm btn-danger" onClick={() => setArchiveMilestone(m)}>
              Archive
            </button>
          </div>
        ) : null,
    },
  ]

  return (
    <div>
      <div className="toolbar">
        <div className="spacer" />
        {can('EDIT_PROJECT') && (
          <button className="btn btn-primary" onClick={() => setCreateOpen(true)}>
            New Milestone
          </button>
        )}
      </div>

      <DataTable
        items={data}
        loading={loading}
        error={error}
        onRetry={reload}
        columns={columns}
        getRowKey={(m) => m.id}
        emptyTitle="No milestones yet."
      />

      {createOpen && (
        <MilestoneFormDialog
          title="New Milestone"
          initial={null}
          phaseOptions={phases.data ?? []}
          onClose={() => setCreateOpen(false)}
          onSubmit={async (values) => {
            await milestonesApi.create(project.id, values)
            reload()
          }}
        />
      )}
      {editMilestone && (
        <MilestoneFormDialog
          title="Edit Milestone"
          initial={editMilestone}
          phaseOptions={phases.data ?? []}
          onClose={() => setEditMilestone(null)}
          onSubmit={async (values) => {
            await milestonesApi.update(editMilestone.id, { ...values, version: editMilestone.version })
            reload()
          }}
        />
      )}
      {archiveMilestone && (
        <ConfirmDialog
          title="Archive milestone"
          message={`Archive "${archiveMilestone.name}"?`}
          confirmLabel="Archive"
          onConfirm={async () => {
            await milestonesApi.archive(archiveMilestone.id)
            reload()
          }}
          onClose={() => setArchiveMilestone(null)}
        />
      )}
    </div>
  )
}

function MilestoneFormDialog({
  title,
  initial,
  phaseOptions,
  onClose,
  onSubmit,
}: {
  title: string
  initial: Milestone | null
  phaseOptions: { id: string; name: string }[]
  onClose: () => void
  onSubmit: (values: {
    name: string
    description: string | null
    dueDate: string | null
    phaseId: string | null
    status?: MilestoneStatus
  }) => Promise<void>
}) {
  const [name, setName] = useState(initial?.name ?? '')
  const [description, setDescription] = useState(initial?.description ?? '')
  const [dueDate, setDueDate] = useState(initial?.dueDate ?? '')
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
