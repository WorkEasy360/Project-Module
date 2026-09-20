import { useEffect, useState, type ReactNode } from 'react'
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

const API_BASE_URL = (import.meta.env.VITE_API_BASE_URL ?? 'http://localhost:8080').replace(/\/$/, '')

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
 * Settings: only things this frontend really controls. Identity is the X-User-Id / X-Org-Id
 * header pair (there is no login or profile API), theme and sidebar state are per-browser
 * preferences, and the feature list is the registry the navigation is built from. Nothing here
 * is a fake preference: the backend has no notification, profile or account settings.
 */
export function SettingsPage() {
  return (
    <div className="ws-page rise-in">
      <PageHeader title="Settings" description="Your identity, appearance and workspace preferences." />
      <div className="ws-settings">
        <IdentitySection />
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

function IdentitySection() {
  const { identity, openPrompt, clearIdentity } = useIdentity()
  return (
    <Section id="identity" icon="user" title="Identity" description="Who the API sees you as.">
      <div className="ws-row">
        <div className="ws-row-label">
          <strong>User ID</strong>
          <span>Sent as the X-User-Id header</span>
        </div>
        <CopyValue label="user ID" value={identity?.userId ?? null} />
      </div>
      <div className="ws-row">
        <div className="ws-row-label">
          <strong>Organization ID</strong>
          <span>Sent as the X-Org-Id header</span>
        </div>
        <CopyValue label="organization ID" value={identity?.orgId ?? null} />
      </div>
      <p className="ws-hint">
        This app has no login of its own. These two identifiers are sent with every request as the X-User-Id and
        X-Org-Id headers and are stored only in this browser; the backend decides what they may do.
      </p>
      <div className="page-actions">
        <button type="button" className="btn" onClick={openPrompt}>
          {identity ? 'Change identity' : 'Set identity'}
        </button>
        {identity && (
          <button type="button" className="btn btn-danger" onClick={clearIdentity}>
            <Icon name="logout" size={15} /> Sign out
          </button>
        )}
      </div>
    </Section>
  )
}

function CopyValue({ label, value }: { label: string; value: string | null }) {
  const [state, setState] = useState<'idle' | 'copied' | 'failed'>('idle')

  useEffect(() => {
    if (state === 'idle') return
    const timer = window.setTimeout(() => setState('idle'), 2000)
    return () => window.clearTimeout(timer)
  }, [state])

  if (!value) return <span className="text-faint">Not set</span>

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
    <span className="ws-copy">
      <code className="ws-code">{value}</code>
      <button type="button" className="btn btn-sm" onClick={copy} aria-label={`Copy ${label}`}>
        {state === 'copied' ? (
          <>
            <Icon name="check" size={14} /> Copied
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
          <span>From VITE_API_BASE_URL at build time</span>
        </div>
        <code className="ws-code">{API_BASE_URL}</code>
      </div>
      <div className="ws-row">
        <div className="ws-row-label">
          <strong>Stored in this browser</strong>
          <span>Identity headers, theme and sidebar preference — nothing else</span>
        </div>
      </div>
      <p className="ws-hint">
        WorkEasy360 keeps no account, profile or notification settings: the backend has no such APIs, so none
        are shown here.
      </p>
    </Section>
  )
}
