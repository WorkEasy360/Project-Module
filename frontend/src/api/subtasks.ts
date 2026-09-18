import { api } from './client'
import type { CreateSubtaskRequest, Subtask, UpdateSubtaskRequest } from '../types/work'

export const subtasksApi = {
  list: (taskId: string) => api.get<Subtask[]>(`/tasks/${taskId}/subtasks`),
  create: (taskId: string, body: CreateSubtaskRequest) =>
    api.post<Subtask>(`/tasks/${taskId}/subtasks`, body),
  update: (id: string, body: UpdateSubtaskRequest) => api.patch<Subtask>(`/subtasks/${id}`, body),
  archive: (id: string) => api.del(`/subtasks/${id}`),
}
