import { useCallback, useEffect, useRef, useState, type DependencyList } from 'react'
import { projectsApi } from '../api/projects'
import { toUserMessage } from '../api/errorMessage'
import { useAsyncData, type AsyncDataState } from './useAsyncData'
import type { PageResponse } from '../types/common'
import type { ProjectSummary } from '../types/project'

/**
 * Cross-project ("workspace") views. The backend has no cross-project task/member/calendar
 * endpoints — every such API is per project — so workspace pages load one page of projects and
 * then call the per-project API for each. `WORKSPACE_PAGE_SIZE` bounds that fan-out; pages must
 * say so when an organisation has more projects than fit on that page.
 */
export const WORKSPACE_PAGE_SIZE = 50

export function useAllProjects(sort = 'updatedAt,DESC'): AsyncDataState<PageResponse<ProjectSummary>> {
  return useAsyncData(() => projectsApi.list(0, WORKSPACE_PAGE_SIZE, sort), [sort])
}

export interface PerProjectResult<T> {
  /** Loaded value per project id; `null` while loading or when that project's call failed. */
  byProject: Record<string, T | null>
  /** User-facing message per project id whose call failed. */
  errors: Record<string, string>
  loading: boolean
  reload: () => void
}

/**
 * Runs `loader(projectId)` for every project in parallel (Promise.allSettled — one failing
 * project never hides the others) and keeps the results keyed by project id.
 */
export function usePerProject<T>(
  projects: ProjectSummary[] | null,
  loader: (projectId: string) => Promise<T>,
  deps: DependencyList = [],
): PerProjectResult<T> {
  const [byProject, setByProject] = useState<Record<string, T | null>>({})
  const [errors, setErrors] = useState<Record<string, string>>({})
  const [loading, setLoading] = useState(true)
  const [token, setToken] = useState(0)
  const loaderRef = useRef(loader)
  useEffect(() => {
    loaderRef.current = loader
  })

  // `null` (not loaded yet) and `[]` (no projects) must be distinguishable, or an empty
  // organisation would never leave the loading state.
  const key = projects ? `loaded:${projects.map((p) => p.id).join(',')}` : null

  useEffect(() => {
    if (!projects) return
    let cancelled = false
    if (projects.length === 0) {
      setByProject({})
      setErrors({})
      setLoading(false)
      return
    }
    setLoading(true)
    Promise.allSettled(projects.map((p) => loaderRef.current(p.id))).then((results) => {
      if (cancelled) return
      const next: Record<string, T | null> = {}
      const nextErrors: Record<string, string> = {}
      results.forEach((r, i) => {
        const id = projects[i].id
        if (r.status === 'fulfilled') next[id] = r.value
        else {
          next[id] = null
          nextErrors[id] = toUserMessage(r.reason)
        }
      })
      setByProject(next)
      setErrors(nextErrors)
      setLoading(false)
    })
    return () => {
      cancelled = true
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [key, token, ...deps])

  const reload = useCallback(() => setToken((t) => t + 1), [])

  return { byProject, errors, loading: projects === null || loading, reload }
}
