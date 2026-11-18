
// ---------- Task ----------

/** V3 task statuses: To Do, In Progress, Blocked and Done (COMPLETED). Overdue is worked out from the due date. */
export type TaskStatus = 'TODO' | 'IN_PROGRESS' | 'BLOCKED' | 'COMPLETED'

export const TASK_STATUSES: TaskStatus[] = ['TODO', 'IN_PROGRESS', 'BLOCKED', 'COMPLETED']

/** A task can move from any status to any other. */
export const TASK_STATUS_TRANSITIONS: Record<TaskStatus, TaskStatus[]> = {
  TODO: ['IN_PROGRESS', 'BLOCKED', 'COMPLETED'],
  IN_PROGRESS: ['TODO', 'BLOCKED', 'COMPLETED'],
  BLOCKED: ['TODO', 'IN_PROGRESS', 'COMPLETED'],
  COMPLETED: ['TODO', 'IN_PROGRESS'],
}

/** V3 priorities: Low, Normal (stored as MEDIUM) and High. */
export type TaskPriority = 'LOW' | 'MEDIUM' | 'HIGH'
export const TASK_PRIORITIES: TaskPriority[] = ['LOW', 'MEDIUM', 'HIGH']

/** The optional fields a task can be created or edited with. */
export interface TaskPlanningFields {
  priority?: TaskPriority | null
}

export type TaskSortField = 'CREATED_AT' | 'DUE_DATE' | 'NAME' | 'STATUS'

export const TASK_SORT_FIELDS: TaskSortField[] = ['CREATED_AT', 'DUE_DATE', 'NAME', 'STATUS']

export interface Task {
  id: string
  projectId: string
  name: string
  description: string | null
  dueDate: string | null
  assigneeId: string | null
  status: TaskStatus
  archived: boolean
  createdAt: string
  updatedAt: string
  version: number
  priority: TaskPriority
}

export interface CreateTaskRequest extends TaskPlanningFields {
  name: string
  description?: string | null
  dueDate?: string | null
  assigneeId?: string | null
}

export interface UpdateTaskRequest extends TaskPlanningFields {
  name?: string | null
  description?: string | null
  dueDate?: string | null
  assigneeId?: string | null
  status?: TaskStatus | null
  version: number
}

export interface TaskListQuery {
  status?: TaskStatus
  assigneeId?: string
  dueDateBefore?: string
  dueDateAfter?: string
  archived?: boolean
  sortBy?: TaskSortField
  sortDir?: 'ASC' | 'DESC'
}

export interface KanbanColumn {
  status: TaskStatus
  tasks: Task[]
}

export interface KanbanBoard {
  columns: KanbanColumn[]
}

export interface BulkArchiveRequest {
  ids: string[]
}

export interface BulkArchiveResult {
  id: string
  succeeded: boolean
  error: string | null
}

export interface BulkArchiveResponse {
  results: BulkArchiveResult[]
}

// ---------- Checklist ----------

export interface ChecklistItem {
  id: string
  taskId: string
  text: string
  checked: boolean
  archived: boolean
  createdAt: string
  updatedAt: string
  version: number
}

export interface CreateChecklistRequest {
  text: string
}

export interface UpdateChecklistRequest {
  text?: string | null
  checked?: boolean | null
  version: number
}

// ---------- Comment ----------

export interface Comment {
  id: string
  projectId: string
  authorId: string
  body: string
  archived: boolean
  createdAt: string
  updatedAt: string
  version: number
}

export interface CreateCommentRequest {
  body: string
  mentions?: string[]
}

export interface UpdateCommentRequest {
  body: string
  version: number
}

// ---------- Reports ----------

/** A project's task counts by status, as the reports endpoint returns them. */
export interface ProjectSummaryReport {
  taskCountByStatus: Partial<Record<TaskStatus, number>>
}
