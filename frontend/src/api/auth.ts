import { api, uploadFile } from './client'

export type WorkspaceRole = 'ADMIN' | 'MEMBER'

export interface Account {
  id: string
  organizationId: string
  email: string
  name: string
  workspaceRole: WorkspaceRole
  /** Changes when the profile photo does; null when there is none. */
  avatarVersion?: string | null
}

export interface Session {
  token: string
  expiresAt: string
  account: Account
}

export const authApi = {
  uploadAvatar: (file: File) => uploadFile<Account>('/auth/me/avatar', file),
  removeAvatar: () => api.del('/auth/me/avatar'),
  login: (body: { email: string; password: string; rememberMe: boolean }) =>
    api.post<Session>('/auth/login', body),
  register: (body: { name: string; email: string; password: string; rememberMe: boolean }) =>
    api.post<Session>('/auth/register', body),
  logout: () => api.post<void>('/auth/logout'),
  config: () => api.get<{ googleClientId: string; emailEnabled: boolean; legacyHandover?: boolean; workeasy360SignInUrl?: string }>('/auth/config'),
  claimEarlierSignIn: (body: { userId: string; organizationId: string; name: string; email: string; password: string; rememberMe: boolean }) =>
    api.post<Session>('/auth/legacy/claim', body),
  google: (body: { credential: string; rememberMe: boolean }) => api.post<Session>('/auth/google', body),
  updateProfile: (name: string) => api.patch<Account>('/auth/me', { name }),
  changePassword: (body: { currentPassword: string; newPassword: string }) =>
    api.post<void>('/auth/change-password', body),
  forgotPassword: (email: string) => api.post<void>('/auth/forgot-password', { email }),
  resetPassword: (body: { token: string; newPassword: string }) => api.post<void>('/auth/reset-password', body),
  previewInvitation: (token: string) =>
    api.post<{ email: string; expiresAt: string }>('/auth/invitation/preview', { token }),
  acceptInvitation: (body: { token: string; name: string; password: string; rememberMe: boolean }) =>
    api.post<Session>('/auth/invitation/accept', body),
}
