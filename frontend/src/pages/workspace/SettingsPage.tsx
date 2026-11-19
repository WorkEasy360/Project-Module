import { useEffect, useMemo, useRef, useState, type ReactNode } from 'react'
import { Link } from 'react-router-dom'
import '../../styles/ops.css'
import '../../styles/ops-sections.css'
import { useIdentity } from '../../context/IdentityContext'
import { useSession } from '../../context/SessionContext'
import { useSignOutActions } from '../../hooks/useSignOutActions'
import { useTheme } from '../../context/ThemeContext'
import type { ThemePreference } from '../../context/themeStore'
import { Icon, type IconName } from '../../components/common/Icon'
import { ApiError } from '../../api/client'
import { authApi } from '../../api/auth'
import { projectsApi } from '../../api/projects'
import { toUserMessage } from '../../api/errorMessage'
import { Modal } from '../../components/common/Modal'
import { InlineError } from '../../components/common/ErrorState'
import { FormActions, TextField } from '../../components/forms/fields'
import { usePeople } from '../../context/PeopleContext'
import { useWorkspaceSettings } from '../../context/WorkspaceSettingsContext'
import { useAsyncData } from '../../hooks/useAsyncData'
import { Avatar } from '../../components/common/Avatar'

export type SettingsSection = 'workspace' | 'fields' | 'notifications' | 'integrations' | 'subscription'

const INTRO: Record<SettingsSection, { title: string; text: string }> = {
  workspace: { title: 'Workspace & Access', text: 'Your workspace, your profile and who can do what.' },
  fields: { title: 'Fields & Tags', text: 'The statuses, priorities and tags used across Operations.' },
  notifications: { title: 'Notifications', text: 'What Operations tells you about, in the bell at the top.' },
  integrations: { title: 'Integrations', text: 'Connect Operations to other tools through WorkEasy Connect.' },
  subscription: { title: 'Subscription & Usage', text: 'Your plan and the shared WorkEasy360 wallets.' },
}

const THEME_OPTIONS: { value: ThemePreference; label: string; icon: IconName }[] = [
  { value: 'light', label: 'Light', icon: 'sun' },
  { value: 'dark', label: 'Dark', icon: 'moon' },
  { value: 'system', label: 'Same as my device', icon: 'monitor' },
]

/**
 * V3 Settings: Workspace & Access, Fields & Tags, Notifications, Integrations, Subscription & Usage.
 * Your profile follows how you signed in: an email account can change its photo, name and password
 * here; a Workeasy360 sign-in is read-only, managed in Workeasy360.
 */
export function SettingsPage({ section }: { section: SettingsSection }) {
  const intro = INTRO[section]
  return (
    <div className="ops-page ops-section-page ops-settings">
      <header className="ops-page-head">
        <div>
          <h1>{intro.title}</h1>
          <p>{intro.text}</p>
        </div>
      </header>
      {section === 'workspace' && <WorkspaceAccess />}
      {section === 'fields' && <FieldsAndTags />}
      {section === 'notifications' && <Notifications />}
      {section === 'integrations' && <Integrations />}
      {section === 'subscription' && <Subscription />}
    </div>
  )
}

function Card({ icon, title, children, className = '' }: { icon: IconName; title: string; children: ReactNode; className?: string }) {
  const id = `set-${title.toLowerCase().replace(/[^a-z]+/g, '-')}`
  return (
    <section className={`ops-card ops-set-card ${className}`} aria-labelledby={id}>
      <header className="ops-card-head">
        <h2 id={id}>
          <Icon name={icon} size={22} /> {title}
        </h2>
      </header>
      {children}
    </section>
  )
}

function Row({ label, hint, children }: { label: string; hint?: string; children: ReactNode }) {
  return (
    <div className="ops-set-row">
      <div className="ops-set-label">
        <strong>{label}</strong>
        {hint && <span>{hint}</span>}
      </div>
      <div className="ops-set-value">{children}</div>
    </div>
  )
}

/* ---------------- Workspace & Access ---------------- */

function WorkspaceAccess() {
  return (
    <div className="ops-grid ops-grid-2 ops-set-grid">
      <WorkspaceCard />
      <ProfileCard />
      <AccessCard />
      <AppearanceCard />
    </div>
  )
}

