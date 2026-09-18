package com.projectmodule.work.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.projectmodule.common.api.SortDirection;
import com.projectmodule.common.context.ActorType;
import com.projectmodule.common.context.ImmutableRequestContext;
import com.projectmodule.common.context.RequestContext;
import com.projectmodule.common.exception.AuthorizationException;
import com.projectmodule.common.exception.BusinessRuleViolationException;
import com.projectmodule.common.exception.ConflictException;
import com.projectmodule.common.exception.ResourceNotFoundException;
import com.projectmodule.common.identity.ExternalUserId;
import com.projectmodule.common.identity.OrganizationId;
import com.projectmodule.project.application.ProjectApplicationService;
import com.projectmodule.project.application.authorization.ProjectAuthorizationService;
import com.projectmodule.project.application.authorization.ProjectPermission;
import com.projectmodule.project.domain.Project;
import com.projectmodule.project.domain.ProjectPriority;
import com.projectmodule.work.api.dto.CreateTaskRequest;
import com.projectmodule.work.api.dto.UpdateTaskRequest;
import com.projectmodule.work.domain.Task;
import com.projectmodule.work.domain.TaskSortField;
import com.projectmodule.work.domain.TaskStatus;
import com.projectmodule.work.domain.event.TaskDomainEvent;
import com.projectmodule.work.infrastructure.TaskRepository;
import java.time.LocalDate;
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
import org.springframework.data.domain.Sort;

@ExtendWith(MockitoExtension.class)
class TaskApplicationServiceTest {

    private static final OrganizationId ORG = OrganizationId.of(UUID.randomUUID());
    private static final ExternalUserId USER = ExternalUserId.of(UUID.randomUUID());

    @Mock
    private TaskRepository taskRepository;

    @Mock
    private ProjectApplicationService projectApplicationService;

    @Mock
    private ProjectAuthorizationService projectAuthorizationService;

    @Mock
    private WorkEventRecorder workEventRecorder;

    private TaskApplicationService service;
    private RequestContext context;
    private Project project;

    @BeforeEach
    void setUp() {
        service = new TaskApplicationService(
                taskRepository, projectApplicationService, projectAuthorizationService, workEventRecorder);
        context = new ImmutableRequestContext(
                Optional.of(USER), Optional.of(ORG), "corr-1", ActorType.HUMAN);
        project = Project.create(ORG, "Apollo", USER, ProjectPriority.MEDIUM);
    }

    @Nested
    class Create {

        @Test
        @DisplayName("creates a task TODO and records exactly one event")
        void createsTask() {
            when(projectApplicationService.findActiveProjectInCallerOrganization(context, project.getId()))
                    .thenReturn(project);
            when(taskRepository.save(any(Task.class))).thenAnswer(inv -> inv.getArgument(0));

            CreateTaskRequest request = new CreateTaskRequest(
                    "Implement login", "OAuth flow", LocalDate.of(2026, 3, 1), null);
            Task created = service.createTask(context, project.getId(), request);

            assertThat(created.getStatus()).isEqualTo(TaskStatus.TODO);
            assertThat(created.getName()).isEqualTo("Implement login");
            verify(workEventRecorder).record(eq(context), eq(created), any(TaskDomainEvent.class));
        }

        @Test
        @DisplayName("denies creation without EDIT_PROJECT")
        void deniesWithoutPermission() {
            when(projectApplicationService.findActiveProjectInCallerOrganization(context, project.getId()))
                    .thenReturn(project);
            doThrow(new AuthorizationException("denied"))
                    .when(projectAuthorizationService)
                    .requirePermission(USER, project.getId(), ProjectPermission.EDIT_PROJECT);

            CreateTaskRequest request = new CreateTaskRequest("Implement login", null, null, null);

            assertThatThrownBy(() -> service.createTask(context, project.getId(), request))
                    .isInstanceOf(AuthorizationException.class);
            verify(taskRepository, never()).save(any());
        }
    }

    @Nested
    class Get {

