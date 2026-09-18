export type AutomationActionType = 'NOTIFY' | 'CHAT_MESSAGE'

export const AUTOMATION_ACTION_TYPES: AutomationActionType[] = ['NOTIFY', 'CHAT_MESSAGE']

export type AutomationRunStatus = 'SUCCEEDED' | 'FAILED'

/**
 * The canonical trigger-event catalogue, mirroring
 * com.projectmodule.automation.domain.AutomationTriggerEvent exactly (19 values). This is the
 * single source of truth for the trigger dropdown — never hand-typed elsewhere in the UI.
 */
export const AUTOMATION_TRIGGER_EVENTS = [
  'project.created',
  'project.updated',
  'project.archived',
  'phase.created',
  'phase.updated',
  'milestone.created',
  'milestone.completed',
  'milestone.at_risk',
  'task.created',
  'task.updated',
  'task.assigned',
  'task.completed',
  'task.overdue',
  'task.blocked',
  'dependency.created',
  'dependency.broken',
  'risk.created',
  'risk.updated',
  'risk.resolved',
] as const

export type AutomationTriggerEvent = (typeof AUTOMATION_TRIGGER_EVENTS)[number]

export interface ProjectAutomation {
  id: string
  projectId: string
  name: string
  description: string | null
  triggerEvent: string
  actionType: AutomationActionType
  actionRecipientId: string | null
  actionChannelReference: string | null
  actionMessage: string
  enabled: boolean
  archived: boolean
  createdAt: string
  updatedAt: string
  version: number
}

export interface CreateAutomationRequest {
  name: string
  description?: string | null
  triggerEvent: string
  actionType: AutomationActionType
  actionRecipientId?: string | null
  actionChannelReference?: string | null
  actionMessage: string
}

export interface UpdateAutomationRequest {
  name?: string | null
  description?: string | null
  actionMessage?: string | null
  enabled?: boolean | null
  version: number
}

export interface AutomationRun {
  id: string
  automationId: string
  projectId: string
  triggerEvent: string
  status: AutomationRunStatus
  errorMessage: string | null
  executedAt: string
}
