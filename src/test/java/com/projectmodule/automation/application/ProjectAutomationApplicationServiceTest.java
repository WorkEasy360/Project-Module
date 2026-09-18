package com.projectmodule.automation.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.projectmodule.automation.domain.AutomationActionType;
import com.projectmodule.automation.domain.AutomationRun;
import com.projectmodule.automation.domain.ProjectAutomation;
import com.projectmodule.automation.infrastructure.AutomationRunRepository;
import com.projectmodule.automation.infrastructure.ProjectAutomationRepository;
import com.projectmodule.common.context.ActorType;
import com.projectmodule.common.context.ImmutableRequestContext;
import com.projectmodule.common.context.RequestContext;
import com.projectmodule.common.exception.AuthorizationException;
import com.projectmodule.common.exception.ConflictException;
import com.projectmodule.common.exception.ResourceNotFoundException;
import com.projectmodule.common.identity.ExternalUserId;
import com.projectmodule.common.identity.OrganizationId;
import com.projectmodule.project.application.ProjectApplicationService;
import com.projectmodule.project.application.authorization.ProjectAuthorizationService;
import com.projectmodule.project.application.authorization.ProjectPermission;
import com.projectmodule.project.domain.Project;
import com.projectmodule.project.domain.ProjectPriority;
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
class ProjectAutomationApplicationServiceTest {

    private static final OrganizationId ORG = OrganizationId.of(UUID.randomUUID());
    private static final ExternalUserId USER = ExternalUserId.of(UUID.randomUUID());

    @Mock
    private ProjectAutomationRepository projectAutomationRepository;

    @Mock
    private AutomationRunRepository automationRunRepository;

    @Mock
    private ProjectApplicationService projectApplicationService;

    @Mock
    private ProjectAuthorizationService projectAuthorizationService;

    private ProjectAutomationApplicationService service;
    private RequestContext context;
    private Project project;

    @BeforeEach
    void setUp() {
        service = new ProjectAutomationApplicationService(projectAutomationRepository, automationRunRepository,
                projectApplicationService, projectAuthorizationService);
        context = new ImmutableRequestContext(Optional.of(USER), Optional.of(ORG), "corr-1", ActorType.HUMAN);
        project = Project.create(ORG, "Apollo", USER, ProjectPriority.MEDIUM);
    }

    private ProjectAutomation sampleAutomation() {
        return ProjectAutomation.create(project.getId(), "Notify on overdue", "task.overdue",
                AutomationActionType.NOTIFY, UUID.randomUUID(), null, "A task is overdue");
    }

    @Nested
    class CreateAutomation {

        @Test
        @DisplayName("creates and persists an automation")
        void createsAutomation() {
            when(projectApplicationService.findActiveProjectInCallerOrganization(context, project.getId()))
                    .thenReturn(project);
            when(projectAutomationRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            ProjectAutomation created = service.createAutomation(context, project.getId(), "Notify on overdue",
                    "desc", "task.overdue", AutomationActionType.NOTIFY, UUID.randomUUID(), null,
                    "A task is overdue");

            assertThat(created.getName()).isEqualTo("Notify on overdue");
            assertThat(created.getDescription()).contains("desc");
        }

        @Test
        @DisplayName("requires EDIT_PROJECT")
        void requiresEditProjectPermission() {
            when(projectApplicationService.findActiveProjectInCallerOrganization(context, project.getId()))
                    .thenReturn(project);
            doThrow(new AuthorizationException("denied"))
                    .when(projectAuthorizationService)
                    .requirePermission(USER, project.getId(), ProjectPermission.EDIT_PROJECT);

            assertThatThrownBy(() -> service.createAutomation(context, project.getId(), "n", null,
                    "task.overdue", AutomationActionType.NOTIFY, UUID.randomUUID(), null, "m"))
                    .isInstanceOf(AuthorizationException.class);
            verify(projectAutomationRepository, never()).save(any());
        }
    }

    @Nested
    class ListAutomations {

