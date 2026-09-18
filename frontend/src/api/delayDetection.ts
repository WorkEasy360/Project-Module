import { api } from './client'
import type { DelayedItems } from '../types/work'

export const delayDetectionApi = {
  get: (projectId: string) => api.get<DelayedItems>(`/projects/${projectId}/delayed`),
}
