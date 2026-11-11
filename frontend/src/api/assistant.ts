import { api } from './client'

export interface AssistantReply {
  conversationId: string
  messages: string[]
  pendingChanges: string[]
  navigateTo: string | null
  awaitingApproval: boolean
}

export const assistantApi = {
  status: () => api.get<{ enabled: boolean }>('/assistant/status'),
  chat: (body: { conversationId: string | null; message: string; context: string }) =>
    api.post<AssistantReply>('/assistant/chat', body),
  decide: (conversationId: string, approve: boolean) =>
    api.post<AssistantReply>(`/assistant/conversations/${conversationId}/decision`, { approve }),
}
