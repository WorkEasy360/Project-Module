import { api } from './client'
import type { ProjectHealth } from '../types/work'

export const healthApi = {
  get: (projectId: string) => api.get<ProjectHealth>(`/projects/${projectId}/health`),
}
