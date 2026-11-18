/** V3 project statuses: Active, On Hold and Completed; Archived is the separate archived flag. */
export type ProjectStatus = 'ACTIVE' | 'ON_HOLD' | 'COMPLETED'

export const PROJECT_STATUSES: ProjectStatus[] = ['ACTIVE', 'ON_HOLD', 'COMPLETED']

export type ProjectPriority = 'LOW' | 'MEDIUM' | 'HIGH'

export const PROJECT_PRIORITIES: ProjectPriority[] = ['LOW', 'MEDIUM', 'HIGH']

export type ProjectRole = 'OWNER' | 'MANAGER' | 'MEMBER' | 'VIEWER'

export const PROJECT_ROLES: ProjectRole[] = ['OWNER', 'MANAGER', 'MEMBER', 'VIEWER']

/** Assignable roles when adding/changing a member — OWNER is never assignable via this API. */
export const ASSIGNABLE_PROJECT_ROLES: ProjectRole[] = ['MANAGER', 'MEMBER', 'VIEWER']

export interface Project {
  id: string
  organizationId: string
  name: string
  description: string | null
  status: ProjectStatus
  priority: ProjectPriority
  startDate: string | null
  targetEndDate: string | null
  ownerId: string
  archived: boolean
  archivedAt: string | null
  archivedBy: string | null
  createdAt: string
  updatedAt: string
  version: number
  /** Who the project is for. */
  customerContact?: string | null
  tags?: string[]
}

export interface ProjectSummary {
  id: string
  name: string
  status: ProjectStatus
  priority: ProjectPriority
  ownerId: string
  updatedAt: string
  description?: string | null
  startDate?: string | null
  targetEndDate?: string | null
  customerContact?: string | null
  tags?: string[]
  archived?: boolean
  archivedAt?: string | null
  createdAt?: string
  /** Live tasks, and how many of them are completed. */
  taskCount?: number
  completedTaskCount?: number
}

export interface CreateProjectRequest {
  name: string
  description?: string | null
  priority: ProjectPriority
  startDate?: string | null
  targetEndDate?: string | null
  customerContact?: string | null
  tags?: string[]
}

export interface UpdateProjectRequest {
  name?: string | null
  description?: string | null
  status?: ProjectStatus | null
  priority?: ProjectPriority | null
  startDate?: string | null
  targetEndDate?: string | null
  version: number
  customerContact?: string | null
  tags?: string[]
}

export interface ProjectMember {
  id: string
  projectId: string
  userId: string
  role: ProjectRole
  joinedAt: string
  version: number
}

export interface AddMemberRequest {
  userId: string
  role: ProjectRole
}

export interface ChangeMemberRoleRequest {
  role: ProjectRole
}
