import { api } from './client'
import type { CreateCustomFieldRequest, CustomField, UpdateCustomFieldRequest } from '../types/work'

export const customFieldsApi = {
  list: (projectId: string) => api.get<CustomField[]>(`/projects/${projectId}/custom-fields`),
  create: (projectId: string, body: CreateCustomFieldRequest) =>
    api.post<CustomField>(`/projects/${projectId}/custom-fields`, body),
  update: (id: string, body: UpdateCustomFieldRequest) =>
    api.patch<CustomField>(`/custom-fields/${id}`, body),
  archive: (id: string) => api.del(`/custom-fields/${id}`),
}
