import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { useAsyncData } from '../../hooks/useAsyncData'
import { templatesApi } from '../../api/templates'
import { DataTable, type Column } from '../../components/common/DataTable'
import { Pagination } from '../../components/common/Pagination'
import { Modal } from '../../components/common/Modal'
import { ConfirmDialog } from '../../components/common/ConfirmDialog'
import { TextField, TextareaField, SelectField, FormActions } from '../../components/forms/fields'
import { InlineError } from '../../components/common/ErrorState'
import { toUserMessage } from '../../api/errorMessage'
import { Badge, PriorityBadge } from '../../components/common/Badge'
import { PROJECT_PRIORITIES, type ProjectPriority } from '../../types/project'
import type { ProjectTemplate } from '../../types/template'

export function TemplatesPage() {
  const [page, setPage] = useState(0)
  const [createOpen, setCreateOpen] = useState(false)
  const [editItem, setEditItem] = useState<ProjectTemplate | null>(null)
  const [archiveItem, setArchiveItem] = useState<ProjectTemplate | null>(null)
  const [applyItem, setApplyItem] = useState<ProjectTemplate | null>(null)

  const { data, loading, error, reload } = useAsyncData(() => templatesApi.list(page, 20), [page])

  const columns: Column<ProjectTemplate>[] = [
    { key: 'name', header: 'Name', render: (t) => t.name },
    {
      key: 'priority',
      header: 'Default priority',
      render: (t) =>
        t.defaultPriority ? (
          <PriorityBadge priority={t.defaultPriority} />
        ) : (
          <Badge tone="warning">Not set — can&apos;t apply</Badge>
        ),
    },
    { key: 'description', header: 'Description', render: (t) => t.description ?? '—' },
    {
      key: 'actions',
      header: '',
      render: (t) => (
        <div className="page-actions">
          <button className="btn btn-sm btn-primary" onClick={() => setApplyItem(t)}>
            Apply
          </button>
          <button className="btn btn-sm" onClick={() => setEditItem(t)}>
            Edit
          </button>
          <button className="btn btn-sm btn-danger" onClick={() => setArchiveItem(t)}>
            Archive
          </button>
        </div>
      ),
    },
  ]

  return (
    <div>
      <div className="page-header">
        <div className="page-header-title">
          <h1>Project Templates</h1>
          <p>Reusable project starting points. Applying a template creates a new, real project.</p>
        </div>
        <div className="page-actions">
          <button className="btn btn-primary" onClick={() => setCreateOpen(true)}>
            New Template
          </button>
        </div>
      </div>

      <DataTable
        items={data?.content ?? null}
        loading={loading}
        error={error}
        onRetry={reload}
        columns={columns}
        getRowKey={(t) => t.id}
        emptyTitle="No templates yet."
      />

      {data && (
        <Pagination page={data.page} totalPages={data.totalPages} totalElements={data.totalElements} onChange={setPage} />
      )}

      {createOpen && (
        <TemplateFormDialog
          title="New Template"
          initial={null}
          onClose={() => setCreateOpen(false)}
          onSubmit={async (values) => {
            await templatesApi.create(values)
            reload()
          }}
        />
      )}
      {editItem && (
        <TemplateFormDialog
          title="Edit Template"
          initial={editItem}
          onClose={() => setEditItem(null)}
          onSubmit={async (values) => {
            await templatesApi.update(editItem.id, { ...values, version: editItem.version })
            reload()
          }}
        />
      )}
      {archiveItem && (
        <ConfirmDialog
          title="Archive template"
          message={`Archive "${archiveItem.name}"? Existing projects created from it are not affected.`}
          confirmLabel="Archive"
          onConfirm={async () => {
            await templatesApi.archive(archiveItem.id)
            reload()
          }}
          onClose={() => setArchiveItem(null)}
        />
      )}
      {applyItem && (
        <ApplyTemplateDialog
          template={applyItem}
          onClose={() => setApplyItem(null)}
          onEditInstead={() => {
            setEditItem(applyItem)
            setApplyItem(null)
          }}
        />
      )}
    </div>
  )
}