function WorkspaceCard() {
  const { settings, rename } = useWorkspaceSettings()
  const [editing, setEditing] = useState(false)
  const [name, setName] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [saving, setSaving] = useState(false)
  return (
    <Card icon="building" title="Workspace">
      <Row label="Workspace name" hint="Shown at the top of every page">
        <span>{settings?.name ?? '…'}</span>
        {settings?.canEdit && (
          <button type="button" className="ops-link-btn" onClick={() => { setName(settings.name); setError(null); setEditing(true) }}>
            Rename
          </button>
        )}
      </Row>
      <Row label="Plan">
        <span>
          {settings?.plan.name ?? '…'}
          {settings?.plan.validUntil ? ` · valid till ${settings.plan.validUntil}` : ''}
        </span>
        <Link to="/settings/subscription" className="ops-link-btn">
          Details
        </Link>
      </Row>
      {editing && (
        <Modal title="Rename workspace" onClose={() => setEditing(false)}>
          <form
            className="form-grid"
            onSubmit={async (e) => {
              e.preventDefault()
              setSaving(true)
              setError(null)
              try {
                await rename(name.trim())
                setEditing(false)
              } catch (err) {
                setError(toUserMessage(err))
              } finally {
                setSaving(false)
              }
            }}
          >
            {error && <InlineError message={error} />}
            <TextField id="workspace-name" label="Workspace name" value={name} onChange={setName} required maxLength={200} placeholder="e.g. Acme Enterprises Pvt. Ltd." />
            <FormActions>
              <button type="button" className="btn" onClick={() => setEditing(false)} disabled={saving}>Cancel</button>
              <button type="submit" className="btn btn-primary" disabled={saving || !name.trim()}>{saving ? 'Saving…' : 'Save'}</button>
            </FormActions>
          </form>
        </Modal>
      )}
    </Card>
  )
}

/** Upload, change or remove your profile photo. Others in the workspace see it next to your name. */
function ProfilePhotoRow() {
  const { identity } = useIdentity()
  const { people, reload } = usePeople()
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const inputRef = useRef<HTMLInputElement>(null)
  if (!identity) return null
  const me = people.find((p) => p.id === identity.userId)
  const name = identity.name ?? identity.email ?? ''

  async function run(action: () => Promise<unknown>) {
    setBusy(true)
    setError(null)
    try {
      await action()
      reload()
    } catch (err) {
      setError(toUserMessage(err))
    } finally {
      setBusy(false)
    }
  }

  return (
    <Row label="Profile photo" hint="PNG, JPEG, WebP or GIF, up to 2 MB">
      <Avatar userId={identity.userId} name={name} className="ops-set-photo" />
      <input
        ref={inputRef}
        type="file"
        accept="image/png,image/jpeg,image/webp,image/gif"
        hidden
        aria-label="Choose a profile photo"
        onChange={(e) => {
          const file = e.target.files?.[0]
          e.target.value = ''
          if (file) void run(() => authApi.uploadAvatar(file))
        }}
      />
      <button type="button" className="ops-link-btn" disabled={busy} onClick={() => inputRef.current?.click()}>
        {busy ? 'Saving…' : me?.avatarVersion ? 'Change photo' : 'Upload photo'}
      </button>
      {me?.avatarVersion && (
        <button type="button" className="ops-link-btn" disabled={busy} onClick={() => void run(() => authApi.removeAvatar())}>
          Remove
        </button>
      )}
      {error && <InlineError message={error} />}
    </Row>
  )
}

function ProfileCard() {
  const { identity } = useIdentity()
  return identity?.provider === 'workeasy360' ? <Workeasy360ProfileCard /> : <EmailProfileCard />
}

/** An email account: its photo and name, which it can change here, its email and its password. */
function EmailProfileCard() {
  const { identity, clearIdentity } = useIdentity()
  const [dialog, setDialog] = useState<'profile' | 'password' | null>(null)
  return (
    <Card icon="user" title="Your profile">
      <ProfilePhotoRow />
      <Row label="Name">
        <span>{identity?.name ?? <span className="ops-muted">Not set</span>}</span>
        {identity && (
          <button type="button" className="ops-link-btn" onClick={() => setDialog('profile')}>
            Edit name
          </button>
        )}
      </Row>
      <Row label="Email">
        <span>{identity?.email ?? <span className="ops-muted">Not set</span>}</span>
      </Row>
      <Row label="Password">
        {identity && (
          <button type="button" className="ops-link-btn" onClick={() => setDialog('password')}>
            Change password
          </button>
        )}
        {identity && (
          <button type="button" className="ops-link-btn danger" onClick={clearIdentity}>
            <Icon name="logout" size={16} /> Sign out
          </button>
        )}
      </Row>
      {dialog === 'profile' && <EditNameDialog onClose={() => setDialog(null)} />}
      {dialog === 'password' && <ChangePasswordDialog onClose={() => setDialog(null)} />}
    </Card>
  )
}

