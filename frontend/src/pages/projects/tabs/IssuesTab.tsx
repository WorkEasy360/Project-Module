import { useMemo, useState } from 'react'
import { useProjectWorkspace } from '../../../context/ProjectWorkspaceContext'
import { useAsyncData } from '../../../hooks/useAsyncData'
import { issuesApi } from '../../../api/issues'
import { DataTable, type Column } from '../../../components/common/DataTable'
import { Modal } from '../../../components/common/Modal'
import { ConfirmDialog } from '../../../components/common/ConfirmDialog'
import { TextField, TextareaField, SelectField, FormActions } from '../../../components/forms/fields'
import { InlineError } from '../../../components/common/ErrorState'
import { toUserMessage } from '../../../api/errorMessage'
import { PriorityBadge, ArchivedBadge } from '../../../components/common/Badge'
import { PROJECT_PRIORITIES, type ProjectPriority } from '../../../types/project'
import type { Issue, IssueListQuery } from '../../../types/work'

export function IssuesTab() {
  const { project, can } = useProjectWorkspace()
  const [priorityFilter, setPriorityFilter] = useState('')
  const [showArchived, setShowArchived] = useState(false)
  const [createOpen, setCreateOpen] = useState(false)
  const [editItem, setEditItem] = useState<Issue | null>(null)
  const [selected, setSelected] = useState<Set<string>>(new Set())
  const [bulkResult, setBulkResult] = useState<string | null>(null)
  const [bulkArchiveOpen, setBulkArchiveOpen] = useState(false)

  const query: IssueListQuery = useMemo(
    () => ({ priority: (priorityFilter || undefined) as ProjectPriority | undefined, archived: showArchived }),
    [priorityFilter, showArchived],
  )

  const { data, loading, error, reload } = useAsyncData(
    () => issuesApi.list(project.id, query),
    [project.id, priorityFilter, showArchived],
  )

  function toggleSelected(id: string) {
    setSelected((prev) => {
      const next = new Set(prev)
      if (next.has(id)) { next.delete(id) } else { next.add(id) }
      return next
    })
  }

  async function handleBulkArchive() {
    const response = await issuesApi.bulkArchive(project.id, { ids: Array.from(selected) })
    const failed = response.results.filter((r) => !r.succeeded)
    setBulkResult(
      failed.length === 0
        ? `Archived ${response.results.length} issue(s).`
        : `${response.results.length - failed.length}/${response.results.length} archived. Failed: ${failed.map((f) => f.error).join('; ')}`,
    )
    setSelected(new Set())
    reload()
  }

  const columns: Column<Issue>[] = [
    {
      key: 'select',
      header: '',
      render: (i) => (
        <input type="checkbox" checked={selected.has(i.id)} onChange={() => toggleSelected(i.id)} aria-label={`Select ${i.name}`} />
      ),
    },
    { key: 'name', header: 'Name', render: (i) => i.name },
    { key: 'priority', header: 'Priority', render: (i) => <PriorityBadge priority={i.priority} /> },
    { key: 'archived', header: '', render: (i) => (i.archived ? <ArchivedBadge /> : null) },
    {
      key: 'actions',
      header: '',
      render: (i) =>
        can('EDIT_PROJECT') && !i.archived ? (
          <button className="btn btn-sm" onClick={() => setEditItem(i)}>
            Edit
          </button>
        ) : null,
    },
  ]

  return (
    <div>
      <div className="filter-bar">
        <SelectField
          id="issue-priority-filter"
          label="Priority"
          value={priorityFilter}
          onChange={setPriorityFilter}
          allowEmpty
          emptyLabel="All priorities"
          options={PROJECT_PRIORITIES.map((p) => ({ value: p, label: p }))}
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
            New Issue
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
        getRowKey={(i) => i.id}
        emptyTitle="No issues recorded"
        emptyDescription="Log problems that have already happened so they get resolved."
      />

      {createOpen && (
        <IssueFormDialog
          title="New Issue"
          initial={null}
          onClose={() => setCreateOpen(false)}
          onSubmit={async (values) => {
            await issuesApi.create(project.id, values)
            reload()
          }}
        />
      )}
      {editItem && (
        <IssueFormDialog
          title="Edit Issue"
          initial={editItem}
          onClose={() => setEditItem(null)}
          onSubmit={async (values) => {
            await issuesApi.update(editItem.id, { ...values, version: editItem.version })
            reload()
          }}
        />
      )}

      {bulkArchiveOpen && (
        <ConfirmDialog
          title="Archive issues"
          message={`Archive ${selected.size} selected issue(s)?`}
          confirmLabel="Archive"
          onConfirm={handleBulkArchive}
          onClose={() => setBulkArchiveOpen(false)}
        />
      )}
    </div>
  )
}

function IssueFormDialog({
  title,
  initial,
  onClose,
  onSubmit,
}: {
  title: string
  initial: Issue | null
  onClose: () => void
  onSubmit: (values: { name: string; description: string | null; priority: ProjectPriority }) => Promise<void>
}) {
  const [name, setName] = useState(initial?.name ?? '')
  const [description, setDescription] = useState(initial?.description ?? '')
  const [priority, setPriority] = useState<ProjectPriority>(initial?.priority ?? 'MEDIUM')
  const [error, setError] = useState<string | null>(null)
  const [submitting, setSubmitting] = useState(false)

  async function handleSubmit(event: React.FormEvent) {
    event.preventDefault()
    setSubmitting(true)
    setError(null)
    try {
      await onSubmit({ name, description: description || null, priority })
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
        <TextField id="issue-name" label="Name" value={name} onChange={setName} required maxLength={200} />
        <TextareaField id="issue-description" label="Description" value={description} onChange={setDescription} />
        <SelectField
          id="issue-priority"
          label="Priority"
          value={priority}
          onChange={setPriority}
          required
          options={PROJECT_PRIORITIES.map((p) => ({ value: p, label: p }))}
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
