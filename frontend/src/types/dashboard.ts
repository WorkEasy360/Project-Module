import type { ProjectPriority, ProjectStatus } from './project'

export interface Dashboard {
  activeProjectCount: number
  archivedProjectCount: number
  byStatus: Record<ProjectStatus, number>
  byPriority: Record<ProjectPriority, number>
}
