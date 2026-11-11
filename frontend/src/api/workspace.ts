import { api } from './client'
import type { Account } from './auth'

export interface Invitation {
  id: string
  email: string
  expiresAt: string
  /** Present only in the response that created the invitation. */
  token: string | null
}

export const workspaceApi = {
  people: () => api.get<Account[]>('/workspace/people'),
  invitations: () => api.get<Invitation[]>('/workspace/invitations'),
  invite: (email: string) => api.post<Invitation>('/workspace/invitations', { email }),
  revokeInvitation: (id: string) => api.del(`/workspace/invitations/${id}`),
  resetLink: (accountId: string) => api.post<{ token: string }>(`/workspace/people/${accountId}/reset-link`),
}

/** The address an invitee opens. The app uses hash routing, so this works on any host. */
export function invitationLink(token: string): string {
  return `${window.location.origin}${window.location.pathname}#/join/${token}`
}

/** The address a teammate opens to set a new password. */
export function passwordResetLink(token: string): string {
  return `${window.location.origin}${window.location.pathname}#/reset/${token}`
}
