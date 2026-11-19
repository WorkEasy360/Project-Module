import { Navigate, Route, HashRouter, Routes } from 'react-router-dom'
import { SessionProvider } from './context/SessionContext'
import { IdentityProvider } from './context/IdentityContext'
import { ErrorBoundary } from './components/common/ErrorBoundary'
import { ThemeProvider } from './context/ThemeContext'
import { AppLayout } from './components/layout/AppLayout'
import { SignInPage } from './pages/auth/SignInPage'
import { DashboardPage } from './pages/dashboard/DashboardPage'
import { ProjectsListPage } from './pages/projects/ProjectsListPage'
import { ProjectWorkspace } from './pages/projects/ProjectWorkspace'
import { OverviewTab } from './pages/projects/tabs/OverviewTab'
import { MembersTab } from './pages/projects/tabs/MembersTab'
import { TasksTab } from './pages/projects/tabs/TasksTab'
import { TaskDetailPage } from './pages/projects/tabs/TaskDetailPage'
import { KanbanTab } from './pages/projects/tabs/KanbanTab'
import { ActivityTab } from './pages/projects/tabs/ActivityTab'
import { TasksPage } from './pages/workspace/TasksPage'
import { TeamPage } from './pages/workspace/TeamPage'
import { ReportsPage } from './pages/workspace/ReportsPage'
import { SearchResultsPage } from './pages/workspace/SearchResultsPage'
import { AutomationPage } from './pages/workspace/AutomationPage'
import { SettingsPage } from './pages/workspace/SettingsPage'
import { NotFoundPage } from './pages/NotFoundPage'
import { HelpdeskPage } from './pages/workspace/HelpdeskPage'

/** Routes for the V3 main navigation: Home, Projects, Tasks, Team, Automation, Reports, Helpdesk, Settings. */
function App() {
  return (
    <ErrorBoundary>
    <ThemeProvider>
      <SessionProvider>
        <IdentityProvider>
        <HashRouter>
          <Routes>
            <Route path="sign-in" element={<SignInPage />} />
            <Route path="sign-up" element={<SignInPage initialMode="register" />} />
            <Route path="join/:inviteToken" element={<SignInPage />} />
            <Route path="reset/:resetToken" element={<SignInPage />} />
            <Route element={<AppLayout />}>
              <Route index element={<Navigate to="/dashboard" replace />} />
              <Route path="dashboard" element={<DashboardPage />} />

              <Route path="projects" element={<ProjectsListPage />} />
              <Route path="projects/archived" element={<ProjectsListPage archived />} />
              <Route path="projects/:projectId" element={<ProjectWorkspace />}>
                <Route index element={<Navigate to="overview" replace />} />
                <Route path="overview" element={<OverviewTab />} />
                <Route path="tasks" element={<TasksTab />} />
                <Route path="tasks/:taskId" element={<TaskDetailPage />} />
                <Route path="kanban" element={<KanbanTab />} />
                <Route path="members" element={<MembersTab />} />
                <Route path="activity" element={<ActivityTab />} />
              </Route>

              <Route path="tasks" element={<TasksPage scope="mine" />} />
              <Route path="tasks/all" element={<TasksPage scope="all" />} />

              <Route path="team" element={<TeamPage section="members" />} />
              <Route path="team/teams" element={<TeamPage section="teams" />} />
              <Route path="team/invitations" element={<TeamPage section="invitations" />} />

              <Route path="automations" element={<AutomationPage view="rules" />} />
              <Route path="automations/runs" element={<AutomationPage view="runs" />} />

              <Route path="reports" element={<ReportsPage view="tasks" />} />
              <Route path="reports/projects" element={<ReportsPage view="projects" />} />
              <Route path="reports/workload" element={<ReportsPage view="workload" />} />

              <Route path="helpdesk" element={<HelpdeskPage section="raise" />} />
              <Route path="helpdesk/tickets" element={<HelpdeskPage section="tickets" />} />
              <Route path="helpdesk/articles" element={<HelpdeskPage section="articles" />} />

              <Route path="settings" element={<SettingsPage section="workspace" />} />
              <Route path="settings/fields" element={<SettingsPage section="fields" />} />
              <Route path="settings/notifications" element={<SettingsPage section="notifications" />} />
              <Route path="settings/integrations" element={<SettingsPage section="integrations" />} />
              <Route path="settings/subscription" element={<SettingsPage section="subscription" />} />

              <Route path="search" element={<SearchResultsPage />} />
              <Route path="*" element={<NotFoundPage />} />
            </Route>
          </Routes>
        </HashRouter>
        </IdentityProvider>
      </SessionProvider>
    </ThemeProvider>
    </ErrorBoundary>
  )
}

export default App
