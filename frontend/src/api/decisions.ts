import { api } from './client'
import type {
  BulkArchiveRequest,
  BulkArchiveResponse,
  CreateDecisionRequest,
  Decision,
  DecisionListQuery,
  UpdateDecisionRequest,
} from '../types/work'

export const decisionsApi = {
  list: (projectId: string, query?: DecisionListQuery) =>
    api.get<Decision[]>(`/projects/${projectId}/decisions`, query as Record<string, unknown>),
  create: (projectId: string, body: CreateDecisionRequest) =>
    api.post<Decision>(`/projects/${projectId}/decisions`, body),
  update: (id: string, body: UpdateDecisionRequest) =>
    api.patch<Decision>(`/decisions/${id}`, body),
  archive: (id: string) => api.del(`/decisions/${id}`),
  bulkArchive: (projectId: string, body: BulkArchiveRequest) =>
    api.post<BulkArchiveResponse>(`/projects/${projectId}/decisions/bulk-archive`, body),
}
