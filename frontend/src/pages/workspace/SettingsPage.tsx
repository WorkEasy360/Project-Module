import { useState, type ReactNode } from 'react'
import { Link } from 'react-router-dom'
import '../../styles/workspace.css'
import { useIdentity } from '../../context/IdentityContext'
import { useTheme } from '../../context/ThemeContext'
import { useSidebar } from '../../context/SidebarContext'
import type { ThemePreference } from '../../context/themeStore'
import { PageHeader } from '../../components/common/PageHeader'
import { Icon, type IconName } from '../../components/common/Icon'
import { Badge, type BadgeTone } from '../../components/common/Badge'
import { FEATURES, groupByCategory, type FeatureDefinition, type ReleaseState } from '../../features/registry'
import { humanizeToken } from '../../utils/format'
import { API_BASE_URL, ApiError } from '../../api/client'
import { authApi } from '../../api/auth'
import { toUserMessage } from '../../api/errorMessage'
import { Modal } from '../../components/common/Modal'
import { InlineError } from '../../components/common/ErrorState'
import { FormActions, TextField } from '../../components/forms/fields'
import { usePeople } from '../../context/PeopleContext'

/** Empty means the API is served from the same origin as this page (the packaged deployment). */
const API_BASE_LABEL = API_BASE_URL === '' ? 'Same origin as this page' : API_BASE_URL

const THEME_OPTIONS: { value: ThemePreference; label: string; icon: IconName; hint: string }[] = [
  { value: 'system', label: 'System', icon: 'settings', hint: 'Follows your OS setting' },
  { value: 'light', label: 'Light', icon: 'sun', hint: 'Always light' },
  { value: 'dark', label: 'Dark', icon: 'moon', hint: 'Always dark' },
]

const RELEASE_TONE: Record<ReleaseState, BadgeTone> = {
  AVAILABLE: 'success',
  BETA: 'info',
  COMING_SOON: 'neutral',
}

/**
 * Settings: only things this frontend really controls. The account is who is signed in, theme and
 * sidebar state are per-browser preferences, and the feature list is the registry the navigation
 * is built from. Nothing here is a fake preference: the backend has no notification settings.
 */
export function SettingsPage() {
  return (
    <div className="ws-page rise-in">
      <PageHeader title="Settings" description="Your account, appearance and workspace preferences." />
      <div className="ws-settings">
        <AccountSection />
        <AppearanceSection />
        <NavigationSection />
        <AboutSection />
        <FeaturesSection />
      </div>
    </div>
  )
}

function Section({
  id,
  icon,
  title,
  description,
  span,
  children,
}: {
  id: string
  icon: IconName
  title: string
  description: string
  span?: boolean
  children: ReactNode
}) {
  return (
    <section className={`ws-section${span ? ' span-2' : ''}`} aria-labelledby={`${id}-title`}>
      <div className="ws-section-head">
        <span className="ws-section-icon" aria-hidden="true">
          <Icon name={icon} size={18} />
        </span>
        <div>
          <h2 id={`${id}-title`}>{title}</h2>
          <p>{description}</p>
        </div>
      </div>
      {children}
    </section>
  )
}

function AccountSection() {
  const { identity, clearIdentity } = useIdentity()
  const [dialog, setDialog] = useState<'profile' | 'password' | null>(null)
  return (
    <Section id="account" icon="user" title="Account" description="Who you are signed in as.">
      <div className="ws-row">
        <div className="ws-row-label">
          <strong>Name</strong>
        </div>
        <span>{identity?.name ?? <span className="text-faint">Not set</span>}</span>
      </div>
      <div className="ws-row">
        <div className="ws-row-label">
          <strong>Email</strong>
        </div>
        <span>{identity?.email ?? <span className="text-faint">Not set</span>}</span>
      </div>
      <div className="page-actions">
        {identity && (
          <>
            <button type="button" className="btn" onClick={() => setDialog('profile')}>
              Edit name
            </button>
            <button type="button" className="btn" onClick={() => setDialog('password')}>
              Change password
            </button>
          </>
        )}
        {identity && (
          <button type="button" className="btn btn-danger" onClick={clearIdentity}>
            <Icon name="logout" size={15} /> Sign out
          </button>
        )}
      </div>
      {dialog === 'profile' && <EditNameDialog onClose={() => setDialog(null)} />}
      {dialog === 'password' && <ChangePasswordDialog onClose={() => setDialog(null)} />}
    </Section>
  )
}

function EditNameDialog({ onClose }: { onClose: () => void }) {
  const { identity, setIdentity } = useIdentity()
  const { reload } = usePeople()
  const [name, setName] = useState(identity?.name ?? '')
  const [error, setError] = useState<string | null>(null)

  async function save(event: React.FormEvent) {
    event.preventDefault()
    try {
      const account = await authApi.updateProfile(name.trim())
      if (identity) setIdentity({ ...identity, name: account.name })
      reload()
      onClose()
    } catch (err) {
      setError(toUserMessage(err))
    }
  }

  return (
    <Modal title="Edit name" onClose={onClose}>
      <form className="form-grid" onSubmit={save}>
        {error && <InlineError message={error} />}
        <TextField id="profile-name" label="Name" value={name} onChange={setName} required maxLength={120} />
        <FormActions>
          <button type="button" className="btn" onClick={onClose}>
            Cancel
          </button>
          <button type="submit" className="btn btn-primary" disabled={!name.trim()}>
            Save
          </button>
        </FormActions>
      </form>
    </Modal>
  )
}

