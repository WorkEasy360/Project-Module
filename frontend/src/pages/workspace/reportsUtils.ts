import { PROJECT_PRIORITIES, PROJECT_STATUSES, type ProjectSummary } from '../../types/project'
import { RISK_STATUSES, TASK_STATUSES, type ProjectSummaryReport } from '../../types/work'

/** A project together with its successfully loaded summary report. */
export interface LoadedReport {
  project: ProjectSummary
  report: ProjectSummaryReport
}

export type ReportSort = 'name,ASC' | 'name,DESC' | 'status'

export const REPORT_SORT_OPTIONS: { value: ReportSort; label: string }[] = [
  { value: 'name,ASC', label: 'Name A–Z' },
  { value: 'name,DESC', label: 'Name Z–A' },
  { value: 'status', label: 'Status' },
]

export interface TaskProgress {
  total: number
  completed: number
  /** Whole percent, 0 when there are no tasks (never a fabricated value). */
  percent: number
}

export function taskProgress(report: ProjectSummaryReport): TaskProgress {
  const total = TASK_STATUSES.reduce((sum, s) => sum + (report.taskCountByStatus[s] ?? 0), 0)
  const completed = report.taskCountByStatus.COMPLETED ?? 0
  return { total, completed, percent: total === 0 ? 0 : Math.round((completed / total) * 100) }
}

export function sumBy<T>(items: T[], pick: (item: T) => number): number {
  return items.reduce((sum, item) => sum + pick(item), 0)
}

function sumRecord<K extends string>(keys: readonly K[], records: Partial<Record<K, number>>[]): Record<K, number> {
  const out = {} as Record<K, number>
  for (const key of keys) out[key] = sumBy(records, (r) => r[key] ?? 0)
  return out
}

/**
 * Client-side totals across the loaded reports — every field of ProjectSummaryReport summed.
 * Only reports that actually loaded are included; the caller labels it as such.
 */
export function sumReports(reports: ProjectSummaryReport[]): ProjectSummaryReport {
  return {
    taskCountByStatus: sumRecord(TASK_STATUSES, reports.map((r) => r.taskCountByStatus)),
    riskCountByStatus: sumRecord(RISK_STATUSES, reports.map((r) => r.riskCountByStatus)),
    riskCountByPriority: sumRecord(PROJECT_PRIORITIES, reports.map((r) => r.riskCountByPriority)),
    issueCountByPriority: sumRecord(PROJECT_PRIORITIES, reports.map((r) => r.issueCountByPriority)),
    decisionCount: sumBy(reports, (r) => r.decisionCount),
    delayedItemCount: sumBy(reports, (r) => r.delayedItemCount),
    brokenDependencyCount: sumBy(reports, (r) => r.brokenDependencyCount),
  }
}

export function sortProjects(projects: ProjectSummary[], sort: ReportSort): ProjectSummary[] {
  const sorted = [...projects]
  if (sort === 'status') {
    sorted.sort(
      (a, b) =>
        PROJECT_STATUSES.indexOf(a.status) - PROJECT_STATUSES.indexOf(b.status) || a.name.localeCompare(b.name),
    )
  } else {
    sorted.sort((a, b) => a.name.localeCompare(b.name))
    if (sort === 'name,DESC') sorted.reverse()
  }
  return sorted
}
