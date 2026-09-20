import type { IconName } from '../components/common/Icon'
import type { ProjectPermission } from '../utils/permissions'

/**
 * The single source of truth for what this product can do and where it lives in the UI.
 *
 * Sidebar, project tabs, the mobile tab bar, the "More / Advanced Features" hub and the header
 * breadcrumb are all derived from this list. Adding a feature (or a future module such as time
 * tracking) means adding ONE definition here — not touching navigation code.
 *
 * `route` values are exactly the paths registered in App.tsx; nothing here invents a route.
 */

export type FeatureScope = 'workspace' | 'project'

/** CORE features are always visible; ADVANCED ones are discovered through "More". */
export type FeatureTier = 'CORE' | 'ADVANCED'

export type FeatureCategory =
  | 'Daily work'
  | 'Advanced planning'
  | 'Project control'
  | 'Productivity'
  | 'Automation & intelligence'

/**
 * AVAILABLE      – backed by a real API and usable now.
 * BETA           – usable, but the backend marks the capability as partial (e.g. AI needs a provider).
 * COMING_SOON    – the backend has no API for it yet; the UI shows an honest placeholder only.
 */
export type ReleaseState = 'AVAILABLE' | 'BETA' | 'COMING_SOON'

export interface FeatureDefinition {
  id: string
  /** Plain-language name shown to users. */
  name: string
  /** One sentence a first-time user understands. */
  description: string
  /** Technical name, shown as a subtitle when it differs from `name`. */
  technicalName?: string
  scope: FeatureScope
  tier: FeatureTier
  category: FeatureCategory
  /** Absolute path for workspace features; path segment under /projects/:id for project ones. */
  route: string
  icon: IconName
  releaseState: ReleaseState
  /** Permission needed to *use* the feature's primary actions (viewing may need less). */
  requiredPermission?: ProjectPermission
  /** Ordering within navigation and hub groups. */
  order: number
}

