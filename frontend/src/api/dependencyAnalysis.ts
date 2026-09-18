import { api } from './client'
import type { DependencyAnalysis } from '../types/work'

export const dependencyAnalysisApi = {
  get: (projectId: string) =>
    api.get<DependencyAnalysis>(`/projects/${projectId}/dependencies/analysis`),
}
