import { useState } from 'react'
import { useProjectWorkspace } from '../../../context/ProjectWorkspaceContext'
import { useAsyncData } from '../../../hooks/useAsyncData'
import { phasesApi } from '../../../api/phases'
import { DataTable, type Column } from '../../../components/common/DataTable'
import { Modal } from '../../../components/common/Modal'
import { ConfirmDialog } from '../../../components/common/ConfirmDialog'
import { TextField, TextareaField, FormActions } from '../../../components/forms/fields'
import { InlineError } from '../../../components/common/ErrorState'
import { toUserMessage } from '../../../api/errorMessage'
import { ApiError } from '../../../api/client'
import { formatDate } from '../../../utils/format'
import type { Phase } from '../../../types/work'

export function PhasesTab() {
  const { project, can } = useProjectWorkspace()
  const { data, loading, error, reload } = useAsyncData(() => phasesApi.list(project.id), [project.id])
  const [createOpen, setCreateOpen] = useState(false)
  const [editPhase, setEditPhase] = useState<Phase | null>(null)
  const [archivePhase, setArchivePhase] = useState<Phase | null>(null)

  const columns: Column<Phase>[] = [
    { key: 'name', header: 'Name', render: (p) => p.name },
    { key: 'dates', header: 'Dates', render: (p) => `${formatDate(p.startDate)} – ${formatDate(p.endDate)}` },
    { key: 'description', header: 'Description', render: (p) => p.description ?? '—' },
    {
      key: 'actions',
      header: '',
      render: (p) =>
        can('EDIT_PROJECT') ? (
          <div className="page-actions">
            <button className="btn btn-sm" onClick={() => setEditPhase(p)}>
              Edit
            </button>
            <button className="btn btn-sm btn-danger" onClick={() => setArchivePhase(p)}>
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
            New Phase
          </button>
        )}
      </div>

      <DataTable
        items={data}
        loading={loading}
        error={error}
        onRetry={reload}
        columns={columns}
        getRowKey={(p) => p.id}
        emptyTitle="No phases yet."
        emptyDescription="Phases group milestones and task lists into stages of work."
      />

      {createOpen && (
        <PhaseFormDialog
          title="New Phase"
          initial={null}
          onClose={() => setCreateOpen(false)}
          onSubmit={async (values) => {
            await phasesApi.create(project.id, values)
            reload()
          }}
        />
      )}
      {editPhase && (
        <PhaseFormDialog
          title="Edit Phase"
          initial={editPhase}
          onClose={() => setEditPhase(null)}
          onSubmit={async (values) => {
            await phasesApi.update(editPhase.id, { ...values, version: editPhase.version })
            reload()
          }}
        />
      )}
      {archivePhase && (
        <ConfirmDialog
          title="Archive phase"
          message={`Archive "${archivePhase.name}"?`}
          confirmLabel="Archive"
          onConfirm={async () => {
            await phasesApi.archive(archivePhase.id)
            reload()
          }}
          onClose={() => setArchivePhase(null)}
        />
      )}
    </div>
  )
}

function PhaseFormDialog({
  title,
  initial,
  onClose,
  onSubmit,
}: {
  title: string
  initial: Phase | null
  onClose: () => void
  onSubmit: (values: { name: string; description: string | null; startDate: string | null; endDate: string | null }) => Promise<void>
}) {
  const [name, setName] = useState(initial?.name ?? '')
  const [description, setDescription] = useState(initial?.description ?? '')
  const [startDate, setStartDate] = useState(initial?.startDate ?? '')
  const [endDate, setEndDate] = useState(initial?.endDate ?? '')
  const [error, setError] = useState<string | null>(null)
  const [conflict, setConflict] = useState(false)
  const [submitting, setSubmitting] = useState(false)

  async function handleSubmit(event: React.FormEvent) {
    event.preventDefault()
    setSubmitting(true)
    setError(null)
    setConflict(false)
    try {
      await onSubmit({
        name,
        description: description || null,
        startDate: startDate || null,
        endDate: endDate || null,
      })
      onClose()
    } catch (err) {
      if (err instanceof ApiError && err.isConflict) setConflict(true)
      setError(toUserMessage(err))
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <Modal title={title} onClose={onClose}>
      <form onSubmit={handleSubmit} className="form-grid">
        {error && <InlineError message={error} />}
        {conflict && (
          <div className="banner banner-warning">
            This phase was modified elsewhere. Close this dialog and reload before trying again.
          </div>
        )}
        <TextField id="phase-name" label="Name" value={name} onChange={setName} required maxLength={200} />
        <TextareaField id="phase-description" label="Description" value={description} onChange={setDescription} />
        <div className="form-row">
          <TextField id="phase-start" label="Start date" type="date" value={startDate} onChange={setStartDate} />
          <TextField id="phase-end" label="End date" type="date" value={endDate} onChange={setEndDate} />
        </div>
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
