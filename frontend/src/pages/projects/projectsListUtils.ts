import type { ProjectSummary } from '../../types/project'

export type ProjectsView = 'card' | 'list'

export const PROJECTS_VIEW_STORAGE_KEY = 'projectmodule.projectsView'

export function readStoredProjectsView(): ProjectsView {
  try {
    const stored = localStorage.getItem(PROJECTS_VIEW_STORAGE_KEY)
    return stored === 'list' || stored === 'card' ? stored : 'card'
  } catch {
    return 'card'
  }
}

export function storeProjectsView(view: ProjectsView) {
  try {
    localStorage.setItem(PROJECTS_VIEW_STORAGE_KEY, view)
  } catch {
    // Storage may be unavailable (private mode, quota); the toggle still works for this session.
  }
}

/**
 * Case-insensitive name match over an already-loaded page. `GET /projects` only supports
 * page/size/sort, so this is the only filtering available and it never reaches other pages.
 */
export function filterProjectsByName(items: ProjectSummary[], query: string): ProjectSummary[] {
  const q = query.trim().toLowerCase()
  if (!q) return items
  return items.filter((p) => p.name.toLowerCase().includes(q))
}
