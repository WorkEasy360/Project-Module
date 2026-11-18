import { api } from './client'
import type { PageResponse } from '../types/common'
import type { CreateProjectRequest, Project, ProjectSummary, UpdateProjectRequest } from '../types/project'

export const projectsApi = {
  list: (page = 0, size = 20, sort?: string, archived = false) =>
    api.get<PageResponse<ProjectSummary>>('/projects', archived ? { page, size, sort, archived } : { page, size, sort }),
  get: (id: string) => api.get<Project>(`/projects/${id}`),
  create: (body: CreateProjectRequest) => api.post<Project>('/projects', body),
  update: (id: string, body: UpdateProjectRequest) => api.patch<Project>(`/projects/${id}`, body),
  archive: (id: string) => api.del(`/projects/${id}`),
}
