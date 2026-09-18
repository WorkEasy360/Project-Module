import type { ProjectPriority } from './project'

// ---------- Phase ----------

export interface Phase {
  id: string
  projectId: string
  name: string
  description: string | null
  startDate: string | null
  endDate: string | null
  archived: boolean
  createdAt: string
  updatedAt: string
  version: number
}

export interface CreatePhaseRequest {
  name: string
  description?: string | null
  startDate?: string | null
  endDate?: string | null
}

export interface UpdatePhaseRequest {
  name?: string | null
  description?: string | null
  startDate?: string | null
  endDate?: string | null
  version: number
}

// ---------- Milestone ----------

export type MilestoneStatus = 'PENDING' | 'AT_RISK' | 'COMPLETED'

export const MILESTONE_STATUSES: MilestoneStatus[] = ['PENDING', 'AT_RISK', 'COMPLETED']

/** Allowed forward transitions only — the domain never allows moving back to PENDING. */
export const MILESTONE_STATUS_TRANSITIONS: Record<MilestoneStatus, MilestoneStatus[]> = {
  PENDING: ['AT_RISK', 'COMPLETED'],
  AT_RISK: ['COMPLETED'],
  COMPLETED: [],
}

export interface Milestone {
  id: string
  projectId: string
  phaseId: string | null
  name: string
  description: string | null
  dueDate: string | null
  status: MilestoneStatus
  archived: boolean
  createdAt: string
  updatedAt: string
  version: number
}

export interface CreateMilestoneRequest {
  name: string
  description?: string | null
  dueDate?: string | null
  phaseId?: string | null
}

export interface UpdateMilestoneRequest {
  name?: string | null
  description?: string | null
  dueDate?: string | null
  phaseId?: string | null
  status?: MilestoneStatus | null
  version: number
}

// ---------- TaskList ----------

export interface TaskList {
  id: string
  projectId: string
  phaseId: string | null
  name: string
  description: string | null
  archived: boolean
  createdAt: string
  updatedAt: string
  version: number
}

export interface CreateTaskListRequest {
  name: string
  description?: string | null
  phaseId?: string | null
}

export interface UpdateTaskListRequest {
  name?: string | null
  description?: string | null
  phaseId?: string | null
  version: number
}

// ---------- Task ----------

export type TaskStatus = 'TODO' | 'BLOCKED' | 'OVERDUE' | 'COMPLETED'

export const TASK_STATUSES: TaskStatus[] = ['TODO', 'BLOCKED', 'OVERDUE', 'COMPLETED']

/** The domain only allows forward transitions away from TODO; never back to TODO. */
export const TASK_STATUS_TRANSITIONS: Record<TaskStatus, TaskStatus[]> = {
  TODO: ['BLOCKED', 'OVERDUE', 'COMPLETED'],
  BLOCKED: ['OVERDUE', 'COMPLETED'],
  OVERDUE: ['BLOCKED', 'COMPLETED'],
  COMPLETED: [],
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
}

export interface CreateTaskRequest {
  name: string
  description?: string | null
  dueDate?: string | null
  assigneeId?: string | null
}

