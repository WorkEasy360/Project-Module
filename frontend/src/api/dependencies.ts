import { api } from './client'
import type { CreateDependencyRequest, Dependency, UpdateDependencyRequest } from '../types/work'

export const dependenciesApi = {
  list: (taskId: string) => api.get<Dependency[]>(`/tasks/${taskId}/dependencies`),
  create: (taskId: string, body: CreateDependencyRequest) =>
    api.post<Dependency>(`/tasks/${taskId}/dependencies`, body),
  update: (id: string, body: UpdateDependencyRequest) =>
    api.patch<Dependency>(`/dependencies/${id}`, body),
  archive: (id: string) => api.del(`/dependencies/${id}`),
}