/**
 * A Workeasy360 sign-in: who Workeasy360 says the person is, read-only (the name, email, password and
 * organization are managed in Workeasy360, and there is no email account here to hold a photo), and
 * the two ways to sign out.
 */
function Workeasy360ProfileCard() {
  const { user, organization } = useSession()
  const { busy, error, workeasy360SignOut, signOut, signOutEverywhere } = useSignOutActions()
  return (
    <Card icon="user" title="Your profile">
      <Row label="Name" hint="From your Workeasy360 account">
        {user?.name ? <span>{user.name}</span> : <span className="ops-muted">Not shared</span>}
      </Row>
      <Row label="Email" hint="From your Workeasy360 account">
        {user?.email ? <span>{user.email}</span> : <span className="ops-muted">Not shared</span>}
      </Row>
      <Row label="User ID" hint="Read-only">
        <CopyValue label="user ID" value={user?.id ?? null} />
      </Row>
      <Row label="Organization ID" hint="Read-only">
        <CopyValue label="organization ID" value={organization?.id ?? null} />
      </Row>
      <p className="ops-field-note">
        You signed in to Easy Project with Workeasy360. Your name, email, password and organization come from
        that sign-in and are changed in Workeasy360, not here; the server decides what you may do.
      </p>
      {error && (
        <div className="banner banner-error" role="alert">
          {error}
        </div>
      )}
      <Row label="Sign out">
        <button type="button" className="ops-link-btn danger" onClick={signOut} disabled={busy}>
          <Icon name="logout" size={16} /> Sign out of Easy Project
        </button>
        {workeasy360SignOut && (
          <button type="button" className="ops-link-btn danger" onClick={signOutEverywhere} disabled={busy}>
            <Icon name="logout" size={16} /> Sign out of Easy Project and Workeasy360
          </button>
        )}
      </Row>
    </Card>
  )
}

function CopyValue({ label, value }: { label: string; value: string | null }) {
  const [state, setState] = useState<'idle' | 'copied' | 'failed'>('idle')

  useEffect(() => {
    if (state === 'idle') return
    const timer = window.setTimeout(() => setState('idle'), 2000)
    return () => window.clearTimeout(timer)
  }, [state])

  if (!value) return <span className="ops-muted">Not set</span>

  async function copy() {
    try {
      if (!navigator.clipboard) throw new Error('Clipboard unavailable')
      await navigator.clipboard.writeText(value as string)
      setState('copied')
    } catch {
      setState('failed')
    }
  }

  return (
    <span className="ops-set-copy">
      <code className="ops-set-code">{value}</code>
      <button type="button" className="ops-link-btn" onClick={copy} aria-label={`Copy ${label}`}>
        {state === 'copied' ? (
          <>
            <Icon name="check" size={16} /> Copied
          </>
        ) : state === 'failed' ? (
          'Copy failed'
        ) : (
          'Copy'
        )}
      </button>
    </span>
  )
}

function AccessCard() {
  const { people, isAdmin } = usePeople()
  const admins = people.filter((p) => p.workspaceRole === 'ADMIN').length
  return (
    <Card icon="lock" title="Access">
      <Row label="Your role">
        <span>{isAdmin ? 'Admin' : 'Member'}</span>
      </Row>
      <Row label="Members" hint="Admins manage members, invitations and workspace settings">
        <span>
          {people.length} {people.length === 1 ? 'person' : 'people'} · {admins} {admins === 1 ? 'admin' : 'admins'}
        </span>
        <Link to="/team" className="ops-link-btn">
          Team → Members
        </Link>
      </Row>
      <Row label="Project access" hint="Set per project">
        <span>Each project&apos;s Members tab decides who can see and change it.</span>
      </Row>
    </Card>
  )
}