export interface UpdateTaskRequest {
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

// ---------- Subtask ----------

export interface Subtask {
  id: string
  taskId: string
  name: string
  description: string | null
  completed: boolean
  archived: boolean
  createdAt: string
  updatedAt: string
  version: number
}

export interface CreateSubtaskRequest {
  name: string
  description?: string | null
}

export interface UpdateSubtaskRequest {
  name?: string | null
  description?: string | null
  completed?: boolean | null
  version: number
}

// ---------- Checklist ----------

export interface ChecklistItem {
  id: string
  taskId: string
  subtaskId: string | null
  text: string
  checked: boolean
  archived: boolean
  createdAt: string
  updatedAt: string
  version: number
}

export interface CreateChecklistRequest {
  text: string
  subtaskId?: string | null
}

export interface UpdateChecklistRequest {
  text?: string | null
  checked?: boolean | null
  subtaskId?: string | null
  version: number
}

// ---------- Dependency ----------

export interface Dependency {
  id: string
  dependentTaskId: string
  prerequisiteTaskId: string
  broken: boolean
  archived: boolean
  createdAt: string
  updatedAt: string
  version: number
}

export interface CreateDependencyRequest {
  prerequisiteTaskId: string
}

export interface UpdateDependencyRequest {
  broken?: boolean | null
  version: number
}

// ---------- Risk ----------

export type RiskStatus = 'OPEN' | 'RESOLVED'

export const RISK_STATUSES: RiskStatus[] = ['OPEN', 'RESOLVED']

export type RiskSortField = 'CREATED_AT' | 'NAME' | 'PRIORITY' | 'STATUS'

export const RISK_SORT_FIELDS: RiskSortField[] = ['CREATED_AT', 'NAME', 'PRIORITY', 'STATUS']

export interface Risk {
  id: string
  projectId: string
  name: string
  description: string | null
  priority: ProjectPriority
  status: RiskStatus
  archived: boolean
  createdAt: string
  updatedAt: string
  version: number
}

export interface CreateRiskRequest {
  name: string
  description?: string | null
  priority: ProjectPriority
}

export interface UpdateRiskRequest {
  name?: string | null
  description?: string | null
  priority?: ProjectPriority | null
  status?: RiskStatus | null
  version: number
}

export interface RiskListQuery {
  status?: RiskStatus
  priority?: ProjectPriority
  archived?: boolean
  sortBy?: RiskSortField
  sortDir?: 'ASC' | 'DESC'
}

// ---------- Issue ----------

export type IssueSortField = 'CREATED_AT' | 'NAME' | 'PRIORITY'

export const ISSUE_SORT_FIELDS: IssueSortField[] = ['CREATED_AT', 'NAME', 'PRIORITY']

export interface Issue {
  id: string
  projectId: string
  name: string
  description: string | null
  priority: ProjectPriority
  archived: boolean
  createdAt: string
  updatedAt: string
  version: number
}

export interface CreateIssueRequest {
  name: string
  description?: string | null
  priority: ProjectPriority
}

export interface UpdateIssueRequest {
  name?: string | null
  description?: string | null
  priority?: ProjectPriority | null
  version: number
}

export interface IssueListQuery {
  priority?: ProjectPriority
  archived?: boolean
  sortBy?: IssueSortField
  sortDir?: 'ASC' | 'DESC'
}

// ---------- Decision ----------

export type DecisionSortField = 'CREATED_AT' | 'NAME'

export const DECISION_SORT_FIELDS: DecisionSortField[] = ['CREATED_AT', 'NAME']

export interface Decision {
  id: string
  projectId: string
  name: string
  description: string | null
  decidedBy: string | null
  archived: boolean
  createdAt: string
  updatedAt: string
  version: number
}

export interface CreateDecisionRequest {
  name: string
  description?: string | null
  decidedBy?: string | null
}

export interface UpdateDecisionRequest {
  name?: string | null
  description?: string | null
  decidedBy?: string | null
  version: number
}

export interface DecisionListQuery {
  decidedBy?: string
  archived?: boolean
  sortBy?: DecisionSortField
  sortDir?: 'ASC' | 'DESC'
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
}

export interface UpdateCommentRequest {
  body: string
  version: number
}

// ---------- Custom Field ----------

export type CustomFieldValueType = 'TEXT' | 'NUMBER' | 'DATE' | 'BOOLEAN'

export const CUSTOM_FIELD_VALUE_TYPES: CustomFieldValueType[] = ['TEXT', 'NUMBER', 'DATE', 'BOOLEAN']

export interface CustomField {
  id: string
  projectId: string
  name: string
  valueType: CustomFieldValueType
  value: string
  archived: boolean
  createdAt: string
  updatedAt: string
  version: number
}

export interface CreateCustomFieldRequest {
  name: string
  valueType: CustomFieldValueType
  value: string
}

export interface UpdateCustomFieldRequest {
  name?: string | null
  value?: string | null
  version: number
}

// ---------- Search ----------

export interface SearchResult {
  resourceType: 'TASK' | 'RISK' | 'ISSUE' | 'DECISION' | string
  id: string
  projectId: string
  name: string
  description: string | null
  archived: boolean
  createdAt: string
  updatedAt: string
}

// ---------- Calendar ----------

export interface CalendarEntry {
  entityType: 'TASK' | 'MILESTONE' | 'PHASE' | string
  id: string
  name: string
  date: string | null
  startDate: string | null
  endDate: string | null
}

export interface Calendar {
  entries: CalendarEntry[]
}

// ---------- Timeline ----------

export interface TimelinePhase {
  id: string
  name: string
  startDate: string | null
  endDate: string | null
  milestones: Milestone[]
}

export interface Timeline {
  phases: TimelinePhase[]
  tasks: Task[]
}

// ---------- Gantt ----------

export interface GanttBar {
  id: string
  name: string
  startDate: string | null
  endDate: string | null
}

export interface GanttPoint {
  id: string
  name: string
  date: string | null
}

export interface Gantt {
  phases: GanttBar[]
  tasks: GanttPoint[]
  milestones: GanttPoint[]
  dependencies: Dependency[]
}

// ---------- Delay Detection ----------

export interface DelayedItem {
  entityType: 'TASK' | 'MILESTONE' | 'PHASE' | string
  id: string
  name: string
  dueDate: string | null
  daysOverdue: number
}

export interface DelayedItems {
  items: DelayedItem[]
}

// ---------- Dependency Analysis ----------

export interface DependencyAnalysis {
  cycles: string[][]
  blockedTaskIds: string[]
  brokenDependencies: Dependency[]
}

// ---------- Project Health ----------

export interface ProjectHealth {
  overdueItemCount: number
  unresolvedRiskCount: number
  unresolvedIssueCount: number
  blockedTaskCount: number
  brokenDependencyCount: number
}

// ---------- Reports ----------

export interface ProjectSummaryReport {
  taskCountByStatus: Record<TaskStatus, number>
  riskCountByStatus: Record<RiskStatus, number>
  riskCountByPriority: Record<ProjectPriority, number>
  issueCountByPriority: Record<ProjectPriority, number>
  decisionCount: number
  delayedItemCount: number
  brokenDependencyCount: number
}
