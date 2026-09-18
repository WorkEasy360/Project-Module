import type { ProjectRole } from '../types/project'

export type ProjectPermission = 'VIEW_PROJECT' | 'EDIT_PROJECT' | 'ARCHIVE_PROJECT' | 'MANAGE_MEMBERS'

/**
 * Mirrors ProjectAuthorizationServiceImpl's in-code role -> permission matrix, for UX only (to
 * hide/disable actions a user clearly cannot perform). This is NOT a security boundary — the
 * backend re-checks every request and remains the sole authority; a hidden button here never
 * replaces the 403 the server would return.
 */
const ROLE_PERMISSIONS: Record<ProjectRole, ProjectPermission[]> = {
  VIEWER: ['VIEW_PROJECT'],
  MEMBER: ['VIEW_PROJECT'],
  MANAGER: ['VIEW_PROJECT', 'EDIT_PROJECT', 'MANAGE_MEMBERS'],
  OWNER: ['VIEW_PROJECT', 'EDIT_PROJECT', 'MANAGE_MEMBERS', 'ARCHIVE_PROJECT'],
}

export function permissionsForRole(role: ProjectRole | null): Set<ProjectPermission> {
  if (!role) return new Set()
  return new Set(ROLE_PERMISSIONS[role])
}

export function hasPermission(role: ProjectRole | null, permission: ProjectPermission): boolean {
  return permissionsForRole(role).has(permission)
}
