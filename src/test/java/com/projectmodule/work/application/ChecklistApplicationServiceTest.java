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
import com.projectmodule.work.api.dto.CreateChecklistRequest;
import com.projectmodule.work.api.dto.UpdateChecklistRequest;
import com.projectmodule.work.domain.Checklist;
import com.projectmodule.work.domain.Task;
import com.projectmodule.work.infrastructure.ChecklistRepository;
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
class ChecklistApplicationServiceTest {

    private static final OrganizationId ORG = OrganizationId.of(UUID.randomUUID());
    private static final ExternalUserId USER = ExternalUserId.of(UUID.randomUUID());

    @Mock
    private ChecklistRepository checklistRepository;

    @Mock
    private TaskApplicationService taskApplicationService;

    @Mock
    private ProjectAuthorizationService projectAuthorizationService;

    private ChecklistApplicationService service;
    private RequestContext context;
    private Project project;
    private Task task;

    @BeforeEach
    void setUp() {
        service = new ChecklistApplicationService(
                checklistRepository, taskApplicationService, projectAuthorizationService);
        context = new ImmutableRequestContext(
                Optional.of(USER), Optional.of(ORG), "corr-1", ActorType.HUMAN);
        project = Project.create(ORG, "Apollo", USER, ProjectPriority.MEDIUM);
        task = Task.create(project.getId(), "Implement login", null, null);
        lenient().when(taskApplicationService.findActiveTaskInCallerOrganization(context, task.getId()))
                .thenReturn(task);
    }

    @Nested
    class Create {

        @Test
        @DisplayName("creates a checklist line, unchecked")
        void createsChecklist() {
            when(checklistRepository.save(any(Checklist.class))).thenAnswer(inv -> inv.getArgument(0));

            CreateChecklistRequest request = new CreateChecklistRequest("Add password validation", null);
            Checklist created = service.createChecklist(context, task.getId(), request);

            assertThat(created.getText()).isEqualTo("Add password validation");
            assertThat(created.isChecked()).isFalse();
            assertThat(created.getTaskId()).isEqualTo(task.getId());
        }
    }

    @Nested
    class List_ {

        @Test
        @DisplayName("lists checklist lines for the task")
        void listsChecklists() {
            Checklist checklist = Checklist.create(task.getId(), null, "Add password validation");
            when(checklistRepository.findByTaskIdAndArchivedAtIsNullOrderByCreatedAtAsc(task.getId()))
                    .thenReturn(List.of(checklist));

            assertThat(service.listChecklists(context, task.getId())).containsExactly(checklist);
        }
    }

    @Nested
    class Update {

        @Test
        @DisplayName("toggles checked via the checked field")
        void togglesChecked() {
            Checklist checklist = Checklist.create(task.getId(), null, "Add password validation");
            when(checklistRepository.findByIdAndArchivedAtIsNull(checklist.getId()))
                    .thenReturn(Optional.of(checklist));
            when(checklistRepository.save(any(Checklist.class))).thenAnswer(inv -> inv.getArgument(0));

            UpdateChecklistRequest request = new UpdateChecklistRequest(null, true, null, checklist.getVersion());
            Checklist updated = service.updateChecklist(context, checklist.getId(), request);

            assertThat(updated.isChecked()).isTrue();
        }

        @Test
        @DisplayName("rejects an update against a stale version")
        void rejectsStaleVersion() {
            Checklist checklist = Checklist.create(task.getId(), null, "Add password validation");
            when(checklistRepository.findByIdAndArchivedAtIsNull(checklist.getId()))
                    .thenReturn(Optional.of(checklist));

            UpdateChecklistRequest request = new UpdateChecklistRequest(
                    "Renamed", null, null, checklist.getVersion() + 1);

            assertThatThrownBy(() -> service.updateChecklist(context, checklist.getId(), request))
                    .isInstanceOf(ConflictException.class);
            verify(checklistRepository, never()).save(any());
        }

        @Test
        @DisplayName("reports not found for a missing checklist line")
        void reportsNotFound() {
            UUID id = UUID.randomUUID();
            when(checklistRepository.findByIdAndArchivedAtIsNull(id)).thenReturn(Optional.empty());

            UpdateChecklistRequest request = new UpdateChecklistRequest("Renamed", null, null, 0L);

            assertThatThrownBy(() -> service.updateChecklist(context, id, request))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    @Nested
    class Archive {

        @Test
        @DisplayName("archives the checklist line")
        void archivesChecklist() {
            Checklist checklist = Checklist.create(task.getId(), null, "Add password validation");
            when(checklistRepository.findByIdAndArchivedAtIsNull(checklist.getId()))
                    .thenReturn(Optional.of(checklist));
            when(checklistRepository.save(any(Checklist.class))).thenAnswer(inv -> inv.getArgument(0));

            service.archiveChecklist(context, checklist.getId());

            assertThat(checklist.isArchived()).isTrue();
        }
    }
}
