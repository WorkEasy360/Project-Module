import { api } from './client'
import type { PageResponse } from '../types/common'
import type { Comment, CreateCommentRequest, UpdateCommentRequest } from '../types/work'

export const commentsApi = {
  list: (projectId: string, page = 0, size = 20) =>
    api.get<PageResponse<Comment>>(`/projects/${projectId}/comments`, { page, size }),
  create: (projectId: string, body: CreateCommentRequest) =>
    api.post<Comment>(`/projects/${projectId}/comments`, body),
  update: (id: string, body: UpdateCommentRequest) => api.patch<Comment>(`/comments/${id}`, body),
  remove: (id: string) => api.del(`/comments/${id}`),
}
