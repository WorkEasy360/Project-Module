import { api } from './client'
import type {
  AutomationRun,
  CreateAutomationRequest,
  ProjectAutomation,
  UpdateAutomationRequest,
} from '../types/automation'

export const automationsApi = {
  list: (projectId: string) => api.get<ProjectAutomation[]>(`/projects/${projectId}/automations`),
  create: (projectId: string, body: CreateAutomationRequest) =>
    api.post<ProjectAutomation>(`/projects/${projectId}/automations`, body),
  update: (id: string, body: UpdateAutomationRequest) =>
    api.patch<ProjectAutomation>(`/automations/${id}`, body),
  archive: (id: string) => api.del(`/automations/${id}`),
  runs: (id: string) => api.get<AutomationRun[]>(`/automations/${id}/runs`),
}
