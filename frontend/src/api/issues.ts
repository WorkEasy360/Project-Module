import { api } from './client'
import type {
  BulkArchiveRequest,
  BulkArchiveResponse,
  CreateIssueRequest,
  Issue,
  IssueListQuery,
  UpdateIssueRequest,
} from '../types/work'

export const issuesApi = {
  list: (projectId: string, query?: IssueListQuery) =>
    api.get<Issue[]>(`/projects/${projectId}/issues`, query as Record<string, unknown>),
  create: (projectId: string, body: CreateIssueRequest) =>
    api.post<Issue>(`/projects/${projectId}/issues`, body),
  update: (id: string, body: UpdateIssueRequest) => api.patch<Issue>(`/issues/${id}`, body),
  archive: (id: string) => api.del(`/issues/${id}`),
  bulkArchive: (projectId: string, body: BulkArchiveRequest) =>
    api.post<BulkArchiveResponse>(`/projects/${projectId}/issues/bulk-archive`, body),
}
