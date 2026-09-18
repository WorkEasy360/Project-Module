import { api } from './client'
import type { CreateMilestoneRequest, Milestone, UpdateMilestoneRequest } from '../types/work'

export const milestonesApi = {
  list: (projectId: string) => api.get<Milestone[]>(`/projects/${projectId}/milestones`),
  create: (projectId: string, body: CreateMilestoneRequest) =>
    api.post<Milestone>(`/projects/${projectId}/milestones`, body),
  update: (id: string, body: UpdateMilestoneRequest) =>
    api.patch<Milestone>(`/milestones/${id}`, body),
  archive: (id: string) => api.del(`/milestones/${id}`),
}
