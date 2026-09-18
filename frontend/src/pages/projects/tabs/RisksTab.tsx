import { useMemo, useState } from 'react'
import { useProjectWorkspace } from '../../../context/ProjectWorkspaceContext'
import { useAsyncData } from '../../../hooks/useAsyncData'
import { risksApi } from '../../../api/risks'
import { DataTable, type Column } from '../../../components/common/DataTable'
import { Modal } from '../../../components/common/Modal'
import { ConfirmDialog } from '../../../components/common/ConfirmDialog'
import { TextField, TextareaField, SelectField, FormActions } from '../../../components/forms/fields'
import { InlineError } from '../../../components/common/ErrorState'
import { toUserMessage } from '../../../api/errorMessage'
import { ApiError } from '../../../api/client'
import { RiskStatusBadge, PriorityBadge, ArchivedBadge } from '../../../components/common/Badge'
import { PROJECT_PRIORITIES, type ProjectPriority } from '../../../types/project'
import { RequestRecommendationDialog } from './AITab'
import { Icon } from '../../../components/common/Icon'
import { RISK_STATUSES, type Risk, type RiskListQuery, type RiskStatus } from '../../../types/work'

export function RisksTab() {
  const { project, can } = useProjectWorkspace()
  const [statusFilter, setStatusFilter] = useState('')
  const [priorityFilter, setPriorityFilter] = useState('')
  const [showArchived, setShowArchived] = useState(false)
  const [createOpen, setCreateOpen] = useState(false)
  const [editItem, setEditItem] = useState<Risk | null>(null)
  const [selected, setSelected] = useState<Set<string>>(new Set())
  const [bulkResult, setBulkResult] = useState<string | null>(null)
  const [bulkArchiveOpen, setBulkArchiveOpen] = useState(false)
  const [aiOpen, setAiOpen] = useState(false)

  const query: RiskListQuery = useMemo(
    () => ({
      status: (statusFilter || undefined) as RiskStatus | undefined,
      priority: (priorityFilter || undefined) as ProjectPriority | undefined,
      archived: showArchived,
    }),
    [statusFilter, priorityFilter, showArchived],
  )

  const { data, loading, error, reload } = useAsyncData(
    () => risksApi.list(project.id, query),
    [project.id, statusFilter, priorityFilter, showArchived],
  )

  function toggleSelected(id: string) {
    setSelected((prev) => {
      const next = new Set(prev)
      if (next.has(id)) { next.delete(id) } else { next.add(id) }
      return next
    })
  }

  async function handleBulkArchive() {
    const response = await risksApi.bulkArchive(project.id, { ids: Array.from(selected) })
    const failed = response.results.filter((r) => !r.succeeded)
    setBulkResult(
      failed.length === 0
        ? `Archived ${response.results.length} risk(s).`
        : `${response.results.length - failed.length}/${response.results.length} archived. Failed: ${failed.map((f) => f.error).join('; ')}`,
    )
    setSelected(new Set())
    reload()
  }

  const columns: Column<Risk>[] = [
    {
      key: 'select',
      header: '',
      render: (r) => (
        <input type="checkbox" checked={selected.has(r.id)} onChange={() => toggleSelected(r.id)} aria-label={`Select ${r.name}`} />
      ),
    },
    { key: 'name', header: 'Name', render: (r) => r.name },
    { key: 'status', header: 'Status', render: (r) => <RiskStatusBadge status={r.status} /> },
    { key: 'priority', header: 'Priority', render: (r) => <PriorityBadge priority={r.priority} /> },
    { key: 'archived', header: '', render: (r) => (r.archived ? <ArchivedBadge /> : null) },
    {
      key: 'actions',
      header: '',
      render: (r) =>
        can('EDIT_PROJECT') && !r.archived ? (
          <button className="btn btn-sm" onClick={() => setEditItem(r)}>
            Edit
          </button>
        ) : null,
    },
  ]

  return (
    <div>
      <div className="filter-bar">
        <SelectField
          id="risk-status-filter"
          label="Status"
          value={statusFilter}
          onChange={setStatusFilter}
          allowEmpty
          emptyLabel="All statuses"
          options={RISK_STATUSES.map((s) => ({ value: s, label: s }))}
        />
        <SelectField
          id="risk-priority-filter"
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
          <button className="btn btn-ghost" onClick={() => setAiOpen(true)}>
            <Icon name="ai" size={14} /> Suggest mitigation
          </button>
        )}
        {can('EDIT_PROJECT') && (
          <button className="btn btn-primary" onClick={() => setCreateOpen(true)}>
            New Risk
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
        getRowKey={(r) => r.id}
        emptyTitle="No risks recorded"
        emptyDescription="Add anything that could go wrong so the team can keep an eye on it."
      />

      {createOpen && (
        <RiskFormDialog
          title="New Risk"
          initial={null}
          onClose={() => setCreateOpen(false)}
          onSubmit={async (values) => {
            await risksApi.create(project.id, values as { name: string; description: string | null; priority: ProjectPriority })
            reload()
          }}
        />
      )}
      {editItem && (
        <RiskFormDialog
          title="Edit Risk"
          initial={editItem}
          onClose={() => setEditItem(null)}
          onSubmit={async (values) => {
            await risksApi.update(editItem.id, { ...values, version: editItem.version })
            reload()
          }}
        />
      )}

      {aiOpen && (
        <RequestRecommendationDialog
          projectId={project.id}
          initialType="RISK_ANALYSIS"
          onClose={() => setAiOpen(false)}
          onRequested={() => setAiOpen(false)}
        />
      )}

      {bulkArchiveOpen && (
        <ConfirmDialog
          title="Archive risks"
          message={`Archive ${selected.size} selected risk(s)?`}
          confirmLabel="Archive"
          onConfirm={handleBulkArchive}
          onClose={() => setBulkArchiveOpen(false)}
        />
      )}
    </div>
  )
}

function RiskFormDialog({
  title,
  initial,
  onClose,
  onSubmit,
}: {
  title: string
  initial: Risk | null
  onClose: () => void
  onSubmit: (values: { name: string; description: string | null; priority: ProjectPriority; status?: RiskStatus }) => Promise<void>
}) {
  const [name, setName] = useState(initial?.name ?? '')
  const [description, setDescription] = useState(initial?.description ?? '')
  const [priority, setPriority] = useState<ProjectPriority>(initial?.priority ?? 'MEDIUM')
  const [resolve, setResolve] = useState(false)
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
        priority,
        ...(resolve ? { status: 'RESOLVED' as RiskStatus } : {}),
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
        {conflict && <div className="banner banner-warning">This risk was modified elsewhere. Reload before retrying.</div>}
        <TextField id="risk-name" label="Name" value={name} onChange={setName} required maxLength={200} />
        <TextareaField id="risk-description" label="Description" value={description} onChange={setDescription} />
        <SelectField
          id="risk-priority"
          label="Priority"
          value={priority}
          onChange={setPriority}
          required
          options={PROJECT_PRIORITIES.map((p) => ({ value: p, label: p }))}
        />
        {initial && initial.status === 'OPEN' && (
          <label className="checkbox-row">
            <input type="checkbox" checked={resolve} onChange={(e) => setResolve(e.target.checked)} />
            Mark as resolved
          </label>
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
