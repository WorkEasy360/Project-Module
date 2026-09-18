package com.projectmodule.work.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.projectmodule.common.context.ActorType;
import com.projectmodule.common.context.ImmutableRequestContext;
import com.projectmodule.common.context.RequestContext;
import com.projectmodule.common.exception.BusinessRuleViolationException;
import com.projectmodule.common.exception.ConflictException;
import com.projectmodule.common.exception.ResourceNotFoundException;
import com.projectmodule.common.identity.ExternalUserId;
import com.projectmodule.common.identity.OrganizationId;
import com.projectmodule.project.application.authorization.ProjectAuthorizationService;
import com.projectmodule.project.domain.Project;
import com.projectmodule.project.domain.ProjectPriority;
import com.projectmodule.work.api.dto.CreateDependencyRequest;
import com.projectmodule.work.api.dto.UpdateDependencyRequest;
import com.projectmodule.work.domain.Dependency;
import com.projectmodule.work.domain.Task;
import com.projectmodule.work.domain.event.DependencyDomainEvent;
import com.projectmodule.work.infrastructure.DependencyRepository;
import com.projectmodule.work.infrastructure.TaskRepository;
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
class DependencyApplicationServiceTest {

    private static final OrganizationId ORG = OrganizationId.of(UUID.randomUUID());
    private static final ExternalUserId USER = ExternalUserId.of(UUID.randomUUID());

    @Mock
    private DependencyRepository dependencyRepository;

    @Mock
    private TaskRepository taskRepository;

    @Mock
    private TaskApplicationService taskApplicationService;

    @Mock
    private ProjectAuthorizationService projectAuthorizationService;

    @Mock
    private WorkEventRecorder workEventRecorder;

    private DependencyApplicationService service;
    private RequestContext context;
    private Project project;
    private Task dependentTask;
    private Task prerequisiteTask;

    @BeforeEach
    void setUp() {
        service = new DependencyApplicationService(
                dependencyRepository, taskRepository, taskApplicationService,
                projectAuthorizationService, workEventRecorder);
        context = new ImmutableRequestContext(
                Optional.of(USER), Optional.of(ORG), "corr-1", ActorType.HUMAN);
        project = Project.create(ORG, "Apollo", USER, ProjectPriority.MEDIUM);
        dependentTask = Task.create(project.getId(), "Ship feature", null, null);
        prerequisiteTask = Task.create(project.getId(), "Design schema", null, null);
        // lenient: not every test path reaches the dependent-task lookup (e.g. update/archive
        // "not found" tests short-circuit at the dependency lookup itself, which comes first).
        lenient().when(taskApplicationService.findActiveTaskInCallerOrganization(context, dependentTask.getId()))
                .thenReturn(dependentTask);
    }

    @Nested
    class Create {

        @Test
        @DisplayName("creates a dependency and records DependencyCreated")
        void createsDependency() {
            when(taskRepository.findByIdAndArchivedAtIsNull(prerequisiteTask.getId()))
                    .thenReturn(Optional.of(prerequisiteTask));
            when(dependencyRepository.existsByDependentTaskIdAndPrerequisiteTaskIdAndArchivedAtIsNull(
                    dependentTask.getId(), prerequisiteTask.getId())).thenReturn(false);
            when(dependencyRepository.existsByDependentTaskIdAndPrerequisiteTaskIdAndArchivedAtIsNull(
                    prerequisiteTask.getId(), dependentTask.getId())).thenReturn(false);
            when(dependencyRepository.save(any(Dependency.class))).thenAnswer(inv -> inv.getArgument(0));

            CreateDependencyRequest request = new CreateDependencyRequest(prerequisiteTask.getId());
            Dependency created = service.createDependency(context, dependentTask.getId(), request);

            assertThat(created.getDependentTaskId()).isEqualTo(dependentTask.getId());
            assertThat(created.getPrerequisiteTaskId()).isEqualTo(prerequisiteTask.getId());
            assertThat(created.isBroken()).isFalse();
            verify(workEventRecorder).record(
                    eq(context), eq(created), eq(project.getId()), any(DependencyDomainEvent.class));
        }

        @Test
        @DisplayName("reports not found when the prerequisite task does not exist")
        void reportsPrerequisiteNotFound() {
            when(taskRepository.findByIdAndArchivedAtIsNull(prerequisiteTask.getId()))
                    .thenReturn(Optional.empty());

            CreateDependencyRequest request = new CreateDependencyRequest(prerequisiteTask.getId());

            assertThatThrownBy(() -> service.createDependency(context, dependentTask.getId(), request))
                    .isInstanceOf(ResourceNotFoundException.class);
        }

        @Test
        @DisplayName("rejects a prerequisite task from a different project")
        void rejectsCrossProjectDependency() {
            Task otherProjectTask = Task.create(UUID.randomUUID(), "Other project task", null, null);
            when(taskRepository.findByIdAndArchivedAtIsNull(otherProjectTask.getId()))
                    .thenReturn(Optional.of(otherProjectTask));

            CreateDependencyRequest request = new CreateDependencyRequest(otherProjectTask.getId());

            assertThatThrownBy(() -> service.createDependency(context, dependentTask.getId(), request))
                    .isInstanceOf(BusinessRuleViolationException.class);
            verify(dependencyRepository, never()).save(any());
        }

