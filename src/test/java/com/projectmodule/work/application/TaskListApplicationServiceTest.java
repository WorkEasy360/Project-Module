package com.projectmodule.work.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.projectmodule.common.context.ActorType;
import com.projectmodule.common.context.ImmutableRequestContext;
import com.projectmodule.common.context.RequestContext;
import com.projectmodule.common.exception.ConflictException;
import com.projectmodule.common.exception.ResourceNotFoundException;
import com.projectmodule.common.identity.ExternalUserId;
import com.projectmodule.common.identity.OrganizationId;
import com.projectmodule.project.application.ProjectApplicationService;
import com.projectmodule.project.application.authorization.ProjectAuthorizationService;
import com.projectmodule.project.domain.Project;
import com.projectmodule.project.domain.ProjectPriority;
import com.projectmodule.work.api.dto.CreateTaskListRequest;
import com.projectmodule.work.api.dto.UpdateTaskListRequest;
import com.projectmodule.work.domain.TaskList;
import com.projectmodule.work.infrastructure.TaskListRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TaskListApplicationServiceTest {

    private static final OrganizationId ORG = OrganizationId.of(UUID.randomUUID());
    private static final ExternalUserId USER = ExternalUserId.of(UUID.randomUUID());

    @Mock
    private TaskListRepository taskListRepository;

    @Mock
    private ProjectApplicationService projectApplicationService;

    @Mock
    private ProjectAuthorizationService projectAuthorizationService;

    private TaskListApplicationService service;
    private RequestContext context;
    private Project project;

    @BeforeEach
    void setUp() {
        service = new TaskListApplicationService(
                taskListRepository, projectApplicationService, projectAuthorizationService);
        context = new ImmutableRequestContext(
                Optional.of(USER), Optional.of(ORG), "corr-1", ActorType.HUMAN);
        project = Project.create(ORG, "Apollo", USER, ProjectPriority.MEDIUM);
    }

    @Nested
    class Create {

        @Test
        @DisplayName("creates a task list")
        void createsTaskList() {
            when(projectApplicationService.findActiveProjectInCallerOrganization(context, project.getId()))
                    .thenReturn(project);
            when(taskListRepository.save(any(TaskList.class))).thenAnswer(inv -> inv.getArgument(0));

            CreateTaskListRequest request = new CreateTaskListRequest("Backend", "Server work", null);
            TaskList created = service.createTaskList(context, project.getId(), request);

            assertThat(created.getName()).isEqualTo("Backend");
            assertThat(created.getProjectId()).isEqualTo(project.getId());
        }
    }

    @Nested
    class List_ {

        @Test
        @DisplayName("lists task lists for the project")
        void listsTaskLists() {
            TaskList taskList = TaskList.create(project.getId(), null, "Backend");
            when(projectApplicationService.findActiveProjectInCallerOrganization(context, project.getId()))
                    .thenReturn(project);
            when(taskListRepository.findByProjectIdAndArchivedAtIsNullOrderByCreatedAtAsc(project.getId()))
                    .thenReturn(List.of(taskList));

            assertThat(service.listTaskLists(context, project.getId())).containsExactly(taskList);
        }
    }

    @Nested
    class Update {

        @Test
        @DisplayName("applies only the fields supplied")
        void appliesSuppliedFields() {
            TaskList taskList = TaskList.create(project.getId(), null, "Backend");
            when(taskListRepository.findByIdAndArchivedAtIsNull(taskList.getId()))
                    .thenReturn(Optional.of(taskList));
            when(projectApplicationService.findActiveProjectInCallerOrganization(context, project.getId()))
                    .thenReturn(project);
            when(taskListRepository.save(any(TaskList.class))).thenAnswer(inv -> inv.getArgument(0));

            UpdateTaskListRequest request = new UpdateTaskListRequest("Renamed", null, null, taskList.getVersion());
            TaskList updated = service.updateTaskList(context, taskList.getId(), request);

            assertThat(updated.getName()).isEqualTo("Renamed");
        }

        @Test
        @DisplayName("rejects an update against a stale version")
        void rejectsStaleVersion() {
            TaskList taskList = TaskList.create(project.getId(), null, "Backend");
            when(taskListRepository.findByIdAndArchivedAtIsNull(taskList.getId()))
                    .thenReturn(Optional.of(taskList));
            when(projectApplicationService.findActiveProjectInCallerOrganization(context, project.getId()))
                    .thenReturn(project);

            UpdateTaskListRequest request = new UpdateTaskListRequest(
                    "Renamed", null, null, taskList.getVersion() + 1);

            assertThatThrownBy(() -> service.updateTaskList(context, taskList.getId(), request))
                    .isInstanceOf(ConflictException.class);
            verify(taskListRepository, never()).save(any());
        }

        @Test
        @DisplayName("reports not found for a missing task list")
        void reportsNotFound() {
            UUID id = UUID.randomUUID();
            when(taskListRepository.findByIdAndArchivedAtIsNull(id)).thenReturn(Optional.empty());

            UpdateTaskListRequest request = new UpdateTaskListRequest("Renamed", null, null, 0L);

            assertThatThrownBy(() -> service.updateTaskList(context, id, request))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    @Nested
    class Archive {

        @Test
        @DisplayName("archives the task list")
        void archivesTaskList() {
            TaskList taskList = TaskList.create(project.getId(), null, "Backend");
            when(taskListRepository.findByIdAndArchivedAtIsNull(taskList.getId()))
                    .thenReturn(Optional.of(taskList));
            when(projectApplicationService.findActiveProjectInCallerOrganization(context, project.getId()))
                    .thenReturn(project);
            when(taskListRepository.save(any(TaskList.class))).thenAnswer(inv -> inv.getArgument(0));

            service.archiveTaskList(context, taskList.getId());

            assertThat(taskList.isArchived()).isTrue();
        }
    }
}
