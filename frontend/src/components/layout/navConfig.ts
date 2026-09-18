import { FEATURES, coreFeatures, type FeatureDefinition } from '../../features/registry'
import type { IconName } from '../common/Icon'

export interface NavItem {
  to: string
  label: string
  icon: IconName
  /** Present for feature-backed items; absent for the synthetic "More" entry. */
  feature?: FeatureDefinition
}

/** The "More" entry that opens the Advanced Features hub — the only nav item not in the registry. */
export const MORE_ITEM = { label: 'More', icon: 'inbox' as IconName, route: 'more' }

/** Workspace nav is direct links only — every workspace feature is CORE, so there is no "More". */
export function workspaceNav(): NavItem[] {
  return coreFeatures('workspace').map((f) => ({ to: f.route, label: f.name, icon: f.icon, feature: f }))
}

export function projectNav(projectId: string): NavItem[] {
  return [
    ...coreFeatures('project').map((f) => ({
      to: `/projects/${projectId}/${f.route}`,
      label: f.name,
      icon: f.icon,
      feature: f,
    })),
    { to: `/projects/${projectId}/${MORE_ITEM.route}`, label: MORE_ITEM.label, icon: MORE_ITEM.icon },
  ]
}

/** Every known route leaf → plain-language label, for the header breadcrumb. Derived, not hand-typed. */
export const ROUTE_LABELS: Record<string, string> = Object.fromEntries([
  ...FEATURES.map((f) => [f.route.replace(/^\//, ''), f.name] as const),
  ['more', 'More'],
  ['runs', 'Run History'],
])
