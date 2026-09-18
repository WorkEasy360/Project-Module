import type { ProjectPriority } from './project'

export interface ProjectTemplate {
  id: string
  organizationId: string
  name: string
  description: string | null
  defaultPriority: ProjectPriority | null
  archived: boolean
  createdAt: string
  updatedAt: string
  version: number
}

export interface CreateTemplateRequest {
  name: string
  description?: string | null
  defaultPriority?: ProjectPriority | null
}

export interface UpdateTemplateRequest {
  name?: string | null
  description?: string | null
  defaultPriority?: ProjectPriority | null
  version: number
}

export interface ApplyTemplateRequest {
  name: string
  description?: string | null
}
