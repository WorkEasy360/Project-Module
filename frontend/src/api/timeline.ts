import { api } from './client'
import type { Timeline } from '../types/work'

export const timelineApi = {
  get: (projectId: string) => api.get<Timeline>(`/projects/${projectId}/timeline`),
}
