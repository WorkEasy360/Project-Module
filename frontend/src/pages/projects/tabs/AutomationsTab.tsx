import { useState } from 'react'
import { Link } from 'react-router-dom'
import { useProjectWorkspace } from '../../../context/ProjectWorkspaceContext'
import { useAsyncData } from '../../../hooks/useAsyncData'
import { automationsApi } from '../../../api/automations'
import { DataTable, type Column } from '../../../components/common/DataTable'
import { Modal } from '../../../components/common/Modal'
import { ConfirmDialog } from '../../../components/common/ConfirmDialog'
import { TextField, TextareaField, SelectField, FormActions } from '../../../components/forms/fields'
import { InlineError } from '../../../components/common/ErrorState'
import { toUserMessage } from '../../../api/errorMessage'
import { Badge } from '../../../components/common/Badge'
import { humanizeToken } from '../../../utils/format'
import {
    AUTOMATION_TRIGGER_EVENTS,
  type AutomationActionType,
  type AutomationTriggerEvent,
  type ProjectAutomation,
} from '../../../types/automation'

export function AutomationsTab() {
  const { project, can } = useProjectWorkspace()
  const { data, loading, error, reload } = useAsyncData(() => automationsApi.list(project.id), [project.id])
  const [createOpen, setCreateOpen] = useState(false)
  const [editItem, setEditItem] = useState<ProjectAutomation | null>(null)
  const [archiveItem, setArchiveItem] = useState<ProjectAutomation | null>(null)
  const [toggleError, setToggleError] = useState<string | null>(null)

  async function handleToggleEnabled(automation: ProjectAutomation) {
    setToggleError(null)
    try {
      await automationsApi.update(automation.id, { enabled: !automation.enabled, version: automation.version })
      reload()
    } catch (err) {
      setToggleError(toUserMessage(err))
    }
  }

  const columns: Column<ProjectAutomation>[] = [
    { key: 'name', header: 'Name', render: (a) => a.name },
    { key: 'trigger', header: 'Trigger', render: (a) => <Badge tone="info">{a.triggerEvent}</Badge> },
    { key: 'action', header: 'Action', render: (a) => humanizeToken(a.actionType) },
    {
      key: 'enabled',
      header: 'Status',
      render: (a) =>
        a.archived ? (
          <Badge tone="neutral">Archived</Badge>
        ) : (
          <Badge tone={a.enabled ? 'success' : 'neutral'}>{a.enabled ? 'Enabled' : 'Disabled'}</Badge>
        ),
    },
    {
      key: 'runs',
      header: '',
      render: (a) => <Link to={`/projects/${project.id}/automations/${a.id}/runs`}>Run history</Link>,
    },
    {
      key: 'actions',
      header: '',
      render: (a) =>
        can('EDIT_PROJECT') && !a.archived ? (
          <div className="page-actions">
            <button className="btn btn-sm" onClick={() => handleToggleEnabled(a)}>
              {a.enabled ? 'Disable' : 'Enable'}
            </button>
            <button className="btn btn-sm" onClick={() => setEditItem(a)}>
              Edit
            </button>
            <button className="btn btn-sm btn-danger" onClick={() => setArchiveItem(a)}>
              Archive
            </button>
          </div>
        ) : null,
    },
  ]

  return (
    <div>
      <p className="text-muted">
        Automations run synchronously when their trigger event occurs and always run in the same
        transaction as the change that caused them — the triggering change succeeds independently
        of whether the automation's action succeeds. Actions are limited to sending a notification
        or a chat message; see run history for outcomes.
      </p>
      {toggleError && <InlineError message={toggleError} />}
      <div className="toolbar">
        <div className="spacer" />
        {can('EDIT_PROJECT') && (
          <button className="btn btn-primary" onClick={() => setCreateOpen(true)}>
            New Automation
          </button>
        )}
      </div>

      <DataTable
        items={data}
        loading={loading}
        error={error}
        onRetry={reload}
        columns={columns}
        getRowKey={(a) => a.id}
        emptyTitle="No automations yet"
        emptyDescription="Automations react to project events like a task becoming overdue by sending a notification or chat message."
      />

      {createOpen && (
        <CreateAutomationDialog
          projectId={project.id}
          onClose={() => setCreateOpen(false)}
          onCreated={() => {
            setCreateOpen(false)
            reload()
          }}
        />
      )}
      {editItem && (
        <EditAutomationDialog
          automation={editItem}
          onClose={() => setEditItem(null)}
          onSaved={reload}
        />
      )}
      {archiveItem && (
        <ConfirmDialog
          title="Archive automation"
          message={`Archive "${archiveItem.name}"? It will stop running.`}
          confirmLabel="Archive"
          onConfirm={async () => {
            await automationsApi.archive(archiveItem.id)
            reload()
          }}
          onClose={() => setArchiveItem(null)}
        />
      )}
    </div>
  )
}

