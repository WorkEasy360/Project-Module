import { api } from './client'
import type { ChecklistItem, CreateChecklistRequest, UpdateChecklistRequest } from '../types/work'

export const checklistsApi = {
  list: (taskId: string) => api.get<ChecklistItem[]>(`/tasks/${taskId}/checklists`),
  create: (taskId: string, body: CreateChecklistRequest) =>
    api.post<ChecklistItem>(`/tasks/${taskId}/checklists`, body),
  update: (id: string, body: UpdateChecklistRequest) =>
    api.patch<ChecklistItem>(`/checklists/${id}`, body),
  archive: (id: string) => api.del(`/checklists/${id}`),
}