        @Test
        @DisplayName("returns a task after checking VIEW_PROJECT")
        void returnsTask() {
            Task task = Task.create(project.getId(), "Implement login", null, null);
            when(taskRepository.findByIdAndArchivedAtIsNull(task.getId())).thenReturn(Optional.of(task));
            when(projectApplicationService.findActiveProjectInCallerOrganization(context, project.getId()))
                    .thenReturn(project);

            assertThat(service.getTask(context, task.getId())).isEqualTo(task);
            verify(projectAuthorizationService).requirePermission(
                    USER, project.getId(), ProjectPermission.VIEW_PROJECT);
        }

        @Test
        @DisplayName("reports not found for a missing task")
        void reportsNotFound() {
            UUID id = UUID.randomUUID();
            when(taskRepository.findByIdAndArchivedAtIsNull(id)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.getTask(context, id))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    @Nested
    class List_ {

        @Test
        @DisplayName("lists tasks for the project")
        void listsTasks() {
            Task task = Task.create(project.getId(), "Implement login", null, null);
            when(projectApplicationService.findActiveProjectInCallerOrganization(context, project.getId()))
                    .thenReturn(project);
            when(taskRepository.findByProjectIdAndArchivedAtIsNullOrderByCreatedAtAsc(project.getId()))
                    .thenReturn(List.of(task));

            assertThat(service.listTasks(context, project.getId())).containsExactly(task);
        }
    }

    /**
     * Coverage for {@code docs/project/14-FILTERS-SPEC.md}: the filtered {@code listTasks}
     * overload.
     */
    @Nested
    class ListFiltered {

        private static final Sort DEFAULT_SORT = Sort.by(Sort.Direction.ASC, "createdAt");

        @Test
        @DisplayName("no filters supplied delegates to the repository with all null/false and the default sort")
        void noFiltersMatchesUnfilteredBehavior() {
            Task task = Task.create(project.getId(), "Implement login", null, null);
            when(projectApplicationService.findActiveProjectInCallerOrganization(context, project.getId()))
                    .thenReturn(project);
            when(taskRepository.findByFilters(project.getId(), null, null, null, null, false, DEFAULT_SORT))
                    .thenReturn(List.of(task));

            assertThat(service.listTasks(context, project.getId(), null, null, null, null, false, null, null))
                    .containsExactly(task);
        }

        @Test
        @DisplayName("filters by status alone")
        void filtersByStatus() {
            Task task = Task.create(project.getId(), "Implement login", null, null);
            task.markBlocked();
            when(projectApplicationService.findActiveProjectInCallerOrganization(context, project.getId()))
                    .thenReturn(project);
            when(taskRepository.findByFilters(project.getId(), TaskStatus.BLOCKED, null, null, null, false, DEFAULT_SORT))
                    .thenReturn(List.of(task));

            assertThat(service.listTasks(context, project.getId(), TaskStatus.BLOCKED, null, null, null, false, null, null))
                    .containsExactly(task);
        }

        @Test
        @DisplayName("filters by assigneeId and a due date range combined (AND)")
        void combinesMultipleFilters() {
            UUID assigneeId = UUID.randomUUID();
            LocalDate before = LocalDate.of(2026, 6, 1);
            LocalDate after = LocalDate.of(2026, 1, 1);
            Task task = Task.create(project.getId(), "Implement login", null, null);
            when(projectApplicationService.findActiveProjectInCallerOrganization(context, project.getId()))
                    .thenReturn(project);
            when(taskRepository.findByFilters(project.getId(), null, assigneeId, before, after, false, DEFAULT_SORT))
                    .thenReturn(List.of(task));

            assertThat(service.listTasks(context, project.getId(), null, assigneeId, before, after, false, null, null))
                    .containsExactly(task);
            verify(taskRepository).findByFilters(project.getId(), null, assigneeId, before, after, false, DEFAULT_SORT);
        }

        @Test
        @DisplayName("archived=true returns only archived tasks")
        void archivedTrueReturnsOnlyArchived() {
            Task task = Task.create(project.getId(), "Legacy", null, null);
            task.archive(ExternalUserId.of(UUID.randomUUID()));
            when(projectApplicationService.findActiveProjectInCallerOrganization(context, project.getId()))
                    .thenReturn(project);
            when(taskRepository.findByFilters(project.getId(), null, null, null, null, true, DEFAULT_SORT))
                    .thenReturn(List.of(task));

            assertThat(service.listTasks(context, project.getId(), null, null, null, null, true, null, null))
                    .containsExactly(task);
        }

        @Test
        @DisplayName("sorts by dueDate descending when requested, per docs/project/15-LIST-SPEC.md")
        void sortsByRequestedFieldAndDirection() {
            Task task = Task.create(project.getId(), "Implement login", null, null);
            Sort dueDateDesc = Sort.by(Sort.Direction.DESC, "dueDate");
            when(projectApplicationService.findActiveProjectInCallerOrganization(context, project.getId()))
                    .thenReturn(project);
            when(taskRepository.findByFilters(project.getId(), null, null, null, null, false, dueDateDesc))
                    .thenReturn(List.of(task));

            assertThat(service.listTasks(context, project.getId(), null, null, null, null, false,
                    TaskSortField.DUE_DATE, SortDirection.DESC)).containsExactly(task);
        }

        @Test
        @DisplayName("requires VIEW_PROJECT")
        void requiresViewProjectPermission() {
            when(projectApplicationService.findActiveProjectInCallerOrganization(context, project.getId()))
                    .thenReturn(project);
            doThrow(new AuthorizationException("denied"))
                    .when(projectAuthorizationService)
                    .requirePermission(USER, project.getId(), ProjectPermission.VIEW_PROJECT);

            assertThatThrownBy(() -> service.listTasks(context, project.getId(), null, null, null, null, false, null, null))
                    .isInstanceOf(AuthorizationException.class);
        }
    }

    /** Coverage for {@code docs/project/16-KANBAN-SPEC.md}: {@code getBoard}. */
    @Nested
    class Board {

        @Test
        @DisplayName("groups active tasks by status, every column present including empty ones")
        void groupsTasksByStatusWithEmptyColumnsPresent() {
            Task todo = Task.create(project.getId(), "Todo task", null, null);
            Task blocked = Task.create(project.getId(), "Blocked task", null, null);
            blocked.markBlocked();
            when(projectApplicationService.findActiveProjectInCallerOrganization(context, project.getId()))
                    .thenReturn(project);
            when(taskRepository.findByProjectIdAndArchivedAtIsNullOrderByCreatedAtAsc(project.getId()))
                    .thenReturn(List.of(todo, blocked));

            var board = service.getBoard(context, project.getId());

            assertThat(board.keySet()).containsExactlyInAnyOrder(TaskStatus.values());
            assertThat(board.get(TaskStatus.TODO)).containsExactly(todo);
            assertThat(board.get(TaskStatus.BLOCKED)).containsExactly(blocked);
            assertThat(board.get(TaskStatus.OVERDUE)).isEmpty();
            assertThat(board.get(TaskStatus.COMPLETED)).isEmpty();
        }

        @Test
        @DisplayName("requires VIEW_PROJECT")
        void requiresViewProjectPermission() {
            when(projectApplicationService.findActiveProjectInCallerOrganization(context, project.getId()))
                    .thenReturn(project);
            doThrow(new AuthorizationException("denied"))
                    .when(projectAuthorizationService)
                    .requirePermission(USER, project.getId(), ProjectPermission.VIEW_PROJECT);

            assertThatThrownBy(() -> service.getBoard(context, project.getId()))
                    .isInstanceOf(AuthorizationException.class);
        }
    }

    @Nested
    class Update {

        @Test
        @DisplayName("a plain field edit records exactly one task.updated event")
        void plainEditRecordsOneEvent() {
            Task task = Task.create(project.getId(), "Implement login", null, null);
            task.pullDomainEvents();
            when(taskRepository.findByIdAndArchivedAtIsNull(task.getId())).thenReturn(Optional.of(task));
            when(projectApplicationService.findActiveProjectInCallerOrganization(context, project.getId()))
                    .thenReturn(project);
            when(taskRepository.save(any(Task.class))).thenAnswer(inv -> inv.getArgument(0));

            UpdateTaskRequest request = new UpdateTaskRequest(
                    "Renamed", "New description", null, null, null, task.getVersion());
            Task updated = service.updateTask(context, task.getId(), request);

            assertThat(updated.getName()).isEqualTo("Renamed");
            verify(workEventRecorder).record(eq(context), eq(updated), any(TaskDomainEvent.class));
        }

        @Test
        @DisplayName("status COMPLETED routes to complete()")
        void statusCompletedRoutesToComplete() {
            Task task = Task.create(project.getId(), "Implement login", null, null);
            when(taskRepository.findByIdAndArchivedAtIsNull(task.getId())).thenReturn(Optional.of(task));
            when(projectApplicationService.findActiveProjectInCallerOrganization(context, project.getId()))
                    .thenReturn(project);
            when(taskRepository.save(any(Task.class))).thenAnswer(inv -> inv.getArgument(0));

            UpdateTaskRequest request = new UpdateTaskRequest(
                    null, null, null, null, TaskStatus.COMPLETED, task.getVersion());
            Task updated = service.updateTask(context, task.getId(), request);

            assertThat(updated.getStatus()).isEqualTo(TaskStatus.COMPLETED);
        }

        @Test
        @DisplayName("status TODO is rejected - no event exists for that transition")
        void statusTodoIsRejected() {
            Task task = Task.create(project.getId(), "Implement login", null, null);
            task.markBlocked();
            when(taskRepository.findByIdAndArchivedAtIsNull(task.getId())).thenReturn(Optional.of(task));
            when(projectApplicationService.findActiveProjectInCallerOrganization(context, project.getId()))
                    .thenReturn(project);

            UpdateTaskRequest request = new UpdateTaskRequest(
                    null, null, null, null, TaskStatus.TODO, task.getVersion());

            assertThatThrownBy(() -> service.updateTask(context, task.getId(), request))
                    .isInstanceOf(BusinessRuleViolationException.class);
        }

        @Test
        @DisplayName("assigning via PATCH records task.assigned")
        void assignsViaPatch() {
            Task task = Task.create(project.getId(), "Implement login", null, null);
            when(taskRepository.findByIdAndArchivedAtIsNull(task.getId())).thenReturn(Optional.of(task));
            when(projectApplicationService.findActiveProjectInCallerOrganization(context, project.getId()))
                    .thenReturn(project);
            when(taskRepository.save(any(Task.class))).thenAnswer(inv -> inv.getArgument(0));

            UUID assigneeId = UUID.randomUUID();
            UpdateTaskRequest request = new UpdateTaskRequest(
                    null, null, null, assigneeId, null, task.getVersion());
            Task updated = service.updateTask(context, task.getId(), request);

            assertThat(updated.getAssigneeId()).contains(ExternalUserId.of(assigneeId));
        }

        @Test
        @DisplayName("rejects an update against a stale version")
        void rejectsStaleVersion() {
            Task task = Task.create(project.getId(), "Implement login", null, null);
            when(taskRepository.findByIdAndArchivedAtIsNull(task.getId())).thenReturn(Optional.of(task));
            when(projectApplicationService.findActiveProjectInCallerOrganization(context, project.getId()))
                    .thenReturn(project);

            UpdateTaskRequest request = new UpdateTaskRequest(
                    "Renamed", null, null, null, null, task.getVersion() + 1);

            assertThatThrownBy(() -> service.updateTask(context, task.getId(), request))
                    .isInstanceOf(ConflictException.class);
            verify(taskRepository, never()).save(any());
        }
    }

    @Nested
    class Archive {

        @Test
        @DisplayName("archives the task and records no event")
        void archivesWithNoEvent() {
            Task task = Task.create(project.getId(), "Implement login", null, null);
            when(taskRepository.findByIdAndArchivedAtIsNull(task.getId())).thenReturn(Optional.of(task));
            when(projectApplicationService.findActiveProjectInCallerOrganization(context, project.getId()))
                    .thenReturn(project);
            when(taskRepository.save(any(Task.class))).thenAnswer(inv -> inv.getArgument(0));

            service.archiveTask(context, task.getId());

            assertThat(task.isArchived()).isTrue();
            verify(workEventRecorder, never()).record(any(), any(), any(TaskDomainEvent.class));
        }
    }
}
