import { api } from './client'
import type {
  BulkArchiveRequest,
  BulkArchiveResponse,
  CreateRiskRequest,
  Risk,
  RiskListQuery,
  UpdateRiskRequest,
} from '../types/work'

export const risksApi = {
  list: (projectId: string, query?: RiskListQuery) =>
    api.get<Risk[]>(`/projects/${projectId}/risks`, query as Record<string, unknown>),
  create: (projectId: string, body: CreateRiskRequest) =>
    api.post<Risk>(`/projects/${projectId}/risks`, body),
  update: (id: string, body: UpdateRiskRequest) => api.patch<Risk>(`/risks/${id}`, body),
  archive: (id: string) => api.del(`/risks/${id}`),
  bulkArchive: (projectId: string, body: BulkArchiveRequest) =>
    api.post<BulkArchiveResponse>(`/projects/${projectId}/risks/bulk-archive`, body),
}
