import { AUTOMATION_TRIGGER_EVENTS, type AutomationRun, type ProjectAutomation } from '../../types/automation'
import type { ProjectSummary } from '../../types/project'

/** One table row: an automation rule together with the project it belongs to. */
export interface AutomationRow {
  automation: ProjectAutomation
  project: ProjectSummary
}

export type EnabledFilter = '' | 'enabled' | 'disabled'

export interface AutomationFilters {
  projectId: string
  trigger: string
  enabled: EnabledFilter
}

export const EMPTY_AUTOMATION_FILTERS: AutomationFilters = { projectId: '', trigger: '', enabled: '' }

/** Run history for one rule, loaded lazily (only when its row is expanded). */
export type RunsState =
  | { status: 'idle' }
  | { status: 'loading' }
  | { status: 'loaded'; runs: AutomationRun[] }
  | { status: 'error'; message: string }

/** Flattens the per-project rule lists, sorted by project name then rule name. */
export function collectAutomations(
  projects: ProjectSummary[],
  byProject: Record<string, ProjectAutomation[] | null>,
): AutomationRow[] {
  const rows: AutomationRow[] = []
  for (const project of projects) {
    for (const automation of byProject[project.id] ?? []) rows.push({ automation, project })
  }
  return rows.sort(
    (a, b) => a.project.name.localeCompare(b.project.name) || a.automation.name.localeCompare(b.automation.name),
  )
}

export function filterAutomations(rows: AutomationRow[], f: AutomationFilters): AutomationRow[] {
  return rows.filter(
    ({ automation, project }) =>
      (f.projectId === '' || project.id === f.projectId) &&
      (f.trigger === '' || automation.triggerEvent === f.trigger) &&
      (f.enabled === '' || (f.enabled === 'enabled') === automation.enabled),
  )
}

/** The catalogue of trigger events plus any value the backend returned that is not in it. */
export function triggerOptions(rows: AutomationRow[]): string[] {
  const seen = new Set<string>(AUTOMATION_TRIGGER_EVENTS)
  for (const { automation } of rows) seen.add(automation.triggerEvent)
  return [...seen]
}

/** Runs newest first (by executedAt). */
export function sortRuns(runs: AutomationRun[]): AutomationRun[] {
  return [...runs].sort((a, b) => b.executedAt.localeCompare(a.executedAt))
}

export function latestRun(runs: AutomationRun[]): AutomationRun | null {
  return sortRuns(runs)[0] ?? null
}
