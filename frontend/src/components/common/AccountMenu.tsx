import { useState } from 'react'
import { useSession } from '../../context/SessionContext'
import { displayName } from '../../context/sessionStore'
import { useSignOutActions } from '../../hooks/useSignOutActions'
import { Modal } from './Modal'
import { Icon } from './Icon'

function initials(name: string | null): string {
  const text = (name ?? '').trim()
  if (!text) return '?'
  const parts = text.split(/\s+/)
  const letters = parts.length > 1 ? parts[0][0] + parts[parts.length - 1][0] : text.slice(0, 2)
  return letters.toUpperCase()
}

function shortId(id: string): string {
  return id.length > 8 ? `${id.slice(0, 8)}…` : id
}

/** Header account control: avatar + name; opens a dialog with the account and the sign-out choices. */
export function AccountMenu() {
  const { user, organization } = useSession()
  const [open, setOpen] = useState(false)
  const name = displayName(user)

  return (
    <>
      <button
        type="button"
        className="identity-trigger configured"
        onClick={() => setOpen(true)}
        title="Your account and sign out"
        aria-haspopup="dialog"
      >
        <span className="identity-avatar" aria-hidden="true">
          {initials(user?.name ?? user?.email ?? name)}
        </span>
        <span className="identity-text">
          <span className="identity-name">{name ?? 'Account'}</span>
          {organization && <span className="identity-sub">org {shortId(organization.id)}</span>}
        </span>
        <Icon name="chevronDown" size={14} />
      </button>
      {open && <AccountDialog onClose={() => setOpen(false)} />}
    </>
  )
}

function AccountDialog({ onClose }: { onClose: () => void }) {
  const { user, organization } = useSession()
  const { busy, error, workeasy360SignOut, signOut, signOutEverywhere } = useSignOutActions()

  return (
    <Modal title="Your account" onClose={onClose}>
      <div className="form-grid">
        {error && (
          <div className="banner banner-error" role="alert">
            {error}
          </div>
        )}
        <dl className="account-details">
          <dt>Name</dt>
          <dd>{user?.name ?? 'Not shared'}</dd>
          <dt>Email</dt>
          <dd>{user?.email ?? 'Not shared'}</dd>
          <dt>Organization</dt>
          <dd className="mono">{organization?.id ?? '—'}</dd>
        </dl>
        <p className="text-muted">You are signed in with Workeasy360.</p>
        <div className="form-actions" style={{ justifyContent: 'flex-end', flexWrap: 'wrap' }}>
          <button type="button" className="btn" onClick={signOut} disabled={busy}>
            <Icon name="logout" size={15} /> Sign out of Easy Project
          </button>
          {workeasy360SignOut && (
            <button type="button" className="btn btn-danger" onClick={signOutEverywhere} disabled={busy}>
              <Icon name="logout" size={15} /> Sign out of Easy Project and Workeasy360
            </button>
          )}
        </div>
      </div>
    </Modal>
  )
}
