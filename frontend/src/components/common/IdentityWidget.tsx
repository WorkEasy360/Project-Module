import { useIdentity } from '../../context/IdentityContext'
import { Modal } from './Modal'
import { Icon } from './Icon'
import { initials } from '../../utils/format'
import { usePeople } from '../../context/PeopleContext'

/** Header account control: avatar, name and email; opens a dialog with the account and Sign out. */
export function IdentityWidget() {
  const { identity, clearIdentity, isPromptOpen, openPrompt, closePrompt } = useIdentity()
  const { isAdmin } = usePeople()
  if (!identity) return null

  const name = identity.name ?? identity.email ?? identity.userId.slice(0, 8)

  return (
    <>
      <button type="button" className="identity-trigger configured" onClick={openPrompt} title="Your account">
        <span className="identity-avatar" aria-hidden="true">
          {initials(name)}
        </span>
        <span className="identity-text">
          <span className="identity-name">{name}</span>
          <span className="identity-sub">{isAdmin ? 'Admin' : 'Member'}</span>
        </span>
        <Icon name="chevronDown" size={14} />
      </button>
      {isPromptOpen && (
        <Modal title="Your account" onClose={closePrompt}>
          <p>
            <strong>{name}</strong>
            {identity.email && (
              <>
                <br />
                <span className="text-muted">{identity.email}</span>
              </>
            )}
          </p>
          <div className="form-actions" style={{ justifyContent: 'space-between' }}>
            <button
              type="button"
              className="btn btn-ghost"
              onClick={() => {
                clearIdentity()
                closePrompt()
              }}
            >
              <Icon name="logout" size={15} /> Sign out
            </button>
            <button type="button" className="btn" onClick={closePrompt}>
              Close
            </button>
          </div>
        </Modal>
      )}
    </>
  )
}
