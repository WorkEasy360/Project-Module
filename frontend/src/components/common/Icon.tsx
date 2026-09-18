import type { SVGProps } from 'react'

/**
 * A small hand-authored, stroke-based icon set (no icon library dependency). Every icon shares
 * the same 20x20 viewBox / stroke weight so they line up regardless of which is used.
 */
export type IconName =
  | 'dashboard'
  | 'projects'
  | 'template'
  | 'overview'
  | 'tasks'
  | 'kanban'
  | 'calendar'
  | 'timeline'
  | 'gantt'
  | 'phase'
  | 'milestone'
  | 'list'
  | 'dependency'
  | 'risk'
  | 'issue'
  | 'decision'
  | 'comment'
  | 'activity'
  | 'health'
  | 'delayed'
  | 'report'
  | 'field'
  | 'automation'
  | 'ai'
  | 'member'
  | 'search'
  | 'menu'
  | 'close'
  | 'chevronLeft'
  | 'chevronRight'
  | 'chevronDown'
  | 'user'
  | 'plus'
  | 'warning'
  | 'refresh'
  | 'inbox'

const PATHS: Record<IconName, React.ReactNode> = {
  dashboard: (
    <>
      <rect x="3" y="3" width="6.5" height="6.5" rx="1.2" />
      <rect x="10.5" y="3" width="6.5" height="4" rx="1.2" />
      <rect x="10.5" y="9" width="6.5" height="8" rx="1.2" />
      <rect x="3" y="11.5" width="6.5" height="5.5" rx="1.2" />
    </>
  ),
  projects: (
    <>
      <rect x="3" y="4.5" width="14" height="11" rx="1.5" />
      <path d="M3 8h14" />
    </>
  ),
  template: (
    <>
      <rect x="3" y="3" width="14" height="14" rx="1.5" />
      <path d="M3 8h14M8 8v9" />
    </>
  ),
  overview: (
    <>
      <circle cx="10" cy="10" r="7" />
      <path d="M10 6v4l3 2" />
    </>
  ),
  tasks: (
    <>
      <path d="M4 6.5l1.5 1.5L8 5" />
      <path d="M4 12.5l1.5 1.5L8 11" />
      <path d="M11 7h6M11 13h6" />
    </>
  ),
  kanban: (
    <>
      <rect x="3" y="3.5" width="14" height="13" rx="1.5" />
      <path d="M8.3 3.5v13M13.6 3.5v13" />
    </>
  ),
  calendar: (
    <>
      <rect x="3" y="4" width="14" height="13" rx="1.5" />
      <path d="M3 8h14M7 2.5v3M13 2.5v3" />
    </>
  ),
  timeline: (
    <>
      <path d="M3 5h14M3 10h9M3 15h11" />
      <circle cx="16" cy="5" r="1.1" fill="currentColor" stroke="none" />
      <circle cx="13.5" cy="10" r="1.1" fill="currentColor" stroke="none" />
    </>
  ),
  gantt: (
    <>
      <path d="M3 4.5h6M9 9h8M3 13.5h10" />
    </>
  ),
  phase: (
    <>
      <path d="M4 4v12M4 4l10 4-10 4" />
    </>
  ),
  milestone: (
    <>
      <path d="M10 3l6 6-6 6-6-6z" />
    </>
  ),
  list: (
    <>
      <path d="M7 5h10M7 10h10M7 15h10" />
      <circle cx="3.5" cy="5" r="0.9" fill="currentColor" stroke="none" />
      <circle cx="3.5" cy="10" r="0.9" fill="currentColor" stroke="none" />
      <circle cx="3.5" cy="15" r="0.9" fill="currentColor" stroke="none" />
    </>
  ),
  dependency: (
    <>
      <circle cx="5.5" cy="14.5" r="2.2" />
      <circle cx="14.5" cy="5.5" r="2.2" />
      <path d="M7.2 12.8l5.6-5.6" />
    </>
  ),
  risk: (
    <>
      <path d="M10 3.5l7.2 12.5H2.8z" />
      <path d="M10 8.3v3.2" />
      <circle cx="10" cy="13.6" r="0.9" fill="currentColor" stroke="none" />
    </>
  ),
  issue: (
    <>
      <circle cx="10" cy="10" r="7" />
      <path d="M10 6.5v4" />
      <circle cx="10" cy="13.3" r="0.9" fill="currentColor" stroke="none" />
    </>
  ),
  decision: (
    <>
      <path d="M10 3l7 7-7 7-7-7z" />
      <path d="M8 10l1.4 1.4L12.5 8.2" />
    </>
  ),
  comment: (
    <>
      <path d="M3.5 4.5h13v9h-7L5 16v-2.5H3.5z" />
    </>
  ),
  activity: (
    <>
      <path d="M3 10h3.2l1.8-5 3 10 1.8-5H17" />
    </>
  ),
  health: (
    <>
      <path d="M10 16.5S3.5 12.6 3.5 8.1a3.4 3.4 0 016.5-1.4 3.4 3.4 0 016.5 1.4c0 4.5-6.5 8.4-6.5 8.4z" />
    </>
  ),
  delayed: (
    <>
      <circle cx="10" cy="10.5" r="6.5" />
      <path d="M10 7v3.5l2.4 1.4" />
      <path d="M7.5 2.5h5" />
    </>
  ),
  report: (
    <>
      <rect x="4" y="3" width="12" height="14" rx="1.3" />
      <path d="M7.5 12v2M10 9.5v4.5M12.5 7v7" />
    </>
  ),
  field: (
    <>
      <rect x="3.5" y="5.5" width="13" height="9" rx="1.3" />
      <path d="M3.5 9h13" />
    </>
  ),
  automation: (
    <>
      <path d="M4 10.5l3.2-6 2 4.2 2.2-3 1 4.8H16" />
      <circle cx="4" cy="10.5" r="1" fill="currentColor" stroke="none" />
      <circle cx="16" cy="10.5" r="1" fill="currentColor" stroke="none" />
    </>
  ),
  ai: (
    <>
      <path d="M10 2.5l1.4 3.6 3.6 1.4-3.6 1.4-1.4 3.6-1.4-3.6-3.6-1.4 3.6-1.4z" />
      <path d="M15.5 13l.7 1.8 1.8.7-1.8.7-.7 1.8-.7-1.8-1.8-.7 1.8-.7z" />
    </>
  ),
  member: (
    <>
      <circle cx="10" cy="7" r="3" />
      <path d="M3.8 17c.7-3.2 3-5 6.2-5s5.5 1.8 6.2 5" />
    </>
  ),
  search: (
    <>
      <circle cx="8.7" cy="8.7" r="5" />
      <path d="M16 16l-3.8-3.8" />
    </>
  ),
  menu: <path d="M3 5.5h14M3 10h14M3 14.5h14" />,
  close: <path d="M5 5l10 10M15 5L5 15" />,
  chevronLeft: <path d="M12 4.5l-5.5 5.5 5.5 5.5" />,
  chevronRight: <path d="M8 4.5l5.5 5.5-5.5 5.5" />,
  chevronDown: <path d="M4.5 7.5l5.5 5.5 5.5-5.5" />,
  user: (
    <>
      <circle cx="10" cy="7" r="3.3" />
      <path d="M4 17c0-3.3 2.7-5.5 6-5.5s6 2.2 6 5.5" />
    </>
  ),
  plus: <path d="M10 4v12M4 10h12" />,
  warning: (
    <>
      <path d="M10 3.5l7.2 12.5H2.8z" />
      <path d="M10 8.3v3.2" />
      <circle cx="10" cy="13.6" r="0.9" fill="currentColor" stroke="none" />
    </>
  ),
  refresh: (
    <>
      <path d="M16 10a6 6 0 10-1.8 4.3" />
      <path d="M16 5.5V10h-4.5" />
    </>
  ),
  inbox: (
    <>
      <path d="M3.5 10h4l1.3 2h2.4l1.3-2h4" />
      <rect x="3.5" y="4.5" width="13" height="11" rx="1.3" />
    </>
  ),
}

export function Icon({
  name,
  size = 18,
  ...rest
}: { name: IconName; size?: number } & SVGProps<SVGSVGElement>) {
  return (
    <svg
      width={size}
      height={size}
      viewBox="0 0 20 20"
      fill="none"
      stroke="currentColor"
      strokeWidth={1.6}
      strokeLinecap="round"
      strokeLinejoin="round"
      aria-hidden="true"
      focusable="false"
      {...rest}
    >
      {PATHS[name]}
    </svg>
  )
}