export const FEATURES: FeatureDefinition[] = [
  // ---------- Workspace: core ----------
  {
    id: 'home',
    name: 'Home',
    description: 'A summary of your projects and what needs attention.',
    scope: 'workspace',
    tier: 'CORE',
    category: 'Daily work',
    route: '/dashboard',
    icon: 'dashboard',
    releaseState: 'AVAILABLE',
    order: 10,
  },
  {
    id: 'projects',
    name: 'Projects',
    description: 'All the projects in your organization.',
    scope: 'workspace',
    tier: 'CORE',
    category: 'Daily work',
    route: '/projects',
    icon: 'projects',
    releaseState: 'AVAILABLE',
    order: 20,
  },

  {
    id: 'my-tasks',
    name: 'My Tasks',
    description: 'Tasks across your projects, with the ones assigned to you first.',
    scope: 'workspace',
    tier: 'CORE',
    category: 'Daily work',
    route: '/tasks',
    icon: 'tasks',
    releaseState: 'AVAILABLE',
    order: 22,
  },
  {
    id: 'workspace-calendar',
    name: 'Calendar',
    description: 'Every task and milestone due date across your projects, by month.',
    scope: 'workspace',
    tier: 'CORE',
    category: 'Daily work',
    route: '/calendar',
    icon: 'calendar',
    releaseState: 'AVAILABLE',
    order: 24,
  },
  {
    id: 'team',
    name: 'My Team',
    description: 'Who is on which project, and in what role.',
    scope: 'workspace',
    tier: 'CORE',
    category: 'Daily work',
    route: '/team',
    icon: 'team',
    releaseState: 'AVAILABLE',
    order: 26,
  },
  {
    id: 'workspace-reports',
    name: 'Reports',
    description: 'Summary reports for each of your projects, side by side.',
    scope: 'workspace',
    tier: 'CORE',
    category: 'Project control',
    route: '/reports',
    icon: 'report',
    releaseState: 'AVAILABLE',
    order: 28,
  },
  {
    id: 'templates',
    name: 'Templates',
    description: 'Start new projects from a reusable starting point.',
    technicalName: 'Project Templates',
    scope: 'workspace',
    tier: 'CORE',
    category: 'Productivity',
    route: '/templates',
    icon: 'template',
    releaseState: 'AVAILABLE',
    order: 30,
  },
  {
    id: 'workspace-automations',
    name: 'Automations',
    description: 'All automation rules across your projects and their recent runs.',
    scope: 'workspace',
    tier: 'CORE',
    category: 'Automation & intelligence',
    route: '/automations',
    icon: 'automation',
    releaseState: 'AVAILABLE',
    order: 32,
  },
  {
    id: 'settings',
    name: 'Settings',
    description: 'Your identity, appearance and workspace preferences.',
    scope: 'workspace',
    tier: 'CORE',
    category: 'Productivity',
    route: '/settings',
    icon: 'settings',
    releaseState: 'AVAILABLE',
    order: 34,
  },

  // ---------- Project: core ----------
  {
    id: 'overview',
    name: 'Overview',
    description: 'Where the project stands right now.',
    scope: 'project',
    tier: 'CORE',
    category: 'Daily work',
    route: 'overview',
    icon: 'overview',
    releaseState: 'AVAILABLE',
    order: 10,
  },
  {
    id: 'tasks',
    name: 'Tasks',
    description: 'The work to be done, with filters and bulk actions.',
    scope: 'project',
    tier: 'CORE',
    category: 'Daily work',
    route: 'tasks',
    icon: 'tasks',
    releaseState: 'AVAILABLE',
    order: 20,
  },
  {
    id: 'kanban',
    name: 'Board',
    description: 'Drag tasks between columns as they progress.',
    technicalName: 'Kanban',
    scope: 'project',
    tier: 'CORE',
    category: 'Daily work',
    route: 'kanban',
    icon: 'kanban',
    releaseState: 'AVAILABLE',
    order: 30,
  },
  {
    id: 'calendar',
    name: 'Calendar',
    description: 'Tasks and milestones by date.',
    scope: 'project',
    tier: 'CORE',
    category: 'Daily work',
    route: 'calendar',
    icon: 'calendar',
    releaseState: 'AVAILABLE',
    order: 40,
  },
  {
    id: 'members',
    name: 'Team',
    description: 'Who is on this project and what they can do.',
    technicalName: 'Members',
    scope: 'project',
    tier: 'CORE',
    category: 'Daily work',
    route: 'members',
    icon: 'member',
    releaseState: 'AVAILABLE',
    requiredPermission: 'MANAGE_MEMBERS',
    order: 50,
  },

  // ---------- Project: advanced planning ----------
  {
    id: 'timeline',
    name: 'Timeline',
    description: 'Phases, milestones and tasks laid out in order.',
    scope: 'project',
    tier: 'ADVANCED',
    category: 'Advanced planning',
    route: 'timeline',
    icon: 'timeline',
    releaseState: 'AVAILABLE',
    order: 10,
  },
  {
    id: 'gantt',
    name: 'Gantt',
    description: 'Visual project scheduling with date bars.',
    scope: 'project',
    tier: 'ADVANCED',
    category: 'Advanced planning',
    route: 'gantt',
    icon: 'gantt',
    releaseState: 'AVAILABLE',
    order: 20,
  },
  {
    id: 'phases',
    name: 'Phases',
    description: 'Break the project into stages with start and end dates.',
    scope: 'project',
    tier: 'ADVANCED',
    category: 'Advanced planning',
    route: 'phases',
    icon: 'phase',
    releaseState: 'AVAILABLE',
    requiredPermission: 'EDIT_PROJECT',
    order: 30,
  },
  {
    id: 'milestones',
    name: 'Milestones',
    description: 'Key dates the project must hit.',
    scope: 'project',
    tier: 'ADVANCED',
    category: 'Advanced planning',
    route: 'milestones',
    icon: 'milestone',
    releaseState: 'AVAILABLE',
    requiredPermission: 'EDIT_PROJECT',
    order: 40,
  },
  {
    id: 'task-lists',
    name: 'Task Lists',
    description: 'Group tasks into named lists.',
    scope: 'project',
    tier: 'ADVANCED',
    category: 'Advanced planning',
    route: 'task-lists',
    icon: 'list',
    releaseState: 'AVAILABLE',
    requiredPermission: 'EDIT_PROJECT',
    order: 50,
  },
  {
    id: 'dependencies',
    name: 'Task Relationships',
    description: 'See which tasks depend on other tasks, and what is blocked or broken.',
    technicalName: 'Dependency Analysis',
    scope: 'project',
    tier: 'ADVANCED',
    category: 'Advanced planning',
    route: 'dependencies',
    icon: 'dependency',
    releaseState: 'AVAILABLE',
    order: 60,
  },

  // ---------- Project: control ----------
  {
    id: 'health',
    name: 'Project Health',
    description: 'See what may need your attention.',
    scope: 'project',
    tier: 'ADVANCED',
    category: 'Project control',
    route: 'health',
    icon: 'health',
    releaseState: 'AVAILABLE',
    order: 10,
  },
  {
    id: 'delayed',
    name: 'Delays',
    description: 'Everything that is past its due date.',
    technicalName: 'Delay Detection',
    scope: 'project',
    tier: 'ADVANCED',
    category: 'Project control',
    route: 'delayed',
    icon: 'delayed',
    releaseState: 'AVAILABLE',
    order: 20,
  },
  {
    id: 'risks',
    name: 'Risks',
    description: 'Things that could go wrong, and whether they are resolved.',
    scope: 'project',
    tier: 'ADVANCED',
    category: 'Project control',
    route: 'risks',
    icon: 'risk',
    releaseState: 'AVAILABLE',
    requiredPermission: 'EDIT_PROJECT',
    order: 30,
  },
  {
    id: 'issues',
    name: 'Issues',
    description: 'Problems that have already happened.',
    scope: 'project',
    tier: 'ADVANCED',
    category: 'Project control',
    route: 'issues',
    icon: 'issue',
    releaseState: 'AVAILABLE',
    requiredPermission: 'EDIT_PROJECT',
    order: 40,
  },
  {
    id: 'decisions',
    name: 'Decisions',
    description: 'A record of what was decided and by whom.',
    scope: 'project',
    tier: 'ADVANCED',
    category: 'Project control',
    route: 'decisions',
    icon: 'decision',
    releaseState: 'AVAILABLE',
    requiredPermission: 'EDIT_PROJECT',
    order: 50,
  },
  {
    id: 'reports',
    name: 'Reports',
    description: 'Counts of tasks, risks, issues and decisions at a glance.',
    scope: 'project',
    tier: 'ADVANCED',
    category: 'Project control',
    route: 'reports',
    icon: 'report',
    releaseState: 'AVAILABLE',
    order: 60,
  },
  {
    id: 'comments',
    name: 'Discussion',
    description: 'Comments shared with the whole project team.',
    technicalName: 'Comments',
    scope: 'project',
    tier: 'ADVANCED',
    category: 'Project control',
    route: 'comments',
    icon: 'comment',
    releaseState: 'AVAILABLE',
    order: 70,
  },
  {
    id: 'activity',
    name: 'Activity',
    description: 'A history of changes made to the project.',
    scope: 'project',
    tier: 'ADVANCED',
    category: 'Project control',
    route: 'activity',
    icon: 'activity',
    releaseState: 'COMING_SOON',
    order: 80,
  },

  // ---------- Project: productivity ----------
  {
    id: 'search',
    name: 'Search',
    description: 'Find tasks, risks, issues and decisions in this project.',
    scope: 'project',
    tier: 'ADVANCED',
    category: 'Productivity',
    route: 'search',
    icon: 'search',
    releaseState: 'AVAILABLE',
    order: 10,
  },
  {
    id: 'custom-fields',
    name: 'Custom Fields',
    description: 'Add your own information to the project.',
    scope: 'project',
    tier: 'ADVANCED',
    category: 'Productivity',
    route: 'custom-fields',
    icon: 'field',
    releaseState: 'AVAILABLE',
    requiredPermission: 'EDIT_PROJECT',
    order: 20,
  },

  // ---------- Project: automation & intelligence ----------
  {
    id: 'automations',
    name: 'Automation',
    description: 'Let the system handle repetitive work when things change.',
    scope: 'project',
    tier: 'ADVANCED',
    category: 'Automation & intelligence',
    route: 'automations',
    icon: 'automation',
    releaseState: 'AVAILABLE',
    requiredPermission: 'EDIT_PROJECT',
    order: 10,
  },
  {
    id: 'ai',
    name: 'AI Suggestions',
    description: 'Ask for suggestions about tasks, risks and the project as a whole.',
    technicalName: 'AI Recommendations',
    scope: 'project',
    tier: 'ADVANCED',
    category: 'Automation & intelligence',
    route: 'ai',
    icon: 'ai',
    releaseState: 'BETA',
    requiredPermission: 'EDIT_PROJECT',
    order: 20,
  },
]