        @Test
        @DisplayName("rejects an exact duplicate dependency")
        void rejectsExactDuplicate() {
            when(taskRepository.findByIdAndArchivedAtIsNull(prerequisiteTask.getId()))
                    .thenReturn(Optional.of(prerequisiteTask));
            when(dependencyRepository.existsByDependentTaskIdAndPrerequisiteTaskIdAndArchivedAtIsNull(
                    dependentTask.getId(), prerequisiteTask.getId())).thenReturn(true);

            CreateDependencyRequest request = new CreateDependencyRequest(prerequisiteTask.getId());

            assertThatThrownBy(() -> service.createDependency(context, dependentTask.getId(), request))
                    .isInstanceOf(ConflictException.class);
            verify(dependencyRepository, never()).save(any());
        }

        @Test
        @DisplayName("rejects the reverse pair, which would create a two-task cycle")
        void rejectsReverseCycle() {
            when(taskRepository.findByIdAndArchivedAtIsNull(prerequisiteTask.getId()))
                    .thenReturn(Optional.of(prerequisiteTask));
            when(dependencyRepository.existsByDependentTaskIdAndPrerequisiteTaskIdAndArchivedAtIsNull(
                    dependentTask.getId(), prerequisiteTask.getId())).thenReturn(false);
            when(dependencyRepository.existsByDependentTaskIdAndPrerequisiteTaskIdAndArchivedAtIsNull(
                    prerequisiteTask.getId(), dependentTask.getId())).thenReturn(true);

            CreateDependencyRequest request = new CreateDependencyRequest(prerequisiteTask.getId());

            assertThatThrownBy(() -> service.createDependency(context, dependentTask.getId(), request))
                    .isInstanceOf(ConflictException.class);
            verify(dependencyRepository, never()).save(any());
        }
    }

    @Nested
    class List_ {

        @Test
        @DisplayName("lists dependencies for the dependent task")
        void listsDependencies() {
            Dependency dependency = Dependency.create(dependentTask.getId(), prerequisiteTask.getId());
            when(dependencyRepository.findByDependentTaskIdAndArchivedAtIsNullOrderByCreatedAtAsc(dependentTask.getId()))
                    .thenReturn(List.of(dependency));

            assertThat(service.listDependencies(context, dependentTask.getId())).containsExactly(dependency);
        }
    }

    @Nested
    class Update {

        @Test
        @DisplayName("sets broken via the broken field")
        void marksBroken() {
            Dependency dependency = Dependency.create(dependentTask.getId(), prerequisiteTask.getId());
            when(dependencyRepository.findByIdAndArchivedAtIsNull(dependency.getId()))
                    .thenReturn(Optional.of(dependency));
            when(dependencyRepository.save(any(Dependency.class))).thenAnswer(inv -> inv.getArgument(0));

            UpdateDependencyRequest request = new UpdateDependencyRequest(true, dependency.getVersion());
            Dependency updated = service.updateDependency(context, dependency.getId(), request);

            assertThat(updated.isBroken()).isTrue();
        }

        @Test
        @DisplayName("rejects setting broken back to false")
        void rejectsUnbreaking() {
            Dependency dependency = Dependency.create(dependentTask.getId(), prerequisiteTask.getId());
            dependency.markBroken();
            when(dependencyRepository.findByIdAndArchivedAtIsNull(dependency.getId()))
                    .thenReturn(Optional.of(dependency));

            UpdateDependencyRequest request = new UpdateDependencyRequest(false, dependency.getVersion());

            assertThatThrownBy(() -> service.updateDependency(context, dependency.getId(), request))
                    .isInstanceOf(BusinessRuleViolationException.class);
            verify(dependencyRepository, never()).save(any());
        }

        @Test
        @DisplayName("rejects an update against a stale version")
        void rejectsStaleVersion() {
            Dependency dependency = Dependency.create(dependentTask.getId(), prerequisiteTask.getId());
            when(dependencyRepository.findByIdAndArchivedAtIsNull(dependency.getId()))
                    .thenReturn(Optional.of(dependency));

            UpdateDependencyRequest request = new UpdateDependencyRequest(true, dependency.getVersion() + 1);

            assertThatThrownBy(() -> service.updateDependency(context, dependency.getId(), request))
                    .isInstanceOf(ConflictException.class);
            verify(dependencyRepository, never()).save(any());
        }

        @Test
        @DisplayName("reports not found for a missing dependency")
        void reportsNotFound() {
            UUID id = UUID.randomUUID();
            when(dependencyRepository.findByIdAndArchivedAtIsNull(id)).thenReturn(Optional.empty());

            UpdateDependencyRequest request = new UpdateDependencyRequest(true, 0L);

            assertThatThrownBy(() -> service.updateDependency(context, id, request))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    @Nested
    class Archive {

        @Test
        @DisplayName("archives the dependency")
        void archivesDependency() {
            Dependency dependency = Dependency.create(dependentTask.getId(), prerequisiteTask.getId());
            when(dependencyRepository.findByIdAndArchivedAtIsNull(dependency.getId()))
                    .thenReturn(Optional.of(dependency));
            when(dependencyRepository.save(any(Dependency.class))).thenAnswer(inv -> inv.getArgument(0));

            service.archiveDependency(context, dependency.getId());

            assertThat(dependency.isArchived()).isTrue();
        }
    }
}
