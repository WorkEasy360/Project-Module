import { useState } from 'react'
import { useIdentity } from '../../context/IdentityContext'
import { Modal } from './Modal'
import { Icon } from './Icon'

const UUID_RE = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i

function shortId(id: string): string {
  return id.length > 8 ? `${id.slice(0, 8)}…` : id
}

/** Header identity control: avatar + short ids; opens the identity dialog (change / sign out). */
export function IdentityWidget() {
  const { identity, setIdentity, clearIdentity, isPromptOpen, openPrompt, closePrompt } = useIdentity()

  return (
    <>
      <button
        type="button"
        className={`identity-trigger${identity ? ' configured' : ''}`}
        onClick={openPrompt}
        title={identity ? 'Change your identity headers or sign out' : 'Set your identity to use the app'}
      >
        {identity ? (
          <>
            <span className="identity-avatar" aria-hidden="true">
              {identity.userId.slice(0, 2).toUpperCase()}
            </span>
            <span className="identity-text">
              <span className="identity-name">{shortId(identity.userId)}</span>
              <span className="identity-sub">org {shortId(identity.orgId)}</span>
            </span>
            <Icon name="chevronDown" size={14} />
          </>
        ) : (
          <>
            <Icon name="user" size={16} />
            <span>Set identity</span>
          </>
        )}
      </button>
      {isPromptOpen && (
        <IdentityModal onClose={closePrompt} onSave={setIdentity} onSignOut={identity ? clearIdentity : null} />
      )}
    </>
  )
}

function IdentityModal({
  onClose,
  onSave,
  onSignOut,
}: {
  onClose: () => void
  onSave: (identity: { userId: string; orgId: string }) => void
  onSignOut: (() => void) | null
}) {
  const { identity } = useIdentity()
  const [userId, setUserId] = useState(identity?.userId ?? '')
  const [orgId, setOrgId] = useState(identity?.orgId ?? '')
  const [error, setError] = useState<string | null>(null)

  function handleSubmit(event: React.FormEvent) {
    event.preventDefault()
    if (!UUID_RE.test(userId.trim()) || !UUID_RE.test(orgId.trim())) {
      setError('Both User ID and Organization ID must be valid UUIDs.')
      return
    }
    onSave({ userId: userId.trim(), orgId: orgId.trim() })
    onClose()
  }

  return (
    <Modal title="Your identity" onClose={onClose}>
      <p className="text-muted">
        This backend has no login screen — a real deployment sits behind a gateway that supplies
        your user and organization identity on every request. This app asks for the same two
        values directly so it can send them as <code>X-User-Id</code> / <code>X-Org-Id</code>{' '}
        headers. They are stored only in this browser.
      </p>
      <form onSubmit={handleSubmit} className="form-grid">
        {error && <div className="banner banner-error">{error}</div>}
        <div className="form-field">
          <label htmlFor="userId">User ID (UUID)</label>
          <input
            id="userId"
            type="text"
            value={userId}
            onChange={(e) => setUserId(e.target.value)}
            placeholder="00000000-0000-0000-0000-000000000000"
            required
          />
        </div>
        <div className="form-field">
          <label htmlFor="orgId">Organization ID (UUID)</label>
          <input
            id="orgId"
            type="text"
            value={orgId}
            onChange={(e) => setOrgId(e.target.value)}
            placeholder="00000000-0000-0000-0000-000000000000"
            required
          />
        </div>
        <div className="form-actions" style={{ justifyContent: 'space-between' }}>
          {onSignOut ? (
            <button
              type="button"
              className="btn btn-ghost"
              onClick={() => {
                onSignOut()
                onClose()
              }}
            >
              <Icon name="logout" size={15} /> Sign out
            </button>
          ) : (
            <span />
          )}
          <span style={{ display: 'inline-flex', gap: 8 }}>
            <button type="button" className="btn" onClick={onClose}>
              Cancel
            </button>
            <button type="submit" className="btn btn-primary">
              Save
            </button>
          </span>
        </div>
      </form>
    </Modal>
  )
}
