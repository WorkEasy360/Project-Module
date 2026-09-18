export type ProjectStatus = 'PLANNING' | 'ACTIVE' | 'ON_HOLD' | 'COMPLETED' | 'CANCELLED'

export const PROJECT_STATUSES: ProjectStatus[] = [
  'PLANNING',
  'ACTIVE',
  'ON_HOLD',
  'COMPLETED',
  'CANCELLED',
]

export type ProjectPriority = 'LOW' | 'MEDIUM' | 'HIGH' | 'CRITICAL'

export const PROJECT_PRIORITIES: ProjectPriority[] = ['LOW', 'MEDIUM', 'HIGH', 'CRITICAL']

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
}

export interface ProjectSummary {
  id: string
  name: string
  status: ProjectStatus
  priority: ProjectPriority
  ownerId: string
  updatedAt: string
}

export interface CreateProjectRequest {
  name: string
  description?: string | null
  priority: ProjectPriority
  startDate?: string | null
  targetEndDate?: string | null
}

export interface UpdateProjectRequest {
  name?: string | null
  description?: string | null
  status?: ProjectStatus | null
  priority?: ProjectPriority | null
  startDate?: string | null
  targetEndDate?: string | null
  version: number
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
