package com.projectmodule.assistant.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.projectmodule.account.application.WorkspaceApplicationService;
import com.projectmodule.account.infrastructure.UserAccountRepository;
import com.projectmodule.collaboration.infrastructure.ProjectActivityRepository;
import com.projectmodule.common.context.ActorType;
import com.projectmodule.common.context.ImmutableRequestContext;
import com.projectmodule.common.context.RequestContext;
import com.projectmodule.common.exception.ResourceNotFoundException;
import com.projectmodule.common.exception.ValidationException;
import com.projectmodule.common.identity.ExternalUserId;
import com.projectmodule.common.identity.OrganizationId;
import com.projectmodule.notification.application.NotificationService;
import com.projectmodule.project.application.ProjectApplicationService;
import com.projectmodule.project.application.ProjectMemberApplicationService;
import com.projectmodule.project.application.authorization.ProjectAuthorizationService;
import com.projectmodule.project.domain.Project;
import com.projectmodule.project.domain.ProjectPriority;
import com.projectmodule.timetracking.application.TimeTrackingApplicationService;
import com.projectmodule.work.application.GlobalSearchApplicationService;
import com.projectmodule.work.application.ProjectCommentApplicationService;
import com.projectmodule.work.application.ProjectHealthApplicationService;
import com.projectmodule.work.application.ReportsApplicationService;
import com.projectmodule.work.application.TaskApplicationService;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class AssistantToolsTest {

    private final ProjectApplicationService projects = mock(ProjectApplicationService.class);
    private final TaskApplicationService tasks = mock(TaskApplicationService.class);
    private final UserAccountRepository accounts = mock(UserAccountRepository.class);
    private AssistantTools tools;
    private RequestContext context;
    private Project project;

    @BeforeEach
    void setUp() {
        tools = new AssistantTools(projects, mock(ProjectMemberApplicationService.class),
                mock(ProjectAuthorizationService.class), tasks, mock(ProjectHealthApplicationService.class),
                mock(ReportsApplicationService.class), mock(ProjectCommentApplicationService.class),
                mock(TimeTrackingApplicationService.class), mock(GlobalSearchApplicationService.class),
                mock(WorkspaceApplicationService.class), mock(NotificationService.class),
                mock(ProjectActivityRepository.class), accounts, new ObjectMapper(), new ProjectSetupTools(projects, mock(ProjectMemberApplicationService.class), tasks,
                        mock(com.projectmodule.work.application.SubtaskApplicationService.class),
                        mock(com.projectmodule.work.application.ChecklistApplicationService.class),
                        mock(com.projectmodule.work.application.PhaseApplicationService.class),
                        mock(com.projectmodule.work.application.MilestoneApplicationService.class),
                        mock(com.projectmodule.work.application.TaskListApplicationService.class),
                        mock(com.projectmodule.work.application.DependencyApplicationService.class),
                        mock(com.projectmodule.work.application.RiskApplicationService.class),
                        mock(com.projectmodule.work.application.IssueApplicationService.class),
                        mock(com.projectmodule.work.application.DecisionApplicationService.class),
                        mock(com.projectmodule.customization.application.ProjectTemplateApplicationService.class),
                        mock(WorkspaceApplicationService.class), accounts, new ObjectMapper()));
        OrganizationId org = OrganizationId.of(UUID.randomUUID());
        ExternalUserId me = ExternalUserId.of(UUID.randomUUID());
        context = new ImmutableRequestContext(Optional.of(me), Optional.of(org), "corr", ActorType.HUMAN);
        project = Project.create(org, "Apollo", me, ProjectPriority.HIGH);
        when(accounts.findById(org.value())).thenReturn(Optional.empty());
    }

    @Test
    @DisplayName("only the changing tools wait for approval")
    void writeToolsAreKnown() {
        assertThat(tools.definitions()).extracting(t -> t.name())
                .filteredOn(tools::isWrite)
                .containsExactlyInAnyOrder("create_task", "update_task", "add_comment", "log_time", "create_project",
                        "add_project_member", "set_up_project", "add_to_task", "update_subtask", "update_checklist_item",
                        "add_dependency", "update_milestone", "create_risk", "update_risk", "create_issue", "record_decision",
                        "invite_to_workspace", "create_project_from_template", "follow_task");
        assertThat(tools.isWrite("list_projects")).isFalse();
        assertThat(tools.isWrite("navigate")).isFalse();
    }

    @Test
    @DisplayName("navigation opens only known pages, and only projects the person can see")
    void navigationIsLimited() {
        assertThat(tools.execute(context, "navigate", Map.of("page", "timesheet")).navigateTo()).isEqualTo("/timesheet");
        assertThat(tools.execute(context, "navigate", Map.of("page", "admin_console")).error()).isTrue();

        UUID hidden = UUID.randomUUID();
        doThrow(new ResourceNotFoundException("Project", hidden)).when(projects).getProject(context, hidden);
        ToolOutcome refused = tools.execute(context, "navigate", Map.of("page", "project_board", "project_id", hidden.toString()));
        assertThat(refused.error()).isTrue();
        assertThat(refused.navigateTo()).isNull();

        when(projects.getProject(context, project.getId())).thenReturn(project);
        assertThat(tools.execute(context, "navigate", Map.of("page", "project_board", "project_id", project.getId().toString()))
                .navigateTo()).isEqualTo("/projects/" + project.getId() + "/kanban");
    }

    @Test
    @DisplayName("the approval text says exactly what will happen, in plain words")
    void describesChanges() {
        when(projects.getProject(context, project.getId())).thenReturn(project);

        String summary = tools.describe(context, "create_task", Map.of("project_id", project.getId().toString(),
                "name", "Write post", "priority", "HIGH", "due_date", "2026-11-20"));

        assertThat(summary).isEqualTo("Create task \"Write post\" in Apollo, High priority, due 2026-11-20");
    }

    @Test
    @DisplayName("a name where an id belongs, or a bad date, is refused with a message the model can act on")
    void validatesInput() {
        assertThatThrownBy(() -> tools.describe(context, "create_task", Map.of("project_id", "Apollo", "name", "x")))
                .isInstanceOf(ValidationException.class).hasMessageContaining("id from a lookup");
        when(projects.getProject(context, project.getId())).thenReturn(project);
        assertThatThrownBy(() -> tools.describe(context, "create_task", Map.of("project_id", project.getId().toString(),
                "name", "x", "due_date", "next friday")))
                .isInstanceOf(ValidationException.class).hasMessageContaining("2026-11-20");
    }

    @Test
    @DisplayName("nobody can be made a project's owner through the assistant")
    void ownerCannotBeGiven() {
        when(projects.getProject(context, project.getId())).thenReturn(project);

        assertThatThrownBy(() -> tools.describe(context, "add_project_member", Map.of("project_id", project.getId().toString(),
                "user_id", UUID.randomUUID().toString(), "role", "OWNER")))
                .isInstanceOf(ValidationException.class);
    }

    @Test
    @DisplayName("a refusal from the app comes back to the model as a failed result, not a crash")
    void failuresAreReported() {
        UUID missing = UUID.randomUUID();
        when(projects.getProject(context, missing)).thenThrow(new ResourceNotFoundException("Project", missing));

        ToolOutcome outcome = tools.execute(context, "get_project", Map.of("project_id", missing.toString()));

        assertThat(outcome.error()).isTrue();
    }
}
