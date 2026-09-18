import { useState } from 'react'
import { useProjectWorkspace } from '../../../context/ProjectWorkspaceContext'
import { useAsyncData } from '../../../hooks/useAsyncData'
import { customFieldsApi } from '../../../api/customFields'
import { DataTable, type Column } from '../../../components/common/DataTable'
import { Modal } from '../../../components/common/Modal'
import { ConfirmDialog } from '../../../components/common/ConfirmDialog'
import { TextField, SelectField, FormActions } from '../../../components/forms/fields'
import { InlineError } from '../../../components/common/ErrorState'
import { toUserMessage } from '../../../api/errorMessage'
import { CUSTOM_FIELD_VALUE_TYPES, type CustomField, type CustomFieldValueType } from '../../../types/work'

export function CustomFieldsTab() {
  const { project, can } = useProjectWorkspace()
  const { data, loading, error, reload } = useAsyncData(() => customFieldsApi.list(project.id), [project.id])
  const [createOpen, setCreateOpen] = useState(false)
  const [editItem, setEditItem] = useState<CustomField | null>(null)
  const [archiveItem, setArchiveItem] = useState<CustomField | null>(null)

  const columns: Column<CustomField>[] = [
    { key: 'name', header: 'Name', render: (f) => f.name },
    { key: 'type', header: 'Type', render: (f) => f.valueType },
    { key: 'value', header: 'Value', render: (f) => f.value },
    {
      key: 'actions',
      header: '',
      render: (f) =>
        can('EDIT_PROJECT') ? (
          <div className="page-actions">
            <button className="btn btn-sm" onClick={() => setEditItem(f)}>
              Edit
            </button>
            <button className="btn btn-sm btn-danger" onClick={() => setArchiveItem(f)}>
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
            New Custom Field
          </button>
        )}
      </div>

      <DataTable
        items={data}
        loading={loading}
        error={error}
        onRetry={reload}
        columns={columns}
        getRowKey={(f) => f.id}
        emptyTitle="No custom fields defined."
      />

      {createOpen && (
        <CreateCustomFieldDialog
          onClose={() => setCreateOpen(false)}
          onSubmit={async (values) => {
            await customFieldsApi.create(project.id, values)
            reload()
          }}
        />
      )}
      {editItem && (
        <EditCustomFieldDialog
          initial={editItem}
          onClose={() => setEditItem(null)}
          onSubmit={async (values) => {
            await customFieldsApi.update(editItem.id, { ...values, version: editItem.version })
            reload()
          }}
        />
      )}
      {archiveItem && (
        <ConfirmDialog
          title="Archive custom field"
          message={`Archive "${archiveItem.name}"?`}
          confirmLabel="Archive"
          onConfirm={async () => {
            await customFieldsApi.archive(archiveItem.id)
            reload()
          }}
          onClose={() => setArchiveItem(null)}
        />
      )}
    </div>
  )
}

function valueInputType(valueType: CustomFieldValueType): 'text' | 'number' | 'date' {
  if (valueType === 'NUMBER') return 'number'
  if (valueType === 'DATE') return 'date'
  return 'text'
}

function CreateCustomFieldDialog({
  onClose,
  onSubmit,
}: {
  onClose: () => void
  onSubmit: (values: { name: string; valueType: CustomFieldValueType; value: string }) => Promise<void>
}) {
  const [name, setName] = useState('')
  const [valueType, setValueType] = useState<CustomFieldValueType>('TEXT')
  const [value, setValue] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [submitting, setSubmitting] = useState(false)

  async function handleSubmit(event: React.FormEvent) {
    event.preventDefault()
    setSubmitting(true)
    setError(null)
    try {
      await onSubmit({ name, valueType, value })
      onClose()
    } catch (err) {
      setError(toUserMessage(err))
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <Modal title="New Custom Field" onClose={onClose}>
      <form onSubmit={handleSubmit} className="form-grid">
        {error && <InlineError message={error} />}
        <TextField id="cf-name" label="Name" value={name} onChange={setName} required maxLength={200} />
        <SelectField
          id="cf-type"
          label="Value type"
          value={valueType}
          onChange={setValueType}
          required
          options={CUSTOM_FIELD_VALUE_TYPES.map((t) => ({ value: t, label: t }))}
        />
        {valueType === 'BOOLEAN' ? (
          <SelectField
            id="cf-value"
            label="Value"
            value={value as 'true' | 'false' | ''}
            onChange={setValue}
            required
            options={[
              { value: 'true', label: 'True' },
              { value: 'false', label: 'False' },
            ]}
          />
        ) : (
          <TextField id="cf-value" label="Value" type={valueInputType(valueType)} value={value} onChange={setValue} required />
        )}
        <FormActions>
          <button type="button" className="btn" onClick={onClose} disabled={submitting}>
            Cancel
          </button>
          <button type="submit" className="btn btn-primary" disabled={submitting || !name.trim() || !value}>
            {submitting ? 'Creating…' : 'Create'}
          </button>
        </FormActions>
      </form>
    </Modal>
  )
}

function EditCustomFieldDialog({
  initial,
  onClose,
  onSubmit,
}: {
  initial: CustomField
  onClose: () => void
  onSubmit: (values: { name: string; value: string }) => Promise<void>
}) {
  const [name, setName] = useState(initial.name)
  const [value, setValue] = useState(initial.value)
  const [error, setError] = useState<string | null>(null)
  const [submitting, setSubmitting] = useState(false)

  async function handleSubmit(event: React.FormEvent) {
    event.preventDefault()
    setSubmitting(true)
    setError(null)
    try {
      await onSubmit({ name, value })
      onClose()
    } catch (err) {
      setError(toUserMessage(err))
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <Modal title="Edit Custom Field" onClose={onClose}>
      <form onSubmit={handleSubmit} className="form-grid">
        {error && <InlineError message={error} />}
        <TextField id="cf-edit-name" label="Name" value={name} onChange={setName} required maxLength={200} />
        {initial.valueType === 'BOOLEAN' ? (
          <SelectField
            id="cf-edit-value"
            label="Value"
            value={value as 'true' | 'false'}
            onChange={setValue}
            required
            options={[
              { value: 'true', label: 'True' },
              { value: 'false', label: 'False' },
            ]}
          />
        ) : (
          <TextField
            id="cf-edit-value"
            label="Value"
            type={valueInputType(initial.valueType)}
            value={value}
            onChange={setValue}
            required
          />
        )}
        <FormActions>
          <button type="button" className="btn" onClick={onClose} disabled={submitting}>
            Cancel
          </button>
          <button type="submit" className="btn btn-primary" disabled={submitting}>
            {submitting ? 'Saving…' : 'Save'}
          </button>
        </FormActions>
      </form>
    </Modal>
  )
}