        @Test
        @DisplayName("requires VIEW_PROJECT")
        void requiresViewProjectPermission() {
            when(projectApplicationService.findActiveProjectInCallerOrganization(context, project.getId()))
                    .thenReturn(project);
            doThrow(new AuthorizationException("denied"))
                    .when(projectAuthorizationService)
                    .requirePermission(USER, project.getId(), ProjectPermission.VIEW_PROJECT);

            assertThatThrownBy(() -> service.listAutomations(context, project.getId()))
                    .isInstanceOf(AuthorizationException.class);
        }

        @Test
        @DisplayName("lists automations for the project")
        void listsAutomations() {
            ProjectAutomation automation = sampleAutomation();
            when(projectApplicationService.findActiveProjectInCallerOrganization(context, project.getId()))
                    .thenReturn(project);
            when(projectAutomationRepository.findByProjectIdAndArchivedAtIsNullOrderByCreatedAtAsc(project.getId()))
                    .thenReturn(List.of(automation));

            assertThat(service.listAutomations(context, project.getId())).containsExactly(automation);
        }
    }

    @Nested
    class UpdateAutomation {

        @Test
        @DisplayName("not found")
        void reportsNotFound() {
            UUID id = UUID.randomUUID();
            when(projectAutomationRepository.findByIdAndArchivedAtIsNull(id)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.updateAutomation(context, id, "n", null, null, null, 0L))
                    .isInstanceOf(ResourceNotFoundException.class);
        }

        @Test
        @DisplayName("rejects an update against a stale version")
        void rejectsStaleVersion() {
            ProjectAutomation automation = sampleAutomation();
            when(projectAutomationRepository.findByIdAndArchivedAtIsNull(automation.getId()))
                    .thenReturn(Optional.of(automation));
            when(projectApplicationService.findActiveProjectInCallerOrganization(context, project.getId()))
                    .thenReturn(project);

            assertThatThrownBy(() -> service.updateAutomation(
                    context, automation.getId(), "n", null, null, null, automation.getVersion() + 1))
                    .isInstanceOf(ConflictException.class);
            verify(projectAutomationRepository, never()).save(any());
        }

        @Test
        @DisplayName("applies a plain field edit and toggles enabled")
        void appliesEditsAndTogglesEnabled() {
            ProjectAutomation automation = sampleAutomation();
            when(projectAutomationRepository.findByIdAndArchivedAtIsNull(automation.getId()))
                    .thenReturn(Optional.of(automation));
            when(projectApplicationService.findActiveProjectInCallerOrganization(context, project.getId()))
                    .thenReturn(project);
            when(projectAutomationRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            ProjectAutomation updated = service.updateAutomation(
                    context, automation.getId(), "Renamed", null, null, false, automation.getVersion());

            assertThat(updated.getName()).isEqualTo("Renamed");
            assertThat(updated.isEnabled()).isFalse();
        }
    }

    @Nested
    class ArchiveAutomation {

        @Test
        @DisplayName("archives the automation")
        void archivesAutomation() {
            ProjectAutomation automation = sampleAutomation();
            when(projectAutomationRepository.findByIdAndArchivedAtIsNull(automation.getId()))
                    .thenReturn(Optional.of(automation));
            when(projectApplicationService.findActiveProjectInCallerOrganization(context, project.getId()))
                    .thenReturn(project);
            when(projectAutomationRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            service.archiveAutomation(context, automation.getId());

            assertThat(automation.isArchived()).isTrue();
        }
    }

    @Nested
    class ListRuns {

        @Test
        @DisplayName("returns run history even for an archived automation")
        void returnsHistoryForArchivedAutomation() {
            ProjectAutomation automation = sampleAutomation();
            automation.archive(USER);
            AutomationRun run = AutomationRun.succeeded(automation.getId(), project.getId(), "task.overdue");
            when(projectAutomationRepository.findById(automation.getId())).thenReturn(Optional.of(automation));
            when(projectApplicationService.findActiveProjectInCallerOrganization(context, project.getId()))
                    .thenReturn(project);
            when(automationRunRepository.findByAutomationIdOrderByExecutedAtDesc(automation.getId()))
                    .thenReturn(List.of(run));

            assertThat(service.listRuns(context, automation.getId())).containsExactly(run);
        }
    }
}