function TemplateFormDialog({
  title,
  initial,
  onClose,
  onSubmit,
}: {
  title: string
  initial: ProjectTemplate | null
  onClose: () => void
  onSubmit: (values: { name: string; description: string | null; defaultPriority: ProjectPriority | null }) => Promise<void>
}) {
  const [name, setName] = useState(initial?.name ?? '')
  const [description, setDescription] = useState(initial?.description ?? '')
  // Backend allows a null defaultPriority (spec §12) but such a template can never be applied
  // (spec [APPROVED #6]) — so the UI refuses to create that dead-end state, and defaults sensibly.
  const [defaultPriority, setDefaultPriority] = useState<ProjectPriority | ''>(initial?.defaultPriority ?? 'MEDIUM')
  const [priorityError, setPriorityError] = useState<string | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [submitting, setSubmitting] = useState(false)

  async function handleSubmit(event: React.FormEvent) {
    event.preventDefault()
    if (!defaultPriority) {
      setPriorityError('Choose a default priority — a template without one cannot be applied.')
      return
    }
    setSubmitting(true)
    setError(null)
    try {
      await onSubmit({ name, description: description || null, defaultPriority })
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
        <TextField id="tpl-name" label="Name" value={name} onChange={setName} required maxLength={200} />
        <TextareaField id="tpl-description" label="Description" value={description} onChange={setDescription} />
        <SelectField
          id="tpl-priority"
          label="Default priority"
          value={defaultPriority}
          onChange={(v) => {
            setDefaultPriority(v)
            setPriorityError(null)
          }}
          required
          hint="Every project created from this template starts with this priority. Required — a template without one cannot be applied."
          options={PROJECT_PRIORITIES.map((p) => ({ value: p, label: p }))}
        />
        {priorityError && (
          <div className="field-error" role="alert">
            {priorityError}
          </div>
        )}
        <FormActions>
          <button type="button" className="btn" onClick={onClose} disabled={submitting}>
            Cancel
          </button>
          <button type="submit" className="btn btn-primary" disabled={submitting || !name.trim() || !defaultPriority}>
            {submitting ? 'Saving…' : 'Save'}
          </button>
        </FormActions>
      </form>
    </Modal>
  )
}

function ApplyTemplateDialog({
  template,
  onClose,
  onEditInstead,
}: {
  template: ProjectTemplate
  onClose: () => void
  onEditInstead: () => void
}) {
  const navigate = useNavigate()
  const [name, setName] = useState(`${template.name} copy`)
  const [description, setDescription] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [submitting, setSubmitting] = useState(false)
  const [confirmed, setConfirmed] = useState(false)

  async function handleSubmit(event: React.FormEvent) {
    event.preventDefault()
    if (!confirmed) return
    setSubmitting(true)
    setError(null)
    try {
      const project = await templatesApi.apply(template.id, { name, description: description || null })
      navigate(`/projects/${project.id}`)
    } catch (err) {
      setError(toUserMessage(err))
    } finally {
      setSubmitting(false)
    }
  }

  if (!template.defaultPriority) {
    return (
      <Modal title={`Apply "${template.name}"`} onClose={onClose}>
        <div className="banner banner-warning">
          This template has no default priority, so it can&apos;t be applied yet. Every project
          needs a priority and a template supplies it — set one and you&apos;ll be able to apply it.
        </div>
        <div className="form-actions">
          <button type="button" className="btn" onClick={onClose}>
            Cancel
          </button>
          <button type="button" className="btn btn-primary" onClick={onEditInstead}>
            Set a default priority
          </button>
        </div>
      </Modal>
    )
  }

  return (
    <Modal title={`Apply "${template.name}"`} onClose={onClose}>
      <form onSubmit={handleSubmit} className="form-grid">
        {error && <InlineError message={error} />}
        <div className="banner banner-info">
          This creates a new, real project from the template — it is not a preview.
        </div>
        <TextField id="apply-name" label="New project name" value={name} onChange={setName} required maxLength={200} />
        <TextareaField
          id="apply-description"
          label="Description (optional override)"
          value={description}
          onChange={setDescription}
        />
        <label className="checkbox-row">
          <input type="checkbox" checked={confirmed} onChange={(e) => setConfirmed(e.target.checked)} />
          I understand this will create a new project
        </label>
        <FormActions>
          <button type="button" className="btn" onClick={onClose} disabled={submitting}>
            Cancel
          </button>
          <button type="submit" className="btn btn-primary" disabled={submitting || !name.trim() || !confirmed}>
            {submitting ? 'Creating…' : 'Create Project from Template'}
          </button>
        </FormActions>
      </form>
    </Modal>
  )
}
