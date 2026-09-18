export type RecommendationType =
  | 'TASK_BREAKDOWN'
  | 'TASK_IMPROVEMENT'
  | 'PROJECT_SUMMARY'
  | 'RISK_ANALYSIS'
  | 'WHAT_IF'

export const RECOMMENDATION_TYPES: RecommendationType[] = [
  'TASK_BREAKDOWN',
  'TASK_IMPROVEMENT',
  'PROJECT_SUMMARY',
  'RISK_ANALYSIS',
  'WHAT_IF',
]

export const RECOMMENDATION_TYPE_LABELS: Record<RecommendationType, string> = {
  TASK_BREAKDOWN: 'Task Breakdown',
  TASK_IMPROVEMENT: 'Task Improvement',
  PROJECT_SUMMARY: 'Project Copilot Summary',
  RISK_ANALYSIS: 'Risk Analysis',
  WHAT_IF: 'What-if Analysis',
}

/** Types that require picking a task (resourceId) as context. */
export const RECOMMENDATION_TYPES_REQUIRING_RESOURCE: RecommendationType[] = [
  'TASK_BREAKDOWN',
  'TASK_IMPROVEMENT',
]

/** Types that require a free-text question. */
export const RECOMMENDATION_TYPES_REQUIRING_QUESTION: RecommendationType[] = ['WHAT_IF']

export type RecommendationStatus = 'PENDING' | 'ACCEPTED' | 'REJECTED' | 'EXPIRED'

export interface AIRecommendation {
  id: string
  projectId: string
  type: RecommendationType
  resourceId: string | null
  question: string | null
  title: string | null
  rationale: string | null
  payload: string | null
  status: RecommendationStatus
  requestedBy: string
  respondedBy: string | null
  respondedAt: string | null
  createdAt: string
  updatedAt: string
  version: number
}

export interface RequestRecommendationRequest {
  type: RecommendationType
  resourceId?: string | null
  question?: string | null
}
