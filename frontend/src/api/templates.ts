import { api } from './client'
import type { PageResponse } from '../types/common'
import type { Project } from '../types/project'
import type {
  ApplyTemplateRequest,
  CreateTemplateRequest,
  ProjectTemplate,
  UpdateTemplateRequest,
} from '../types/template'

export const templatesApi = {
  list: (page = 0, size = 20) => api.get<PageResponse<ProjectTemplate>>('/templates', { page, size }),
  get: (id: string) => api.get<ProjectTemplate>(`/templates/${id}`),
  create: (body: CreateTemplateRequest) => api.post<ProjectTemplate>('/templates', body),
  update: (id: string, body: UpdateTemplateRequest) =>
    api.patch<ProjectTemplate>(`/templates/${id}`, body),
  archive: (id: string) => api.del(`/templates/${id}`),
  apply: (id: string, body: ApplyTemplateRequest) =>
    api.post<Project>(`/templates/${id}/apply`, body),
}
