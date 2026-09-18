import { api } from './client'
import type { Gantt } from '../types/work'

export const ganttApi = {
  get: (projectId: string) => api.get<Gantt>(`/projects/${projectId}/gantt`),
}
