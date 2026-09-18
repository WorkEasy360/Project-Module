import { humanizeToken } from '../../utils/format'

export type BadgeTone = 'neutral' | 'primary' | 'success' | 'warning' | 'danger' | 'info'

export function Badge({ tone = 'neutral', children }: { tone?: BadgeTone; children: React.ReactNode }) {
  return <span className={`badge badge-${tone}`}>{children}</span>
}

const TASK_STATUS_TONE: Record<string, BadgeTone> = {
  TODO: 'neutral',
  BLOCKED: 'danger',
  OVERDUE: 'warning',
  COMPLETED: 'success',
}

const MILESTONE_STATUS_TONE: Record<string, BadgeTone> = {
  PENDING: 'neutral',
  AT_RISK: 'warning',
  COMPLETED: 'success',
}

const RISK_STATUS_TONE: Record<string, BadgeTone> = {
  OPEN: 'warning',
  RESOLVED: 'success',
}

const PRIORITY_TONE: Record<string, BadgeTone> = {
  LOW: 'neutral',
  MEDIUM: 'info',
  HIGH: 'warning',
  CRITICAL: 'danger',
}

const PROJECT_STATUS_TONE: Record<string, BadgeTone> = {
  PLANNING: 'neutral',
  ACTIVE: 'primary',
  ON_HOLD: 'warning',
  COMPLETED: 'success',
  CANCELLED: 'danger',
}

const RUN_STATUS_TONE: Record<string, BadgeTone> = {
  SUCCEEDED: 'success',
  FAILED: 'danger',
}

const RECOMMENDATION_STATUS_TONE: Record<string, BadgeTone> = {
  PENDING: 'neutral',
  ACCEPTED: 'success',
  REJECTED: 'danger',
  EXPIRED: 'warning',
}

function StatusBadge({ value, tones }: { value: string; tones: Record<string, BadgeTone> }) {
  return <Badge tone={tones[value] ?? 'neutral'}>{humanizeToken(value)}</Badge>
}

export const TaskStatusBadge = (p: { status: string }) => (
  <StatusBadge value={p.status} tones={TASK_STATUS_TONE} />
)
export const MilestoneStatusBadge = (p: { status: string }) => (
  <StatusBadge value={p.status} tones={MILESTONE_STATUS_TONE} />
)
export const RiskStatusBadge = (p: { status: string }) => (
  <StatusBadge value={p.status} tones={RISK_STATUS_TONE} />
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
export const RecommendationStatusBadge = (p: { status: string }) => (
  <StatusBadge value={p.status} tones={RECOMMENDATION_STATUS_TONE} />
)
export const ArchivedBadge = () => <Badge tone="neutral">Archived</Badge>
