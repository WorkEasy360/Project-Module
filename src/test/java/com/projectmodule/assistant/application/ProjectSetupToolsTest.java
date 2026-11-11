package com.projectmodule.assistant.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.projectmodule.account.application.WorkspaceApplicationService;
import com.projectmodule.account.domain.UserAccount;
import com.projectmodule.account.infrastructure.UserAccountRepository;
import com.projectmodule.common.context.ActorType;
import com.projectmodule.common.context.ImmutableRequestContext;
import com.projectmodule.common.context.RequestContext;
import com.projectmodule.common.exception.AuthorizationException;
import com.projectmodule.common.exception.ValidationException;
import com.projectmodule.common.identity.ExternalUserId;
import com.projectmodule.common.identity.OrganizationId;
import com.projectmodule.customization.application.ProjectTemplateApplicationService;
import com.projectmodule.project.api.dto.AddMemberRequest;
import com.projectmodule.project.application.ProjectApplicationService;
import com.projectmodule.project.application.ProjectMemberApplicationService;
import com.projectmodule.project.domain.Project;
import com.projectmodule.project.domain.ProjectMember;
import com.projectmodule.project.domain.ProjectPriority;
import com.projectmodule.project.domain.ProjectRole;
import com.projectmodule.work.api.dto.CreateDependencyRequest;
import com.projectmodule.work.api.dto.CreateTaskRequest;
import com.projectmodule.work.application.ChecklistApplicationService;
import com.projectmodule.work.application.DecisionApplicationService;
import com.projectmodule.work.application.DependencyApplicationService;
import com.projectmodule.work.application.IssueApplicationService;
import com.projectmodule.work.application.MilestoneApplicationService;
import com.projectmodule.work.application.PhaseApplicationService;
import com.projectmodule.work.application.RiskApplicationService;
import com.projectmodule.work.application.SubtaskApplicationService;
import com.projectmodule.work.application.TaskApplicationService;
import com.projectmodule.work.application.TaskListApplicationService;
import com.projectmodule.work.domain.Checklist;
import com.projectmodule.work.domain.Dependency;
import com.projectmodule.work.domain.Subtask;
import com.projectmodule.work.domain.Task;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ProjectSetupToolsTest {

    private final ProjectApplicationService projects = mock(ProjectApplicationService.class);
    private final ProjectMemberApplicationService members = mock(ProjectMemberApplicationService.class);
    private final TaskApplicationService tasks = mock(TaskApplicationService.class);
    private final SubtaskApplicationService subtasks = mock(SubtaskApplicationService.class);
    private final ChecklistApplicationService checklists = mock(ChecklistApplicationService.class);
    private final DependencyApplicationService dependencies = mock(DependencyApplicationService.class);
    private final WorkspaceApplicationService workspace = mock(WorkspaceApplicationService.class);
    private final ObjectMapper mapper = new ObjectMapper();
    private ProjectSetupTools setup;
    private RequestContext context;
    private Project project;
    private UUID ravi;

    @BeforeEach
    void setUp() {
        setup = new ProjectSetupTools(projects, members, tasks, subtasks, checklists, mock(PhaseApplicationService.class),
                mock(MilestoneApplicationService.class), mock(TaskListApplicationService.class), dependencies,
                mock(RiskApplicationService.class), mock(IssueApplicationService.class),
                mock(DecisionApplicationService.class), mock(ProjectTemplateApplicationService.class), workspace,
                mock(UserAccountRepository.class), mapper);
        OrganizationId org = OrganizationId.of(UUID.randomUUID());
        ExternalUserId me = ExternalUserId.of(UUID.randomUUID());
        context = new ImmutableRequestContext(Optional.of(me), Optional.of(org), "corr", ActorType.HUMAN);
        project = Project.create(org, "Website Relaunch", me, ProjectPriority.HIGH);
        ravi = UUID.randomUUID();
        UserAccount person = mock(UserAccount.class);
        when(person.getId()).thenReturn(ravi);
        when(person.getDisplayName()).thenReturn("Ravi Kumar");
        when(workspace.listPeople(context)).thenReturn(List.of(person));
        when(projects.getProject(context, project.getId())).thenReturn(project);
        when(projects.createProject(eq(context), any())).thenReturn(project);
    }

    private Map<String, Object> plan() {
        return Map.of(
                "name", "Website Relaunch",
                "priority", "HIGH",
                "target_end_date", "2026-12-15",
                "members", List.of(Map.of("user_id", ravi.toString(), "role", "MEMBER")),
                "tasks", List.of(
                        Map.of("name", "Design homepage", "assignee_id", ravi.toString(), "due_date", "2026-11-20",
                                "subtasks", List.of("Wireframe", "Visual design"), "checklist", List.of("Use green palette")),
                        Map.of("name", "QA testing", "due_date", "2026-12-10", "depends_on", List.of("Design homepage"))));
    }

    @Test
    @DisplayName("a whole plan is described in one approval, task by task")
    void describesAPlan() {
        String summary = setup.describe(context, "set_up_project", plan());

        assertThat(summary).startsWith("Create project \"Website Relaunch\", High priority, finishing by 2026-12-15:")
                .contains("• Team: Ravi Kumar (Member)")
                .contains("• 2 tasks:")
                .contains("– Design homepage: Ravi Kumar, due 2026-11-20, 2 subtasks, 1 checklist item")
                .contains("– QA testing: due 2026-12-10, after Design homepage");
    }

    @Test
    @DisplayName("an approved plan creates the project, team, tasks, subtasks, checklist and dependencies in order")
    void buildsAPlan() throws Exception {
        Task design = Task.create(project.getId(), "Design homepage", null, null);
        Task qa = Task.create(project.getId(), "QA testing", null, null);
        when(tasks.createTask(eq(context), eq(project.getId()), any(CreateTaskRequest.class)))
                .thenAnswer(call -> ((CreateTaskRequest) call.getArgument(2)).name().equals("QA testing") ? qa : design);
        when(members.addMember(eq(context), eq(project.getId()), any())).thenReturn(mock(ProjectMember.class));
        when(subtasks.createSubtask(eq(context), eq(design.getId()), any())).thenReturn(mock(Subtask.class));
        when(checklists.createChecklist(eq(context), eq(design.getId()), any())).thenReturn(mock(Checklist.class));
        when(dependencies.createDependency(eq(context), eq(qa.getId()), any())).thenReturn(mock(Dependency.class));

        ToolOutcome outcome = setup.run(context, "set_up_project", plan());

        JsonNode result = mapper.readTree(outcome.content());
        assertThat(outcome.error()).isFalse();
        assertThat(result.get("project_created").asBoolean()).isTrue();
        assertThat(result.get("members_added").asInt()).isEqualTo(1);
        assertThat(result.get("tasks_created").asInt()).isEqualTo(2);
        assertThat(result.get("subtasks_created").asInt()).isEqualTo(2);
        assertThat(result.get("checklist_items_created").asInt()).isEqualTo(1);
        assertThat(result.get("dependencies_created").asInt()).isEqualTo(1);
        assertThat(result.get("failed")).isEmpty();
        verify(members).addMember(context, project.getId(), new AddMemberRequest(ravi, ProjectRole.MEMBER));
        verify(dependencies).createDependency(context, qa.getId(), new CreateDependencyRequest(design.getId()));
    }

    @Test
    @DisplayName("one refused step is reported and the rest of the plan still gets built")
    void reportsPartialFailures() throws Exception {
        Task design = Task.create(project.getId(), "Design homepage", null, null);
        when(tasks.createTask(eq(context), eq(project.getId()), any(CreateTaskRequest.class)))
                .thenAnswer(call -> {
                    if (((CreateTaskRequest) call.getArgument(2)).name().equals("QA testing")) {
                        throw new AuthorizationException("You cannot create tasks here");
                    }
                    return design;
                });
        when(members.addMember(eq(context), eq(project.getId()), any())).thenReturn(mock(ProjectMember.class));
        when(subtasks.createSubtask(eq(context), eq(design.getId()), any())).thenReturn(mock(Subtask.class));
        when(checklists.createChecklist(eq(context), eq(design.getId()), any())).thenReturn(mock(Checklist.class));

        JsonNode result = mapper.readTree(setup.run(context, "set_up_project", plan()).content());

        assertThat(result.get("tasks_created").asInt()).isEqualTo(1);
        assertThat(result.get("failed").toString()).contains("QA testing").contains("cannot create tasks");
        verify(dependencies, never()).createDependency(any(), any(), any());
    }

    @Test
    @DisplayName("a plan that cannot work is refused before the person is asked to approve it")
    void refusesBadPlans() {
        assertThatThrownBy(() -> setup.describe(context, "set_up_project", Map.of("tasks", List.of(Map.of("name", "x")))))
                .isInstanceOf(ValidationException.class).hasMessageContaining("name for a new project");
        assertThatThrownBy(() -> setup.describe(context, "set_up_project", Map.of("name", "P",
                "tasks", List.of(Map.of("name", "QA", "depends_on", List.of("Design"))))))
                .isInstanceOf(ValidationException.class).hasMessageContaining("not a task in this plan");
        assertThatThrownBy(() -> setup.describe(context, "set_up_project", Map.of("name", "P",
                "tasks", List.of(Map.of("name", "Write", "assignee_id", ravi.toString())))))
                .isInstanceOf(ValidationException.class).hasMessageContaining("not on the project");
        assertThatThrownBy(() -> setup.describe(context, "set_up_project", Map.of("name", "P",
                "tasks", List.of(Map.of("name", "Standup", "recurrence", "DAILY")))))
                .isInstanceOf(ValidationException.class).hasMessageContaining("needs a due date");
        assertThatThrownBy(() -> setup.describe(context, "set_up_project", Map.of("name", "P",
                "members", List.of(Map.of("user_id", ravi.toString(), "role", "OWNER")))))
                .isInstanceOf(ValidationException.class);
    }

    @Test
    @DisplayName("invitations need a real email address")
    void invitationNeedsEmail() {
        assertThat(setup.describe(context, "invite_to_workspace", Map.of("email", "meera@example.com")))
                .isEqualTo("Invite meera@example.com to the workspace");
        assertThatThrownBy(() -> setup.describe(context, "invite_to_workspace", Map.of("email", "Meera")))
                .isInstanceOf(ValidationException.class);
    }
}
