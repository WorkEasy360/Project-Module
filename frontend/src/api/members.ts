import { api } from './client'
import type { AddMemberRequest, ChangeMemberRoleRequest, ProjectMember } from '../types/project'

export const membersApi = {
  list: (projectId: string) => api.get<ProjectMember[]>(`/projects/${projectId}/members`),
  add: (projectId: string, body: AddMemberRequest) =>
    api.post<ProjectMember>(`/projects/${projectId}/members`, body),
  changeRole: (projectId: string, memberId: string, body: ChangeMemberRoleRequest) =>
    api.patch<ProjectMember>(`/projects/${projectId}/members/${memberId}`, body),
  remove: (projectId: string, memberId: string) =>
    api.del(`/projects/${projectId}/members/${memberId}`),
}
