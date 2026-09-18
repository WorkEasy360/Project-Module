import { api } from './client'
import type {
  BulkArchiveRequest,
  BulkArchiveResponse,
  CreateTaskRequest,
  KanbanBoard,
  Task,
  TaskListQuery,
  UpdateTaskRequest,
} from '../types/work'

export const tasksApi = {
  list: (projectId: string, query?: TaskListQuery) =>
    api.get<Task[]>(`/projects/${projectId}/tasks`, query as Record<string, unknown>),
  board: (projectId: string) => api.get<KanbanBoard>(`/projects/${projectId}/tasks/board`),
  get: (id: string) => api.get<Task>(`/tasks/${id}`),
  create: (projectId: string, body: CreateTaskRequest) =>
    api.post<Task>(`/projects/${projectId}/tasks`, body),
  update: (id: string, body: UpdateTaskRequest) => api.patch<Task>(`/tasks/${id}`, body),
  archive: (id: string) => api.del(`/tasks/${id}`),
  bulkArchive: (projectId: string, body: BulkArchiveRequest) =>
    api.post<BulkArchiveResponse>(`/projects/${projectId}/tasks/bulk-archive`, body),
}
