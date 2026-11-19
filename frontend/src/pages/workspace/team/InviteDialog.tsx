import { useState } from 'react'
import { invitationLink, workspaceApi } from '../../../api/workspace'
import { toUserMessage } from '../../../api/errorMessage'
import { InlineError } from '../../../components/common/ErrorState'
import { Modal } from '../../../components/common/Modal'
import { FormActions, TextField } from '../../../components/forms/fields'

/** A one-time link with a Copy button. */
export function CopyableLink({ link, label }: { link: string; label: string }) {
  const [copied, setCopied] = useState(false)
  async function copy() {
    await navigator.clipboard?.writeText(link)
    setCopied(true)
  }
  return (
    <div className="ws-invite-link">
      <input type="text" readOnly value={link} aria-label={label} onFocus={(e) => e.target.select()} />
      <button type="button" className="btn" onClick={copy}>
        {copied ? 'Copied' : 'Copy'}
      </button>
    </div>
  )
}

/** Shows a freshly created invitation link; it is only ever shown once. */
export function InvitationReady({ email, link, onClose }: { email: string; link: string; onClose: () => void }) {
  return (
    <Modal title="Invitation ready" onClose={onClose}>
      <p className="text-muted">
        An email is on its way to {email}. You can also send this link yourself. It works once and expires in 7 days,
        and you will not be able to see it again.
      </p>
      <CopyableLink link={link} label="Invitation link" />
      <FormActions>
        <button type="button" className="btn btn-primary" onClick={onClose}>
          Done
        </button>
      </FormActions>
    </Modal>
  )
}

/**
 * Invite a new member to the workspace (admins only). Inviting an address that already has a
 * pending invitation replaces it, so this is also how an invitation is sent again.
 */
export function InviteDialog({
  onClose,
  onInvited,
  initialEmail = '',
}: {
  onClose: () => void
  onInvited: () => void
  initialEmail?: string
}) {
  const [email, setEmail] = useState(initialEmail)
  const [error, setError] = useState<string | null>(null)
  const [submitting, setSubmitting] = useState(false)
  const [link, setLink] = useState<string | null>(null)

  async function handleSubmit(event: React.FormEvent) {
    event.preventDefault()
    setSubmitting(true)
    setError(null)
    try {
      const invitation = await workspaceApi.invite(email.trim())
      setLink(invitationLink(invitation.token as string))
      onInvited()
    } catch (err) {
      setError(toUserMessage(err))
    } finally {
      setSubmitting(false)
    }
  }

  if (link) return <InvitationReady email={email.trim()} link={link} onClose={onClose} />

  return (
    <Modal title="Invite Member" onClose={onClose}>
      <form onSubmit={handleSubmit} className="form-grid">
        {error && <InlineError message={error} />}
        <TextField
          id="invite-email"
          label="Email address"
          value={email}
          onChange={setEmail}
          required
          placeholder="teammate@company.com"
        />
        <p className="hint">They join your workspace with their own login and can then be added to projects.</p>
        <FormActions>
          <button type="button" className="btn" onClick={onClose} disabled={submitting}>
            Cancel
          </button>
          <button type="submit" className="btn btn-primary" disabled={submitting || !email.trim()}>
            {submitting ? 'Sending…' : 'Send invitation'}
          </button>
        </FormActions>
      </form>
    </Modal>
  )
}
