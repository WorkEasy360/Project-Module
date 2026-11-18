import { humanizeToken } from '../../utils/format'

export type BadgeTone = 'neutral' | 'primary' | 'success' | 'warning' | 'danger' | 'info'

export function Badge({ tone = 'neutral', children }: { tone?: BadgeTone; children: React.ReactNode }) {
  return <span className={`badge badge-${tone}`}>{children}</span>
}

const TASK_STATUS_TONE: Record<string, BadgeTone> = {
  TODO: 'neutral',
  IN_PROGRESS: 'info',
  BLOCKED: 'danger',
  COMPLETED: 'success',
}

const PRIORITY_TONE: Record<string, BadgeTone> = {
  LOW: 'neutral',
  MEDIUM: 'info',
  HIGH: 'warning',
}

const PROJECT_STATUS_TONE: Record<string, BadgeTone> = {
  ACTIVE: 'primary',
  ON_HOLD: 'warning',
  COMPLETED: 'success',
}

const RUN_STATUS_TONE: Record<string, BadgeTone> = {
  SUCCEEDED: 'success',
  FAILED: 'danger',
}

function StatusBadge({ value, tones, label }: { value: string; tones: Record<string, BadgeTone>; label?: string }) {
  return <Badge tone={tones[value] ?? 'neutral'}>{label ?? humanizeToken(value)}</Badge>
}

/** The V3 name of a task status: a finished task reads "Done". */
export function taskStatusLabel(status: string): string {
  return status === 'COMPLETED' ? 'Done' : humanizeToken(status)
}

export const TaskStatusBadge = (p: { status: string }) => (
  <StatusBadge value={p.status} tones={TASK_STATUS_TONE} label={taskStatusLabel(p.status)} />
)
export const PriorityBadge = (p: { priority: string }) => (
  <StatusBadge value={p.priority} tones={PRIORITY_TONE} />
)
export const ProjectStatusBadge = (p: { status: string }) => (
  <StatusBadge value={p.status} tones={PROJECT_STATUS_TONE} />
)
export const RunStatusBadge = (p: { status: string }) => (
  <StatusBadge value={p.status} tones={RUN_STATUS_TONE} />
)
export const ArchivedBadge = () => <Badge tone="neutral">Archived</Badge>
