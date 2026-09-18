import { useIdentity } from '../../context/IdentityContext'
import { Icon } from './Icon'

/**
 * Shown until identity is configured. Explains *why* (this backend has no login of its own —
 * see IdentityWidget's modal copy) without dominating the page, and opens the same identity
 * dialog the header's compact indicator uses.
 */
export function IdentitySetupBanner() {
  const { isConfigured, openPrompt } = useIdentity()
  if (isConfigured) return null

  return (
    <div className="setup-banner" role="status">
      <Icon name="user" size={18} />
      <div className="setup-banner-text">
        <strong>Set your identity to get started.</strong>
        <span>
          {' '}
          This backend authorizes every request by the User ID and Organization ID you provide —
          there's no separate login step.
        </span>
      </div>
      <button type="button" className="btn btn-sm btn-primary" onClick={openPrompt}>
        Set identity
      </button>
    </div>
  )
}
