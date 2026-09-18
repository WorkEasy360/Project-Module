import { useMemo, useState } from 'react'
import { useProjectWorkspace } from '../../../context/ProjectWorkspaceContext'
import { useAsyncData } from '../../../hooks/useAsyncData'
import { decisionsApi } from '../../../api/decisions'
import { DataTable, type Column } from '../../../components/common/DataTable'
import { Modal } from '../../../components/common/Modal'
import { ConfirmDialog } from '../../../components/common/ConfirmDialog'
import { TextField, TextareaField, FormActions } from '../../../components/forms/fields'
import { InlineError } from '../../../components/common/ErrorState'
import { toUserMessage } from '../../../api/errorMessage'
import { ArchivedBadge } from '../../../components/common/Badge'
import type { Decision, DecisionListQuery } from '../../../types/work'

export function DecisionsTab() {
  const { project, can } = useProjectWorkspace()
  const [decidedByFilter, setDecidedByFilter] = useState('')
  const [showArchived, setShowArchived] = useState(false)
  const [createOpen, setCreateOpen] = useState(false)
  const [editItem, setEditItem] = useState<Decision | null>(null)
  const [selected, setSelected] = useState<Set<string>>(new Set())
  const [bulkResult, setBulkResult] = useState<string | null>(null)
  const [bulkArchiveOpen, setBulkArchiveOpen] = useState(false)

  const query: DecisionListQuery = useMemo(
    () => ({ decidedBy: decidedByFilter || undefined, archived: showArchived }),
    [decidedByFilter, showArchived],
  )

  const { data, loading, error, reload } = useAsyncData(
    () => decisionsApi.list(project.id, query),
    [project.id, decidedByFilter, showArchived],
  )

  function toggleSelected(id: string) {
    setSelected((prev) => {
      const next = new Set(prev)
      if (next.has(id)) { next.delete(id) } else { next.add(id) }
      return next
    })
  }

  async function handleBulkArchive() {
    const response = await decisionsApi.bulkArchive(project.id, { ids: Array.from(selected) })
    const failed = response.results.filter((r) => !r.succeeded)
    setBulkResult(
      failed.length === 0
        ? `Archived ${response.results.length} decision(s).`
        : `${response.results.length - failed.length}/${response.results.length} archived. Failed: ${failed.map((f) => f.error).join('; ')}`,
    )
    setSelected(new Set())
    reload()
  }

  const columns: Column<Decision>[] = [
    {
      key: 'select',
      header: '',
      render: (d) => (
        <input type="checkbox" checked={selected.has(d.id)} onChange={() => toggleSelected(d.id)} aria-label={`Select ${d.name}`} />
      ),
    },
    { key: 'name', header: 'Name', render: (d) => d.name },
    { key: 'decidedBy', header: 'Decided by', render: (d) => d.decidedBy ?? '—' },
    { key: 'archived', header: '', render: (d) => (d.archived ? <ArchivedBadge /> : null) },
    {
      key: 'actions',
      header: '',
      render: (d) =>
        can('EDIT_PROJECT') && !d.archived ? (
          <button className="btn btn-sm" onClick={() => setEditItem(d)}>
            Edit
          </button>
        ) : null,
    },
  ]

  return (
    <div>
      <div className="filter-bar">
        <TextField
          id="decision-decidedby-filter"
          label="Decided by (User ID)"
          value={decidedByFilter}
          onChange={setDecidedByFilter}
        />
        <label className="checkbox-row">
          <input type="checkbox" checked={showArchived} onChange={(e) => setShowArchived(e.target.checked)} />
          Show archived
        </label>
        <div className="spacer" />
        {can('EDIT_PROJECT') && selected.size > 0 && (
          <button className="btn btn-sm btn-danger" onClick={() => setBulkArchiveOpen(true)}>
            Archive {selected.size} selected
          </button>
        )}
        {can('EDIT_PROJECT') && (
          <button className="btn btn-primary" onClick={() => setCreateOpen(true)}>
            New Decision
          </button>
        )}
      </div>

      {bulkResult && <div className="banner banner-info">{bulkResult}</div>}

      <DataTable
        items={data}
        loading={loading}
        error={error}
        onRetry={reload}
        columns={columns}
        getRowKey={(d) => d.id}
        emptyTitle="No decisions recorded"
        emptyDescription="Keep a record of what was decided and by whom."
      />

      {createOpen && (
        <DecisionFormDialog
          title="New Decision"
          initial={null}
          onClose={() => setCreateOpen(false)}
          onSubmit={async (values) => {
            await decisionsApi.create(project.id, values)
            reload()
          }}
        />
      )}
      {editItem && (
        <DecisionFormDialog
          title="Edit Decision"
          initial={editItem}
          onClose={() => setEditItem(null)}
          onSubmit={async (values) => {
            await decisionsApi.update(editItem.id, { ...values, version: editItem.version })
            reload()
          }}
        />
      )}

      {bulkArchiveOpen && (
        <ConfirmDialog
          title="Archive decisions"
          message={`Archive ${selected.size} selected decision(s)?`}
          confirmLabel="Archive"
          onConfirm={handleBulkArchive}
          onClose={() => setBulkArchiveOpen(false)}
        />
      )}
    </div>
  )
}

function DecisionFormDialog({
  title,
  initial,
  onClose,
  onSubmit,
}: {
  title: string
  initial: Decision | null
  onClose: () => void
  onSubmit: (values: { name: string; description: string | null; decidedBy: string | null }) => Promise<void>
}) {
  const [name, setName] = useState(initial?.name ?? '')
  const [description, setDescription] = useState(initial?.description ?? '')
  const [decidedBy, setDecidedBy] = useState(initial?.decidedBy ?? '')
  const [error, setError] = useState<string | null>(null)
  const [submitting, setSubmitting] = useState(false)

  async function handleSubmit(event: React.FormEvent) {
    event.preventDefault()
    setSubmitting(true)
    setError(null)
    try {
      await onSubmit({ name, description: description || null, decidedBy: decidedBy || null })
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
        <TextField id="decision-name" label="Name" value={name} onChange={setName} required maxLength={200} />
        <TextareaField id="decision-description" label="Description" value={description} onChange={setDescription} />
        <TextField id="decision-decidedby" label="Decided by (User ID)" value={decidedBy} onChange={setDecidedBy} />
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