export function CreateAutomationDialog({
  projectId,
  onClose,
  onCreated,
}: {
  projectId: string
  onClose: () => void
  onCreated: () => void
}) {
  const [name, setName] = useState('')
  const [description, setDescription] = useState('')
  const [triggerEvent, setTriggerEvent] = useState<AutomationTriggerEvent>(AUTOMATION_TRIGGER_EVENTS[0])
  const [actionType, setActionType] = useState<AutomationActionType>('NOTIFY')
  const [actionRecipientId, setActionRecipientId] = useState('')
  const [actionChannelReference, setActionChannelReference] = useState('')
  const [actionMessage, setActionMessage] = useState('')
  const [showMore, setShowMore] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [submitting, setSubmitting] = useState(false)

  async function handleSubmit(event: React.FormEvent) {
    event.preventDefault()
    setSubmitting(true)
    setError(null)
    try {
      await automationsApi.create(projectId, {
        name,
        description: description || null,
        triggerEvent,
        actionType,
        actionRecipientId: actionType === 'NOTIFY' ? actionRecipientId || null : null,
        actionChannelReference: actionType === 'CHAT_MESSAGE' ? actionChannelReference || null : null,
        actionMessage,
      })
      onCreated()
    } catch (err) {
      setError(toUserMessage(err))
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <Modal title="New Automation" onClose={onClose}>
      <form onSubmit={handleSubmit} className="form-grid">
        {error && <InlineError message={error} />}
        <TextField
          id="auto-name"
          label="Name"
          value={name}
          onChange={setName}
          required
          maxLength={200}
          placeholder="e.g. Tell me when a task is overdue"
        />

        <div className="wizard-step">
          <div className="wizard-step-label">WHEN</div>
          <SelectField
            id="auto-trigger"
            label="This happens"
            value={triggerEvent}
            onChange={setTriggerEvent}
            required
            options={AUTOMATION_TRIGGER_EVENTS.map((e) => ({ value: e, label: humanizeToken(e) }))}
          />
        </div>

        <div className="wizard-step wizard-step-muted">
          <div className="wizard-step-label">IF</div>
          <p className="text-muted">
            In this project. Extra conditions (for example, only tasks assigned to me) aren&apos;t
            supported by the backend yet.
          </p>
        </div>

        <div className="wizard-step">
          <div className="wizard-step-label">THEN</div>
          <SelectField
            id="auto-action-type"
            label="Do this"
            value={actionType}
            onChange={setActionType}
            required
            options={[
              { value: "NOTIFY", label: "Send a notification to someone" },
              { value: "CHAT_MESSAGE", label: "Post a message to a chat channel" },
            ]}
          />
          {actionType === "NOTIFY" ? (
            <TextField
              id="auto-recipient"
              label="Notify (user ID)"
              value={actionRecipientId}
              onChange={setActionRecipientId}
              required
            />
          ) : (
            <TextField
              id="auto-channel"
              label="Chat channel"
              value={actionChannelReference}
              onChange={setActionChannelReference}
              required
            />
          )}
          <TextareaField id="auto-message" label="Message" value={actionMessage} onChange={setActionMessage} required />
        </div>

        {!showMore ? (
          <button type="button" className="btn btn-ghost btn-sm disclosure" onClick={() => setShowMore(true)}>
            More options
          </button>
        ) : (
          <TextareaField id="auto-description" label="Description" value={description} onChange={setDescription} />
        )}

        <FormActions>
          <button type="button" className="btn" onClick={onClose} disabled={submitting}>
            Cancel
          </button>
          <button type="submit" className="btn btn-primary" disabled={submitting || !name.trim() || !actionMessage.trim()}>
            {submitting ? "Creating…" : "Create Automation"}
          </button>
        </FormActions>
      </form>
    </Modal>
  )
}

function EditAutomationDialog({
  automation,
  onClose,
  onSaved,
}: {
  automation: ProjectAutomation
  onClose: () => void
  onSaved: () => void
}) {
  const [name, setName] = useState(automation.name)
  const [description, setDescription] = useState(automation.description ?? '')
  const [actionMessage, setActionMessage] = useState(automation.actionMessage)
  const [error, setError] = useState<string | null>(null)
  const [submitting, setSubmitting] = useState(false)

  async function handleSubmit(event: React.FormEvent) {
    event.preventDefault()
    setSubmitting(true)
    setError(null)
    try {
      await automationsApi.update(automation.id, {
        name,
        description: description || null,
        actionMessage,
        version: automation.version,
      })
      onSaved()
      onClose()
    } catch (err) {
      setError(toUserMessage(err))
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <Modal title="Edit Automation" onClose={onClose}>
      <form onSubmit={handleSubmit} className="form-grid">
        {error && <InlineError message={error} />}
        <p className="hint">
          Trigger event and action target are fixed after creation. Archive and create a new
          automation to change them.
        </p>
        <TextField id="auto-edit-name" label="Name" value={name} onChange={setName} required maxLength={200} />
        <TextareaField id="auto-edit-description" label="Description" value={description} onChange={setDescription} />
        <TextareaField id="auto-edit-message" label="Message" value={actionMessage} onChange={setActionMessage} required />
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
