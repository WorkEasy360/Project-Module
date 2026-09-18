import { api } from './client'
import type { PageResponse } from '../types/common'
import type { SearchResult } from '../types/work'

export const searchApi = {
  search: (projectId: string, q: string, page = 0, size = 20) =>
    api.get<PageResponse<SearchResult>>(`/projects/${projectId}/search`, { q, page, size }),
}
