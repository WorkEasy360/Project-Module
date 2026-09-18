import { api } from './client'
import type { CreateTaskListRequest, TaskList, UpdateTaskListRequest } from '../types/work'

export const taskListsApi = {
  list: (projectId: string) => api.get<TaskList[]>(`/projects/${projectId}/task-lists`),
  create: (projectId: string, body: CreateTaskListRequest) =>
    api.post<TaskList>(`/projects/${projectId}/task-lists`, body),
  update: (id: string, body: UpdateTaskListRequest) =>
    api.patch<TaskList>(`/task-lists/${id}`, body),
  archive: (id: string) => api.del(`/task-lists/${id}`),
}
