import { api } from './client'
import type { ProjectSummaryReport } from '../types/work'

export const reportsApi = {
  summary: (projectId: string) =>
    api.get<ProjectSummaryReport>(`/projects/${projectId}/reports/summary`),
}
