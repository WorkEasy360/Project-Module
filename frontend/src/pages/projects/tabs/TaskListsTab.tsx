import { useState } from 'react'
import { useProjectWorkspace } from '../../../context/ProjectWorkspaceContext'
import { useAsyncData } from '../../../hooks/useAsyncData'
import { taskListsApi } from '../../../api/taskLists'
import { phasesApi } from '../../../api/phases'
import { DataTable, type Column } from '../../../components/common/DataTable'
import { Modal } from '../../../components/common/Modal'
import { ConfirmDialog } from '../../../components/common/ConfirmDialog'
import { TextField, TextareaField, SelectField, FormActions } from '../../../components/forms/fields'
import { InlineError } from '../../../components/common/ErrorState'
import { toUserMessage } from '../../../api/errorMessage'
import type { TaskList } from '../../../types/work'

export function TaskListsTab() {
  const { project, can } = useProjectWorkspace()
  const { data, loading, error, reload } = useAsyncData(() => taskListsApi.list(project.id), [project.id])
  const phases = useAsyncData(() => phasesApi.list(project.id), [project.id])
  const [createOpen, setCreateOpen] = useState(false)
  const [editItem, setEditItem] = useState<TaskList | null>(null)
  const [archiveItem, setArchiveItem] = useState<TaskList | null>(null)

  const phaseName = (id: string | null) => phases.data?.find((p) => p.id === id)?.name ?? '—'

  const columns: Column<TaskList>[] = [
    { key: 'name', header: 'Name', render: (t) => t.name },
    { key: 'phase', header: 'Phase', render: (t) => phaseName(t.phaseId) },
    { key: 'description', header: 'Description', render: (t) => t.description ?? '—' },
    {
      key: 'actions',
      header: '',
      render: (t) =>
        can('EDIT_PROJECT') ? (
          <div className="page-actions">
            <button className="btn btn-sm" onClick={() => setEditItem(t)}>
              Edit
            </button>
            <button className="btn btn-sm btn-danger" onClick={() => setArchiveItem(t)}>
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
            New Task List
          </button>
        )}
      </div>

      <DataTable
        items={data}
        loading={loading}
        error={error}
        onRetry={reload}
        columns={columns}
        getRowKey={(t) => t.id}
        emptyTitle="No task lists yet."
      />

      {createOpen && (
        <TaskListFormDialog
          title="New Task List"
          initial={null}
          phaseOptions={phases.data ?? []}
          onClose={() => setCreateOpen(false)}
          onSubmit={async (values) => {
            await taskListsApi.create(project.id, values)
            reload()
          }}
        />
      )}
      {editItem && (
        <TaskListFormDialog
          title="Edit Task List"
          initial={editItem}
          phaseOptions={phases.data ?? []}
          onClose={() => setEditItem(null)}
          onSubmit={async (values) => {
            await taskListsApi.update(editItem.id, { ...values, version: editItem.version })
            reload()
          }}
        />
      )}
      {archiveItem && (
        <ConfirmDialog
          title="Archive task list"
          message={`Archive "${archiveItem.name}"?`}
          confirmLabel="Archive"
          onConfirm={async () => {
            await taskListsApi.archive(archiveItem.id)
            reload()
          }}
          onClose={() => setArchiveItem(null)}
        />
      )}
    </div>
  )
}

function TaskListFormDialog({
  title,
  initial,
  phaseOptions,
  onClose,
  onSubmit,
}: {
  title: string
  initial: TaskList | null
  phaseOptions: { id: string; name: string }[]
  onClose: () => void
  onSubmit: (values: { name: string; description: string | null; phaseId: string | null }) => Promise<void>
}) {
  const [name, setName] = useState(initial?.name ?? '')
  const [description, setDescription] = useState(initial?.description ?? '')
  const [phaseId, setPhaseId] = useState(initial?.phaseId ?? '')
  const [error, setError] = useState<string | null>(null)
  const [submitting, setSubmitting] = useState(false)

  async function handleSubmit(event: React.FormEvent) {
    event.preventDefault()
    setSubmitting(true)
    setError(null)
    try {
      await onSubmit({ name, description: description || null, phaseId: phaseId || null })
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
        <TextField id="tl-name" label="Name" value={name} onChange={setName} required maxLength={200} />
        <TextareaField id="tl-description" label="Description" value={description} onChange={setDescription} />
        <SelectField
          id="tl-phase"
          label="Phase"
          value={phaseId}
          onChange={setPhaseId}
          allowEmpty
          emptyLabel="No phase"
          options={phaseOptions.map((p) => ({ value: p.id, label: p.name }))}
        />
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
