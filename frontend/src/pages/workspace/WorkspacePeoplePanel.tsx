import { useState } from 'react'
import { invitationLink, passwordResetLink, workspaceApi, type Invitation } from '../../api/workspace'
import { toUserMessage } from '../../api/errorMessage'
import { Badge } from '../../components/common/Badge'
import { InlineError } from '../../components/common/ErrorState'
import { Icon } from '../../components/common/Icon'
import { Modal } from '../../components/common/Modal'
import { FormActions, TextField } from '../../components/forms/fields'
import { useIdentity } from '../../context/IdentityContext'
import { usePeople } from '../../context/PeopleContext'
import { useAsyncData } from '../../hooks/useAsyncData'
import { formatDate, initials } from '../../utils/format'

/** Everyone in the workspace and, for admins, inviting more people and pending invitations. */
export function WorkspacePeoplePanel() {
  const { identity } = useIdentity()
  const { people, isAdmin } = usePeople()
  const [inviteOpen, setInviteOpen] = useState(false)
  const [refreshKey, setRefreshKey] = useState(0)
  const [resetLink, setResetLink] = useState<{ email: string; link: string } | null>(null)

  async function createResetLink(accountId: string, email: string) {
    const { token } = await workspaceApi.resetLink(accountId)
    setResetLink({ email, link: passwordResetLink(token) })
  }

  if (people.length === 0) return null

  return (
    <section className="ws-section ws-people-panel" aria-labelledby="ws-people-title">
      <div className="ws-section-head ws-panel-head">
        <div>
          <h2 id="ws-people-title">Workspace members</h2>
          <p>
            {people.length} {people.length === 1 ? 'person' : 'people'} in your workspace. Add them to a project from
            its Team tab.
          </p>
        </div>
        {isAdmin && (
          <button type="button" className="btn btn-primary" onClick={() => setInviteOpen(true)}>
            <Icon name="plus" size={15} /> Invite people
          </button>
        )}
      </div>
      <ul className="ws-directory">
        {people.map((person) => (
          <li key={person.id} className="ws-directory-row">
            <span className="ws-avatar sm" aria-hidden="true">
              {initials(person.name)}
            </span>
            <span className="ws-directory-name">
              <strong>
                {person.name}
                {person.id === identity?.userId ? ' (you)' : ''}
              </strong>
              <span className="text-muted">{person.email}</span>
            </span>
            {person.workspaceRole === 'ADMIN' && <Badge tone="primary">Admin</Badge>}
            {isAdmin && person.id !== identity?.userId && (
              <button type="button" className="btn btn-sm btn-ghost" onClick={() => createResetLink(person.id, person.email)}>
                Reset password
              </button>
            )}
          </li>
        ))}
      </ul>
      {isAdmin && <PendingInvitations refreshKey={refreshKey} />}
      {resetLink && (
        <Modal title="Password reset link" onClose={() => setResetLink(null)}>
          <p className="text-muted">Send this to {resetLink.email}. It works once and expires in 2 hours.</p>
          <CopyableLink link={resetLink.link} label="Password reset link" />
          <FormActions>
            <button type="button" className="btn btn-primary" onClick={() => setResetLink(null)}>
              Done
            </button>
          </FormActions>
        </Modal>
      )}
      {inviteOpen && (
        <InviteDialog onClose={() => setInviteOpen(false)} onInvited={() => setRefreshKey((key) => key + 1)} />
      )}
    </section>
  )
}

function PendingInvitations({ refreshKey }: { refreshKey: number }) {
  const { data, reload } = useAsyncData(() => workspaceApi.invitations(), [refreshKey])
  if (!data || data.length === 0) return null

  async function revoke(invitation: Invitation) {
    await workspaceApi.revokeInvitation(invitation.id)
    reload()
  }

  return (
    <>
      <h3 className="ws-subhead">Waiting to join</h3>
      <ul className="ws-directory">
        {data.map((invitation) => (
          <li key={invitation.id} className="ws-directory-row">
            <span className="ws-avatar sm" aria-hidden="true">
              <Icon name="user" size={14} />
            </span>
            <span className="ws-directory-name">
              <strong>{invitation.email}</strong>
              <span className="text-muted">Invitation expires {formatDate(invitation.expiresAt)}</span>
            </span>
            <button type="button" className="btn btn-sm btn-ghost" onClick={() => revoke(invitation)}>
              Withdraw
            </button>
          </li>
        ))}
      </ul>
    </>
  )
}

function CopyableLink({ link, label }: { link: string; label: string }) {
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

function InviteDialog({ onClose, onInvited }: { onClose: () => void; onInvited: () => void }) {
  const [email, setEmail] = useState('')
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

  if (link) {
    return (
      <Modal title="Invitation ready" onClose={onClose}>
        <p className="text-muted">
          Send this link to {email.trim()}. It works once and expires in 7 days. You will not be able to see it again.
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

  return (
    <Modal title="Invite someone" onClose={onClose}>
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
        <FormActions>
          <button type="button" className="btn" onClick={onClose} disabled={submitting}>
            Cancel
          </button>
          <button type="submit" className="btn btn-primary" disabled={submitting || !email.trim()}>
            {submitting ? 'Creating…' : 'Create invitation'}
          </button>
        </FormActions>
      </form>
    </Modal>
  )
}
