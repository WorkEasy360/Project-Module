/** V3: rules notify people inside Operations. */
export type AutomationActionType = 'NOTIFY'

export const AUTOMATION_ACTION_TYPES: AutomationActionType[] = ['NOTIFY']

export type AutomationRunStatus = 'SUCCEEDED' | 'FAILED'

/**
 * The trigger events a rule can start from, mirroring the backend's AutomationTriggerEvent after
 * the V3 clean-up. The single source for the trigger choice; never hand-typed elsewhere.
 */
export const AUTOMATION_TRIGGER_EVENTS = [
  'task.created',
  'task.assigned',
  'task.updated',
  'task.completed',
  'task.blocked',
  'project.created',
  'project.updated',
  'project.archived',
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

/** Plain-language label for a trigger, as the rule form and lists show it. */
export const TRIGGER_LABEL: Record<AutomationTriggerEvent, string> = {
  'task.created': 'A task is created',
  'task.assigned': 'A task is assigned',
  'task.updated': 'A task is changed',
  'task.completed': 'A task is completed',
  'task.blocked': 'A task is marked Blocked',
  'project.created': 'A project is created',
  'project.updated': 'A project is changed',
  'project.archived': 'A project is archived',
}

export function triggerLabel(event: string): string {
  return (TRIGGER_LABEL as Record<string, string>)[event] ?? event
}
