import { api, downloadFile, uploadFile } from './client'
import { todayIso } from '../utils/format'
import type { PageResponse } from '../types/common'
import type { Comment, CreateCommentRequest } from '../types/work'

export interface AppNotification {
  id: string
  type: 'TASK_ASSIGNED' | 'TASK_COMPLETED' | 'TASK_COMMENTED' | 'MENTIONED' | 'TASK_DUE_SOON'
  title: string
  projectId: string | null
  taskId: string | null
  read: boolean
  createdAt: string
}

export const notificationsApi = {
  list: (page = 0, size = 15) => api.get<PageResponse<AppNotification>>('/notifications', { page, size }),
  unreadCount: () => api.get<{ count: number }>('/notifications/unread-count'),
  markRead: (id: string) => api.post<void>(`/notifications/${id}/read`),
  markAllRead: () => api.post<void>('/notifications/read-all'),
}

export interface ActivityEntry {
  id: string
  type: string
  actorType: string
  actorId: string | null
  summary: string
  occurredAt: string
}

export const activityApi = {
  list: (projectId: string, page = 0, size = 30) =>
    api.get<PageResponse<ActivityEntry>>(`/projects/${projectId}/activity`, { page, size }),
}

export const taskCommentsApi = {
  list: (taskId: string) => api.get<Comment[]>(`/tasks/${taskId}/comments`),
  create: (taskId: string, body: CreateCommentRequest) => api.post<Comment>(`/tasks/${taskId}/comments`, body),
}

export const followersApi = {
  list: (taskId: string) => api.get<string[]>(`/tasks/${taskId}/followers`),
  follow: (taskId: string) => api.post<void>(`/tasks/${taskId}/followers/me`),
  unfollow: (taskId: string) => api.del(`/tasks/${taskId}/followers/me`),
}

export interface TimeEntry {
  id: string
  taskId: string
  projectId: string
  userId: string
  workDate: string
  minutes: number
  note: string | null
  billable: boolean
}

export interface RunningTimer {
  taskId: string
  startedAt: string
}

export const timeApi = {
  listForTask: (taskId: string) => api.get<TimeEntry[]>(`/tasks/${taskId}/time-entries`),
  listMine: (from: string, to: string) => api.get<TimeEntry[]>('/time-entries/mine', { from, to }),
  log: (taskId: string, body: { workDate: string; minutes: number; note?: string; billable: boolean }) =>
    api.post<TimeEntry>(`/tasks/${taskId}/time-entries`, body),
  remove: (id: string) => api.del(`/time-entries/${id}`),
  /** Resolves undefined when no timer is running. */
  currentTimer: () => api.get<RunningTimer | undefined>('/timer'),
  startTimer: (taskId: string) => api.post<RunningTimer>(`/tasks/${taskId}/timer`),
  stopTimer: () => api.post<TimeEntry>('/timer/stop', { workDate: todayIso() }),
  cancelTimer: () => api.del('/timer'),
}

export interface Attachment {
  id: string
  taskId: string
  fileName: string
  contentType: string
  sizeBytes: number
  uploadedBy: string
  createdAt: string
}

export const attachmentsApi = {
  list: (taskId: string) => api.get<Attachment[]>(`/tasks/${taskId}/attachments`),
  upload: (taskId: string, file: File) => uploadFile<Attachment>(`/tasks/${taskId}/attachments`, file),
  download: (attachment: Attachment) => downloadFile(`/attachments/${attachment.id}/content`, attachment.fileName),
  remove: (id: string) => api.del(`/attachments/${id}`),
}

export interface SearchHit {
  type: 'PROJECT' | 'TASK'
  id: string
  name: string
  projectId: string
  projectName: string
}

export const globalSearchApi = {
  search: (q: string) => api.get<SearchHit[]>('/search', { q }),
}