function ChangePasswordDialog({ onClose }: { onClose: () => void }) {
  const { clearIdentity } = useIdentity()
  const [current, setCurrent] = useState('')
  const [next, setNext] = useState('')
  const [error, setError] = useState<string | null>(null)

  async function save(event: React.FormEvent) {
    event.preventDefault()
    if (next.length < 8) {
      setError('Your new password must be at least 8 characters.')
      return
    }
    try {
      await authApi.changePassword({ currentPassword: current, newPassword: next })
      // Every session has now ended, including this one, so the person signs in again.
      clearIdentity()
    } catch (err) {
      setError(err instanceof ApiError && err.problem?.detail ? err.problem.detail : toUserMessage(err))
    }
  }

  return (
    <Modal title="Change password" onClose={onClose}>
      <form className="form-grid" onSubmit={save}>
        {error && <InlineError message={error} />}
        <div className="form-field">
          <label htmlFor="current-password">Current password</label>
          <input id="current-password" type="password" autoComplete="current-password" value={current} onChange={(e) => setCurrent(e.target.value)} required />
        </div>
        <div className="form-field">
          <label htmlFor="new-password">New password</label>
          <input id="new-password" type="password" autoComplete="new-password" value={next} onChange={(e) => setNext(e.target.value)} required />
          <span className="hint">You will be signed out everywhere and sign in again with this one.</span>
        </div>
        <FormActions>
          <button type="button" className="btn" onClick={onClose}>
            Cancel
          </button>
          <button type="submit" className="btn btn-primary" disabled={!current || !next}>
            Change password
          </button>
        </FormActions>
      </form>
    </Modal>
  )
}

function AppearanceSection() {
  const { preference, resolved, setPreference } = useTheme()
  return (
    <Section id="appearance" icon="sun" title="Appearance" description="Colour theme for this browser.">
      <fieldset className="ws-radios">
        <legend className="sr-only">Theme</legend>
        {THEME_OPTIONS.map((opt) => (
          <label key={opt.value} className="ws-radio">
            <input
              type="radio"
              name="theme"
              value={opt.value}
              checked={preference === opt.value}
              onChange={() => setPreference(opt.value)}
            />
            <Icon name={opt.icon} size={18} />
            {opt.label}
          </label>
        ))}
      </fieldset>
      <p className="ws-hint">
        {THEME_OPTIONS.find((o) => o.value === preference)?.hint}. Currently showing the {resolved} theme. Saved
        in this browser only.
      </p>
    </Section>
  )
}

function NavigationSection() {
  const { collapsed, toggle, isMobile } = useSidebar()
  return (
    <Section id="navigation" icon="menu" title="Navigation" description="How the sidebar behaves.">
      <label className="ws-switch">
        <input type="checkbox" checked={collapsed} onChange={toggle} disabled={isMobile} />
        Collapse the sidebar to icons only
      </label>
      <p className="ws-hint">
        {isMobile
          ? 'On small screens the sidebar is a drawer, so this setting applies on larger screens.'
          : 'The same as the menu button in the header. Remembered in this browser.'}
      </p>
    </Section>
  )
}

function FeaturesSection() {
  const workspace = groupByCategory(FEATURES.filter((f) => f.scope === 'workspace'))
  const project = groupByCategory(FEATURES.filter((f) => f.scope === 'project'))
  return (
    <Section
      id="features"
      icon="sparkles"
      title="Features"
      description="Everything this app can do, and where it lives. Read-only."
      span
    >
      <details className="ws-details">
        <summary>Show all features</summary>
      <div className="ws-feature-groups">
        {workspace.map(([category, items]) => (
          <div className="ws-feature-group" key={`w-${category}`}>
            <h3>Workspace · {category}</h3>
            <FeatureList items={items} />
          </div>
        ))}
        {project.map(([category, items]) => (
          <div className="ws-feature-group" key={`p-${category}`}>
            <h3>In each project · {category}</h3>
            <FeatureList items={items} />
          </div>
        ))}
      </div>
      </details>
      <p className="ws-hint">
        Project features open from inside a project (Projects → a project → its tabs). Availability comes from
        the feature registry, not from a per-user setting.
      </p>
    </Section>
  )
}

function FeatureList({ items }: { items: FeatureDefinition[] }) {
  return (
    <ul className="ws-feature-list">
      {items.map((f) => (
        <li key={f.id} className="ws-feature">
          <span className="ws-feature-icon" aria-hidden="true">
            <Icon name={f.icon} size={16} />
          </span>
          <span className="ws-feature-name">
            {f.scope === 'workspace' ? <Link to={f.route}>{f.name}</Link> : <strong>{f.name}</strong>}
            <span>{f.scope === 'workspace' ? f.route : `/projects/…/${f.route}`}</span>
          </span>
          <Badge tone={RELEASE_TONE[f.releaseState]}>{humanizeToken(f.releaseState)}</Badge>
        </li>
      ))}
    </ul>
  )
}

function AboutSection() {
  return (
    <Section id="about" icon="overview" title="About" description="Where this app talks to.">
      <div className="ws-row">
        <div className="ws-row-label">
          <strong>API base URL</strong>
          <span>Resolved at build time; VITE_API_BASE_URL overrides it</span>
        </div>
        <code className="ws-code">{API_BASE_LABEL}</code>
      </div>
      <div className="ws-row">
        <div className="ws-row-label">
          <strong>Stored in this browser</strong>
          <span>Your sign-in session, theme and sidebar preference — nothing else</span>
        </div>
      </div>
    </Section>
  )
}
