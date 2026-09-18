import { api } from './client'
import type { AIRecommendation, RequestRecommendationRequest } from '../types/ai'

export const aiApi = {
  list: (projectId: string) =>
    api.get<AIRecommendation[]>(`/projects/${projectId}/ai/recommendations`),
  get: (id: string) => api.get<AIRecommendation>(`/ai/recommendations/${id}`),
  request: (projectId: string, body: RequestRecommendationRequest) =>
    api.post<AIRecommendation>(`/projects/${projectId}/ai/recommendations`, body),
  accept: (id: string) => api.post<AIRecommendation>(`/ai/recommendations/${id}/accept`),
  reject: (id: string) => api.post<AIRecommendation>(`/ai/recommendations/${id}/reject`),
}
