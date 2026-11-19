import type { IconName } from '../common/Icon'

/** One entry of the sidebar: a link, or a group that opens to show its pages. */
export interface SideEntry {
  label: string
  icon: IconName
  to?: string
  children?: { label: string; to: string }[]
}

/**
 * The V3 main navigation, top to bottom. Settings sits below a divider. Anything else a page needs
 * lives in its tabs and filters, not in more sidebar items.
 */
export const SIDEBAR: SideEntry[] = [
  { label: 'Home', icon: 'home', to: '/dashboard' },
  {
    label: 'Projects',
    icon: 'folder',
    children: [
      { label: 'Active Projects', to: '/projects' },
      { label: 'Archived Projects', to: '/projects/archived' },
    ],
  },
  {
    label: 'Tasks',
    icon: 'tasks',
    children: [
      { label: 'My Tasks', to: '/tasks' },
      { label: 'All Tasks', to: '/tasks/all' },
    ],
  },
  {
    label: 'Team',
    icon: 'team',
    children: [
      { label: 'Members', to: '/team' },
      { label: 'Teams', to: '/team/teams' },
      { label: 'Invitations', to: '/team/invitations' },
    ],
  },
  {
    label: 'Automation',
    icon: 'bolt',
    children: [
      { label: 'Rules', to: '/automations' },
      { label: 'Run History', to: '/automations/runs' },
    ],
  },
  {
    label: 'Reports',
    icon: 'barChart',
    children: [
      { label: 'Task Progress', to: '/reports' },
      { label: 'Project Progress', to: '/reports/projects' },
      { label: 'Team Workload', to: '/reports/workload' },
    ],
  },
  {
    label: 'Helpdesk',
    icon: 'headset',
    children: [
      { label: 'Raise Ticket', to: '/helpdesk' },
      { label: 'My Tickets', to: '/helpdesk/tickets' },
      { label: 'Help Articles', to: '/helpdesk/articles' },
    ],
  },
]

export const SIDEBAR_FOOTER: SideEntry[] = [
  {
    label: 'Settings',
    icon: 'settings',
    children: [
      { label: 'Workspace & Access', to: '/settings' },
      { label: 'Fields & Tags', to: '/settings/fields' },
      { label: 'Notifications', to: '/settings/notifications' },
      { label: 'Integrations', to: '/settings/integrations' },
      { label: 'Subscription & Usage', to: '/settings/subscription' },
    ],
  },
]

/** The first page of each section, which its own deeper pages do not count as. */
const SECTION_ROOTS = ['/projects', '/tasks', '/team', '/automations', '/reports', '/helpdesk', '/settings']

/**
 * Whether a sidebar link is the current page. A section's first link is active only on that exact
 * page, except that a project's own pages count as "Active Projects".
 */
export function isLinkActive(to: string, pathname: string): boolean {
  if (to === '/projects') {
    return pathname === '/projects' || (pathname.startsWith('/projects/') && !pathname.startsWith('/projects/archived'))
  }
  if (SECTION_ROOTS.includes(to)) return pathname === to
  return pathname === to || pathname.startsWith(`${to}/`)
}

/** V3 Project Details: Overview, Tasks (List / Board over the same records), Members and Activity. */
export function projectTabs(projectId: string): { to: string; label: string; icon: IconName; also?: string[] }[] {
  const base = `/projects/${projectId}`
  return [
    { to: `${base}/overview`, label: 'Overview', icon: 'home' },
    { to: `${base}/tasks`, label: 'Tasks', icon: 'tasks', also: [`${base}/kanban`] },
    { to: `${base}/members`, label: 'Members', icon: 'team' },
    { to: `${base}/activity`, label: 'Activity', icon: 'clock' },
  ]
}
