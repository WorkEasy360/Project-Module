import { createContext, useContext } from 'react'
import type { Project, ProjectMember } from '../types/project'
import type { ProjectPermission } from '../utils/permissions'

export interface ProjectWorkspaceValue {
  project: Project
  reloadProject: () => void
  currentMember: ProjectMember | null
  can: (permission: ProjectPermission) => boolean
}

export const ProjectWorkspaceContext = createContext<ProjectWorkspaceValue | null>(null)

export function useProjectWorkspace(): ProjectWorkspaceValue {
  const ctx = useContext(ProjectWorkspaceContext)
  if (!ctx) throw new Error('useProjectWorkspace must be used within a ProjectWorkspace route')
  return ctx
}

/** Like useProjectWorkspace, but returns null outside a project (for screens shared with the workspace). */
export function useOptionalProjectWorkspace(): ProjectWorkspaceValue | null {
  return useContext(ProjectWorkspaceContext)
}
