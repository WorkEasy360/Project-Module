import { useIdentity } from '../../context/IdentityContext'
import { Modal } from './Modal'
import { Icon } from './Icon'
import { usePeople } from '../../context/PeopleContext'
import { Avatar } from './Avatar'

/** Header account control: avatar, name and email; opens a dialog with the account and Sign out. */
export function IdentityWidget() {
  const { identity, clearIdentity, isPromptOpen, openPrompt, closePrompt } = useIdentity()
  const { isAdmin } = usePeople()
  if (!identity) return null

  const name = identity.name ?? identity.email ?? identity.userId.slice(0, 8)

  return (
    <>
      <button type="button" className="identity-trigger configured" onClick={openPrompt} title="Your account">
        <Avatar userId={identity.userId} name={name} className="identity-avatar" />
        <span className="identity-text">
          <span className="identity-name">{name}</span>
          <span className="identity-sub">{isAdmin ? 'Admin' : 'Member'}</span>
        </span>
        <Icon name="chevronDown" size={18} />
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
