import { api } from './client'
import type { Dashboard } from '../types/dashboard'

export const dashboardApi = {
  get: () => api.get<Dashboard>('/dashboard'),
}
