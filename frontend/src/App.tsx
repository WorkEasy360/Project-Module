import { Navigate, Route, HashRouter, Routes } from 'react-router-dom'
import { IdentityProvider } from './context/IdentityContext'
import { AppLayout } from './components/layout/AppLayout'
import { DashboardPage } from './pages/dashboard/DashboardPage'
import { ProjectsListPage } from './pages/projects/ProjectsListPage'
import { ProjectWorkspace } from './pages/projects/ProjectWorkspace'
import { OverviewTab } from './pages/projects/tabs/OverviewTab'
import { MembersTab } from './pages/projects/tabs/MembersTab'
import { PhasesTab } from './pages/projects/tabs/PhasesTab'
import { MilestonesTab } from './pages/projects/tabs/MilestonesTab'
import { TaskListsTab } from './pages/projects/tabs/TaskListsTab'
import { TasksTab } from './pages/projects/tabs/TasksTab'
import { TaskDetailPage } from './pages/projects/tabs/TaskDetailPage'
import { KanbanTab } from './pages/projects/tabs/KanbanTab'
import { CalendarTab } from './pages/projects/tabs/CalendarTab'
import { TimelineTab } from './pages/projects/tabs/TimelineTab'
import { GanttTab } from './pages/projects/tabs/GanttTab'
import { RisksTab } from './pages/projects/tabs/RisksTab'
import { IssuesTab } from './pages/projects/tabs/IssuesTab'
import { DecisionsTab } from './pages/projects/tabs/DecisionsTab'
import { DependenciesTab } from './pages/projects/tabs/DependenciesTab'
import { CommentsTab } from './pages/projects/tabs/CommentsTab'
import { ActivityTab } from './pages/projects/tabs/ActivityTab'
import { CustomFieldsTab } from './pages/projects/tabs/CustomFieldsTab'
import { SearchTab } from './pages/projects/tabs/SearchTab'
import { HealthTab } from './pages/projects/tabs/HealthTab'
import { DelayedTab } from './pages/projects/tabs/DelayedTab'
import { ReportsTab } from './pages/projects/tabs/ReportsTab'
import { AutomationsTab } from './pages/projects/tabs/AutomationsTab'
import { AutomationRunsPage } from './pages/projects/tabs/AutomationRunsPage'
import { AITab } from './pages/projects/tabs/AITab'
import { TemplatesPage } from './pages/templates/TemplatesPage'
import { NotFoundPage } from './pages/NotFoundPage'
import { AdvancedFeaturesPage } from './pages/features/AdvancedFeaturesPage'

export default function App() {
  return (
    <IdentityProvider>
      <HashRouter>
        <Routes>
          <Route element={<AppLayout />}>
            <Route index element={<Navigate to="/dashboard" replace />} />
            <Route path="dashboard" element={<DashboardPage />} />
            <Route path="projects" element={<ProjectsListPage />} />
            <Route path="templates" element={<TemplatesPage />} />

            <Route path="projects/:projectId" element={<ProjectWorkspace />}>
              <Route index element={<Navigate to="overview" replace />} />
              <Route path="overview" element={<OverviewTab />} />
              <Route path="members" element={<MembersTab />} />
              <Route path="phases" element={<PhasesTab />} />
              <Route path="milestones" element={<MilestonesTab />} />
              <Route path="task-lists" element={<TaskListsTab />} />
              <Route path="tasks" element={<TasksTab />} />
              <Route path="tasks/:taskId" element={<TaskDetailPage />} />
              <Route path="kanban" element={<KanbanTab />} />
              <Route path="calendar" element={<CalendarTab />} />
              <Route path="timeline" element={<TimelineTab />} />
              <Route path="gantt" element={<GanttTab />} />
              <Route path="risks" element={<RisksTab />} />
              <Route path="issues" element={<IssuesTab />} />
              <Route path="decisions" element={<DecisionsTab />} />
              <Route path="dependencies" element={<DependenciesTab />} />
              <Route path="comments" element={<CommentsTab />} />
              <Route path="activity" element={<ActivityTab />} />
              <Route path="custom-fields" element={<CustomFieldsTab />} />
              <Route path="search" element={<SearchTab />} />
              <Route path="health" element={<HealthTab />} />
              <Route path="delayed" element={<DelayedTab />} />
              <Route path="reports" element={<ReportsTab />} />
              <Route path="automations" element={<AutomationsTab />} />
              <Route path="automations/:automationId/runs" element={<AutomationRunsPage />} />
              <Route path="ai" element={<AITab />} />
              <Route path="more" element={<AdvancedFeaturesPage />} />
            </Route>

            <Route path="*" element={<NotFoundPage />} />
          </Route>
        </Routes>
      </HashRouter>
    </IdentityProvider>
  )
}
