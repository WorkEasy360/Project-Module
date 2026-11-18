import { useState } from 'react'
import { automationsApi } from '../../../api/automations'
import { toUserMessage } from '../../../api/errorMessage'
import { InlineError } from '../../../components/common/ErrorState'
import { Modal } from '../../../components/common/Modal'
import { CheckboxField, FormActions, SelectField, TextareaField, TextField } from '../../../components/forms/fields'
import { ProjectPersonField } from '../../../components/forms/ProjectPersonField'
import {
  AUTOMATION_TRIGGER_EVENTS,
  TRIGGER_LABEL,
  type AutomationTriggerEvent,
  type ProjectAutomation,
} from '../../../types/automation'
import type { ProjectSummary } from '../../../types/project'

/**
 * V3 guided rule form: name, the project it watches, WHEN (trigger), IF (the project; extra
 * conditions are not supported yet), THEN notify a project member with a message, and enabled.
 */
export function CreateRuleDialog({
  projects,
  initialProjectId,
  onClose,
  onCreated,
}: {
  projects: ProjectSummary[]
  initialProjectId?: string
  onClose: () => void
  onCreated: () => void
}) {
  const [projectId, setProjectId] = useState(initialProjectId ?? projects[0]?.id ?? '')
  const [name, setName] = useState('')
  const [triggerEvent, setTriggerEvent] = useState<AutomationTriggerEvent>('task.assigned')
  const [recipientId, setRecipientId] = useState('')
  const [message, setMessage] = useState('')
  const [enabled, setEnabled] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [submitting, setSubmitting] = useState(false)

  async function handleSubmit(event: React.FormEvent) {
    event.preventDefault()
    setSubmitting(true)
    setError(null)
    try {
      const created = await automationsApi.create(projectId, {
        name: name.trim(),
        triggerEvent,
        actionType: 'NOTIFY',
        actionRecipientId: recipientId,
        actionMessage: message.trim(),
      })
      if (!enabled) await automationsApi.update(created.id, { enabled: false, version: created.version })
      onCreated()
      onClose()
    } catch (err) {
      setError(toUserMessage(err))
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <Modal title="New Rule" onClose={onClose}>
      <form onSubmit={handleSubmit} className="form-grid">
        {error && <InlineError message={error} />}
        <TextField id="rule-name" label="Rule name" value={name} onChange={setName} required maxLength={200} placeholder="e.g. Tell the owner when a task is done" />
        <SelectField
          id="rule-project"
          label="Project"
          value={projectId}
          onChange={(id) => {
            setProjectId(id)
            setRecipientId('')
          }}
          required
          options={projects.map((p) => ({ value: p.id, label: p.name }))}
        />

        <div className="wizard-step">
          <div className="wizard-step-label">WHEN</div>
          <SelectField
            id="rule-trigger"
            label="This happens"
            value={triggerEvent}
            onChange={setTriggerEvent}
            required
            options={AUTOMATION_TRIGGER_EVENTS.map((e) => ({ value: e, label: TRIGGER_LABEL[e] }))}
          />
        </div>

        <div className="wizard-step wizard-step-muted">
          <div className="wizard-step-label">IF</div>
          <p className="text-muted">In the project chosen above. Extra conditions are not available yet.</p>
        </div>

        <div className="wizard-step">
          <div className="wizard-step-label">THEN</div>
          {projectId ? (
            <ProjectPersonField id="rule-recipient" label="Notify" projectId={projectId} value={recipientId} onChange={setRecipientId} emptyLabel="Choose a project member" />
          ) : (
            <p className="text-muted">Choose a project first.</p>
          )}
          <TextareaField id="rule-message" label="Message" value={message} onChange={setMessage} required />
        </div>

        <CheckboxField id="rule-enabled" label="Turn this rule on now" checked={enabled} onChange={setEnabled} />

        <FormActions>
          <button type="button" className="btn" onClick={onClose} disabled={submitting}>
            Cancel
          </button>
          <button type="submit" className="btn btn-primary" disabled={submitting || !projectId || !name.trim() || !recipientId || !message.trim()}>
            {submitting ? 'Creating…' : 'Create Rule'}
          </button>
        </FormActions>
      </form>
    </Modal>
  )
}

/** Rename a rule or change its message. The trigger and who it notifies are fixed once created. */
export function EditRuleDialog({ rule, onClose, onSaved }: { rule: ProjectAutomation; onClose: () => void; onSaved: () => void }) {
  const [name, setName] = useState(rule.name)
  const [message, setMessage] = useState(rule.actionMessage)
  const [error, setError] = useState<string | null>(null)
  const [submitting, setSubmitting] = useState(false)

  async function handleSubmit(event: React.FormEvent) {
    event.preventDefault()
    setSubmitting(true)
    setError(null)
    try {
      await automationsApi.update(rule.id, { name: name.trim(), actionMessage: message.trim(), version: rule.version })
      onSaved()
      onClose()
    } catch (err) {
      setError(toUserMessage(err))
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <Modal title="Edit Rule" onClose={onClose}>
      <form onSubmit={handleSubmit} className="form-grid">
        {error && <InlineError message={error} />}
        <p className="hint">What starts the rule and who it notifies cannot change. To change those, delete this rule and create a new one.</p>
        <TextField id="rule-edit-name" label="Rule name" value={name} onChange={setName} required maxLength={200} />
        <TextareaField id="rule-edit-message" label="Message" value={message} onChange={setMessage} required />
        <FormActions>
          <button type="button" className="btn" onClick={onClose} disabled={submitting}>
            Cancel
          </button>
          <button type="submit" className="btn btn-primary" disabled={submitting || !name.trim() || !message.trim()}>
            {submitting ? 'Saving…' : 'Save'}
          </button>
        </FormActions>
      </form>
    </Modal>
  )
}