export const CATEGORY_ORDER: FeatureCategory[] = [
  'Daily work',
  'Advanced planning',
  'Project control',
  'Productivity',
  'Automation & intelligence',
]

const byOrder = (a: FeatureDefinition, b: FeatureDefinition) => a.order - b.order

export function coreFeatures(scope: FeatureScope): FeatureDefinition[] {
  return FEATURES.filter((f) => f.scope === scope && f.tier === 'CORE').sort(byOrder)
}

export function advancedFeatures(scope: FeatureScope): FeatureDefinition[] {
  return FEATURES.filter((f) => f.scope === scope && f.tier === 'ADVANCED').sort(byOrder)
}

export function groupByCategory(features: FeatureDefinition[]): [FeatureCategory, FeatureDefinition[]][] {
  return CATEGORY_ORDER.map((category) => [category, features.filter((f) => f.category === category)] as const)
    .filter(([, items]) => items.length > 0)
    .map(([category, items]) => [category, [...items].sort(byOrder)])
}

export function featureHref(feature: FeatureDefinition, projectId?: string): string {
  return feature.scope === 'project' ? `/projects/${projectId}/${feature.route}` : feature.route
}

export function findFeatureByRoute(scope: FeatureScope, routeSegment: string): FeatureDefinition | undefined {
  return FEATURES.find((f) => f.scope === scope && f.route === routeSegment)
}
