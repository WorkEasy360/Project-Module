import { api } from './client'
import type { Calendar } from '../types/work'

export const calendarApi = {
  get: (projectId: string, from?: string, to?: string) =>
    api.get<Calendar>(`/projects/${projectId}/calendar`, { from, to }),
}
