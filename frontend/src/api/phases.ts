import { api } from './client'
import type { CreatePhaseRequest, Phase, UpdatePhaseRequest } from '../types/work'

export const phasesApi = {
  list: (projectId: string) => api.get<Phase[]>(`/projects/${projectId}/phases`),
  create: (projectId: string, body: CreatePhaseRequest) =>
    api.post<Phase>(`/projects/${projectId}/phases`, body),
  update: (id: string, body: UpdatePhaseRequest) => api.patch<Phase>(`/phases/${id}`, body),
  archive: (id: string) => api.del(`/phases/${id}`),
}