function AppearanceCard() {
  const { preference, setPreference } = useTheme()
  return (
    <Card icon="sun" title="Appearance">
      <Row label="Colour theme" hint="Saved in this browser">
        <div className="ops-choice" role="radiogroup" aria-label="Theme">
          {THEME_OPTIONS.map((opt) => (
            <button
              key={opt.value}
              type="button"
              role="radio"
              aria-checked={preference === opt.value}
              className={preference === opt.value ? 'on' : ''}
              onClick={() => setPreference(opt.value)}
            >
              <Icon name={opt.icon} size={18} /> {opt.label}
            </button>
          ))}
        </div>
      </Row>
    </Card>
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

/* ---------------- Fields & Tags ---------------- */

const DEFINITIONS: { title: string; items: { label: string; tone: string; note?: string }[] }[] = [
  {
    title: 'Task status',
    items: [
      { label: 'To Do', tone: 'gray', note: 'default' },
      { label: 'In Progress', tone: 'blue' },
      { label: 'Blocked', tone: 'red' },
      { label: 'Done', tone: 'green' },
    ],
  },
  {
    title: 'Task priority',
    items: [
      { label: 'Low', tone: 'gray' },
      { label: 'Normal', tone: 'blue', note: 'default' },
      { label: 'High', tone: 'orange' },
    ],
  },
  {
    title: 'Project status',
    items: [
      { label: 'Active', tone: 'green', note: 'default' },
      { label: 'On Hold', tone: 'orange' },
      { label: 'Completed', tone: 'blue' },
      { label: 'Archived', tone: 'purple', note: 'kept, read only' },
    ],
  },
]

/** Count each project tag in use, most used first. */
export function tagCounts(lists: (string[] | undefined)[]): { tag: string; count: number }[] {
  const counts = new Map<string, number>()
  for (const tags of lists) for (const tag of tags ?? []) counts.set(tag, (counts.get(tag) ?? 0) + 1)
  return [...counts.entries()].map(([tag, count]) => ({ tag, count })).sort((a, b) => b.count - a.count || a.tag.localeCompare(b.tag))
}

function FieldsAndTags() {
  const projects = useAsyncData(() => projectsApi.list(0, 100), [])
  const tags = useMemo(() => tagCounts((projects.data?.content ?? []).map((p) => p.tags)), [projects.data])
  return (
    <>
      <div className="ops-grid ops-grid-2 ops-set-grid">
        <Card icon="flag" title="Statuses and priorities">
          {DEFINITIONS.map((d) => (
            <Row key={d.title} label={d.title}>
              <span className="ops-chip-row">
                {d.items.map((item) => (
                  <span key={item.label} className={`ops-chip tone-${item.tone}`}>
                    {item.label}
                    {item.note && <em> · {item.note}</em>}
                  </span>
                ))}
              </span>
            </Row>
          ))}
          <p className="ops-field-note">Overdue is not a status: a task is overdue when its due date has passed and it is not Done.</p>
        </Card>
        <Card icon="field" title="Project tags in use">
          {projects.loading && <p className="ops-empty">Loading tags…</p>}
          {projects.error && <p className="ops-empty">{projects.error}</p>}
          {projects.data && tags.length === 0 && <p className="ops-empty">No project has tags yet. Add tags when you create or edit a project.</p>}
          {tags.length > 0 && (
            <span className="ops-chip-row">
              {tags.map((t, i) => (
                <span key={t.tag} className={`ops-chip tone-${i % 2 === 0 ? 'blue' : 'purple'}`}>
                  {t.tag} <em>· {t.count}</em>
                </span>
              ))}
            </span>
          )}
          <p className="ops-field-note">Project tags belong to Operations and are not copied onto customer contacts.</p>
        </Card>
      </div>
      <p className="ops-notice">
        <Icon name="field" size={18} /> Custom fields and shared tags come from the WorkEasy360 Field Registry and Tag Directory,
        which connect to Operations in a later update. Nothing here can be edited yet.
      </p>
    </>
  )
}

/* ---------------- Notifications ---------------- */

const NOTICES: { icon: IconName; title: string; who: string }[] = [
  { icon: 'userPlus', title: 'A task is assigned to you', who: 'The new assignee' },
  { icon: 'clock', title: 'A task is due soon', who: 'The assignee, once, the day before it is due' },
  { icon: 'checkCircle', title: 'A task is completed', who: 'The assignee and the project owner' },
  { icon: 'comment', title: 'Someone comments on a task', who: 'The assignee and earlier commenters' },
  { icon: 'member', title: 'Someone mentions you', who: 'The person mentioned in a comment' },
]

function Notifications() {
  return (
    <>
      <section className="ops-card ops-table-card">
        <div className="ops-table-scroll">
          <table className="ops-table">
            <thead>
              <tr>
                <th>When</th>
                <th>Who is told</th>
                <th>Where</th>
              </tr>
            </thead>
            <tbody>
              {NOTICES.map((n) => (
                <tr key={n.title}>
                  <td>
                    <span className="ops-owner">
                      <Icon name={n.icon} size={20} className="tone-text-green" />
                      <strong>{n.title}</strong>
                    </span>
                  </td>
                  <td>{n.who}</td>
                  <td>In the app (bell)</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
        <footer className="ops-table-foot">
          <span>Nobody is told about their own changes.</span>
        </footer>
      </section>
      <p className="ops-notice">
        <Icon name="bell" size={18} /> These are always on for now. Choosing which notices you get, and email or WhatsApp delivery,
        arrive with the automation and notifications update.
      </p>
    </>
  )
}

/* ---------------- Integrations ---------------- */

const CONNECT_ITEMS: { icon: IconName; name: string; text: string }[] = [
  { icon: 'bolt', name: 'Incoming webhook', text: 'Create a task in Operations when another system sends an event.' },
  { icon: 'arrowRight', name: 'Outgoing webhook', text: 'Tell another system when a task is created, updated or completed.' },
  { icon: 'settings', name: 'API connection', text: 'Connect a system you build, with field mapping and a test preview.' },
]

const COMING_SOON = ['Google Sheets', 'Pabbly', 'Zoom', 'Zapier']

function Integrations() {
  return (
    <>
      <section className="ops-card" aria-labelledby="connect-title">
        <header className="ops-card-head">
          <h2 id="connect-title">
            <Icon name="bolt" size={22} /> WorkEasy Connect
          </h2>
          <span className="ops-soon-badge">Not connected yet</span>
        </header>
        <p className="ops-muted">
          External connections run through WorkEasy Connect, the shared WorkEasy360 integration tool, with one set of
          credentials, delivery logs and retries for every product. It is not linked to this workspace yet.
        </p>
        <div className="ops-connect-grid">
          {CONNECT_ITEMS.map((item) => (
            <div key={item.name} className="ops-connect-item">
              <Icon name={item.icon} size={24} className="tone-text-green" />
              <div>
                <strong>{item.name}</strong>
                <span>{item.text}</span>
              </div>
            </div>
          ))}
        </div>
      </section>
      <section className="ops-card" aria-labelledby="soon-title">
        <header className="ops-card-head">
          <h2 id="soon-title">
            <Icon name="clock" size={22} /> Ready-made connectors
          </h2>
        </header>
        <ul className="ops-soon-grid">
          {COMING_SOON.map((name) => (
            <li key={name} className="ops-soon-tile" aria-disabled="true">
              <strong>{name}</strong>
              <span className="ops-soon-badge">Coming Soon</span>
              <span className="ops-muted">Not available yet</span>
            </li>
          ))}
        </ul>
      </section>
    </>
  )
}

/* ---------------- Subscription & Usage ---------------- */

const WALLETS: { icon: IconName; name: string; text: string }[] = [
  { icon: 'crown', name: 'Platform Wallet', text: 'Activate or renew your plan.' },
  { icon: 'sparkles', name: 'AI Credits', text: 'What the AI Assistant uses, and how much is left.' },
  { icon: 'megaphone', name: 'Consumption Wallet', text: 'Billable messaging and integrations.' },
]

function Subscription() {
  const { settings } = useWorkspaceSettings()
  const plan = settings?.plan
  const manageUrl = plan?.manageUrl ?? 'https://workeasy360.com'
  return (
    <div className="ops-grid ops-grid-2 ops-set-grid">
      <Card icon="crown" title="Your plan">
        <Row label="Plan">
          <strong className="ops-plan-name">{plan?.name ?? '…'}</strong>
        </Row>
        <Row label="Valid till">
          <span>{plan?.validUntil ?? <span className="ops-muted">Not set</span>}</span>
        </Row>
        <div className="ops-form-actions">
          <a className="ops-primary" href={manageUrl} target="_blank" rel="noopener noreferrer">
            Manage Plan <Icon name="arrowRight" size={18} />
          </a>
        </div>
        <p className="ops-field-note">Opens the WorkEasy360 platform, where plans and checkout are managed for every product.</p>
      </Card>
      <Card icon="barChart" title="Wallets and usage">
        <ul className="ops-wallets">
          {WALLETS.map((w) => (
            <li key={w.name}>
              <Icon name={w.icon} size={24} className="tone-text-green" />
              <div>
                <strong>{w.name}</strong>
                <span>{w.text}</span>
              </div>
              <a className="ops-link-btn" href={manageUrl} target="_blank" rel="noopener noreferrer">
                Open on WorkEasy360 <Icon name="arrowRight" size={16} />
              </a>
            </li>
          ))}
        </ul>
        <p className="ops-field-note">Balances are kept by the WorkEasy360 platform; Operations has no wallet of its own.</p>
      </Card>
    </div>
  )
}
