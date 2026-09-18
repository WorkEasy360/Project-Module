package com.projectmodule.work.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
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
import com.projectmodule.project.application.authorization.ProjectAuthorizationService;
import com.projectmodule.project.domain.Project;
import com.projectmodule.project.domain.ProjectPriority;
import com.projectmodule.work.api.dto.CreateSubtaskRequest;
import com.projectmodule.work.api.dto.UpdateSubtaskRequest;
import com.projectmodule.work.domain.Subtask;
import com.projectmodule.work.domain.Task;
import com.projectmodule.work.infrastructure.SubtaskRepository;
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
class SubtaskApplicationServiceTest {

    private static final OrganizationId ORG = OrganizationId.of(UUID.randomUUID());
    private static final ExternalUserId USER = ExternalUserId.of(UUID.randomUUID());

    @Mock
    private SubtaskRepository subtaskRepository;

    @Mock
    private TaskApplicationService taskApplicationService;

    @Mock
    private ProjectAuthorizationService projectAuthorizationService;

    private SubtaskApplicationService service;
    private RequestContext context;
    private Project project;
    private Task task;

    @BeforeEach
    void setUp() {
        service = new SubtaskApplicationService(subtaskRepository, taskApplicationService, projectAuthorizationService);
        context = new ImmutableRequestContext(
                Optional.of(USER), Optional.of(ORG), "corr-1", ActorType.HUMAN);
        project = Project.create(ORG, "Apollo", USER, ProjectPriority.MEDIUM);
        task = Task.create(project.getId(), "Implement login", null, null);
        // lenient: the not-found test path never reaches this call, since the subtask lookup
        // (which comes first) already throws for it.
        lenient().when(taskApplicationService.findActiveTaskInCallerOrganization(context, task.getId()))
                .thenReturn(task);
    }

    @Nested
    class Create {

        @Test
        @DisplayName("creates a subtask, not completed")
        void createsSubtask() {
            when(subtaskRepository.save(any(Subtask.class))).thenAnswer(inv -> inv.getArgument(0));

            CreateSubtaskRequest request = new CreateSubtaskRequest("Write unit tests", null);
            Subtask created = service.createSubtask(context, task.getId(), request);

            assertThat(created.getName()).isEqualTo("Write unit tests");
            assertThat(created.isCompleted()).isFalse();
            assertThat(created.getTaskId()).isEqualTo(task.getId());
        }
    }

    @Nested
    class List_ {

        @Test
        @DisplayName("lists subtasks for the task")
        void listsSubtasks() {
            Subtask subtask = Subtask.create(task.getId(), "Write unit tests");
            when(subtaskRepository.findByTaskIdAndArchivedAtIsNullOrderByCreatedAtAsc(task.getId()))
                    .thenReturn(List.of(subtask));

            assertThat(service.listSubtasks(context, task.getId())).containsExactly(subtask);
        }
    }

    @Nested
    class Update {

        @Test
        @DisplayName("toggles completion via the completed field")
        void togglesCompletion() {
            Subtask subtask = Subtask.create(task.getId(), "Write unit tests");
            when(subtaskRepository.findByIdAndArchivedAtIsNull(subtask.getId())).thenReturn(Optional.of(subtask));
            when(subtaskRepository.save(any(Subtask.class))).thenAnswer(inv -> inv.getArgument(0));

            UpdateSubtaskRequest request = new UpdateSubtaskRequest(null, null, true, subtask.getVersion());
            Subtask updated = service.updateSubtask(context, subtask.getId(), request);

            assertThat(updated.isCompleted()).isTrue();
        }

        @Test
        @DisplayName("rejects an update against a stale version")
        void rejectsStaleVersion() {
            Subtask subtask = Subtask.create(task.getId(), "Write unit tests");
            when(subtaskRepository.findByIdAndArchivedAtIsNull(subtask.getId())).thenReturn(Optional.of(subtask));

            UpdateSubtaskRequest request = new UpdateSubtaskRequest(
                    "Renamed", null, null, subtask.getVersion() + 1);

            assertThatThrownBy(() -> service.updateSubtask(context, subtask.getId(), request))
                    .isInstanceOf(ConflictException.class);
            verify(subtaskRepository, never()).save(any());
        }

        @Test
        @DisplayName("reports not found for a missing subtask")
        void reportsNotFound() {
            UUID id = UUID.randomUUID();
            when(subtaskRepository.findByIdAndArchivedAtIsNull(id)).thenReturn(Optional.empty());

            UpdateSubtaskRequest request = new UpdateSubtaskRequest("Renamed", null, null, 0L);

            assertThatThrownBy(() -> service.updateSubtask(context, id, request))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    @Nested
    class Archive {

        @Test
        @DisplayName("archives the subtask")
        void archivesSubtask() {
            Subtask subtask = Subtask.create(task.getId(), "Write unit tests");
            when(subtaskRepository.findByIdAndArchivedAtIsNull(subtask.getId())).thenReturn(Optional.of(subtask));
            when(subtaskRepository.save(any(Subtask.class))).thenAnswer(inv -> inv.getArgument(0));

            service.archiveSubtask(context, subtask.getId());

            assertThat(subtask.isArchived()).isTrue();
        }
    }
}
