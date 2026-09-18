package com.projectmodule.work.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
import com.projectmodule.work.api.dto.CreatePhaseRequest;
import com.projectmodule.work.api.dto.UpdatePhaseRequest;
import com.projectmodule.work.domain.Phase;
import com.projectmodule.work.infrastructure.PhaseRepository;
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

@ExtendWith(MockitoExtension.class)
class PhaseApplicationServiceTest {

    private static final OrganizationId ORG = OrganizationId.of(UUID.randomUUID());
    private static final ExternalUserId USER = ExternalUserId.of(UUID.randomUUID());

    @Mock
    private PhaseRepository phaseRepository;

    @Mock
    private ProjectApplicationService projectApplicationService;

    @Mock
    private ProjectAuthorizationService projectAuthorizationService;

    @Mock
    private WorkEventRecorder workEventRecorder;

    private PhaseApplicationService service;
    private RequestContext context;
    private Project project;

    @BeforeEach
    void setUp() {
        service = new PhaseApplicationService(
                phaseRepository, projectApplicationService, projectAuthorizationService, workEventRecorder);
        context = new ImmutableRequestContext(
                Optional.of(USER), Optional.of(ORG), "corr-1", ActorType.HUMAN);
        project = Project.create(ORG, "Apollo", USER, ProjectPriority.MEDIUM);
    }

    @Nested
    class Create {

        @Test
        @DisplayName("creates a phase and records its event")
        void createsPhase() {
            when(projectApplicationService.findActiveProjectInCallerOrganization(context, project.getId()))
                    .thenReturn(project);
            when(phaseRepository.save(any(Phase.class))).thenAnswer(inv -> inv.getArgument(0));

            CreatePhaseRequest request = new CreatePhaseRequest("Discovery", "Explore", null, null);
            Phase created = service.createPhase(context, project.getId(), request);

            assertThat(created.getName()).isEqualTo("Discovery");
            assertThat(created.getProjectId()).isEqualTo(project.getId());
            verify(workEventRecorder).record(eq(context), eq(created), any(com.projectmodule.work.domain.event.PhaseDomainEvent.class));
        }

        @Test
        @DisplayName("denies creation without EDIT_PROJECT")
        void deniesWithoutPermission() {
            when(projectApplicationService.findActiveProjectInCallerOrganization(context, project.getId()))
                    .thenReturn(project);
            doThrow(new AuthorizationException("denied"))
                    .when(projectAuthorizationService)
                    .requirePermission(USER, project.getId(), ProjectPermission.EDIT_PROJECT);

            CreatePhaseRequest request = new CreatePhaseRequest("Discovery", null, null, null);

            assertThatThrownBy(() -> service.createPhase(context, project.getId(), request))
                    .isInstanceOf(AuthorizationException.class);
            verify(phaseRepository, never()).save(any());
        }
    }

    @Nested
    class List_ {

        @Test
        @DisplayName("lists phases for the project after checking VIEW_PROJECT")
        void listsPhases() {
            Phase phase = Phase.create(project.getId(), "Discovery");
            when(projectApplicationService.findActiveProjectInCallerOrganization(context, project.getId()))
                    .thenReturn(project);
            when(phaseRepository.findByProjectIdAndArchivedAtIsNullOrderByCreatedAtAsc(project.getId()))
                    .thenReturn(List.of(phase));

            assertThat(service.listPhases(context, project.getId())).containsExactly(phase);
            verify(projectAuthorizationService).requirePermission(
                    USER, project.getId(), ProjectPermission.VIEW_PROJECT);
        }
    }

    @Nested
    class Update {

        @Test
        @DisplayName("applies only the fields supplied and records exactly one event")
        void appliesSuppliedFieldsAndRecordsEvent() {
            Phase phase = Phase.create(project.getId(), "Discovery");
            phase.pullDomainEvents();
            when(phaseRepository.findByIdAndArchivedAtIsNull(phase.getId())).thenReturn(Optional.of(phase));
            when(projectApplicationService.findActiveProjectInCallerOrganization(context, project.getId()))
                    .thenReturn(project);
            when(phaseRepository.save(any(Phase.class))).thenAnswer(inv -> inv.getArgument(0));

            UpdatePhaseRequest request = new UpdatePhaseRequest("Renamed", null, null, null, phase.getVersion());
            Phase updated = service.updatePhase(context, phase.getId(), request);

            assertThat(updated.getName()).isEqualTo("Renamed");
            verify(workEventRecorder).record(eq(context), eq(updated), any(com.projectmodule.work.domain.event.PhaseDomainEvent.class));
        }

        @Test
        @DisplayName("rejects an update against a stale version")
        void rejectsStaleVersion() {
            Phase phase = Phase.create(project.getId(), "Discovery");
            when(phaseRepository.findByIdAndArchivedAtIsNull(phase.getId())).thenReturn(Optional.of(phase));
            when(projectApplicationService.findActiveProjectInCallerOrganization(context, project.getId()))
                    .thenReturn(project);

            UpdatePhaseRequest request = new UpdatePhaseRequest(
                    "Renamed", null, null, null, phase.getVersion() + 1);

            assertThatThrownBy(() -> service.updatePhase(context, phase.getId(), request))
                    .isInstanceOf(ConflictException.class);
            verify(phaseRepository, never()).save(any());
        }

        @Test
        @DisplayName("reports not found for a missing phase")
        void reportsNotFound() {
            UUID id = UUID.randomUUID();
            when(phaseRepository.findByIdAndArchivedAtIsNull(id)).thenReturn(Optional.empty());

            UpdatePhaseRequest request = new UpdatePhaseRequest("Renamed", null, null, null, 0L);

            assertThatThrownBy(() -> service.updatePhase(context, id, request))
                    .isInstanceOf(ResourceNotFoundException.class);
        }

        @Test
        @DisplayName("rejects a reversed date range")
        void rejectsReversedDates() {
            Phase phase = Phase.create(project.getId(), "Discovery");
            when(phaseRepository.findByIdAndArchivedAtIsNull(phase.getId())).thenReturn(Optional.of(phase));
            when(projectApplicationService.findActiveProjectInCallerOrganization(context, project.getId()))
                    .thenReturn(project);

            UpdatePhaseRequest request = new UpdatePhaseRequest(null, null,
                    LocalDate.of(2026, 5, 1), LocalDate.of(2026, 1, 1), phase.getVersion());

            assertThatThrownBy(() -> service.updatePhase(context, phase.getId(), request))
                    .isInstanceOf(com.projectmodule.common.exception.ValidationException.class);
        }
    }

    @Nested
    class Archive {

        @Test
        @DisplayName("archives the phase and records no event")
        void archivesWithNoEvent() {
            Phase phase = Phase.create(project.getId(), "Discovery");
            when(phaseRepository.findByIdAndArchivedAtIsNull(phase.getId())).thenReturn(Optional.of(phase));
            when(projectApplicationService.findActiveProjectInCallerOrganization(context, project.getId()))
                    .thenReturn(project);
            when(phaseRepository.save(any(Phase.class))).thenAnswer(inv -> inv.getArgument(0));

            service.archivePhase(context, phase.getId());

            assertThat(phase.isArchived()).isTrue();
            verify(workEventRecorder, never()).record(any(), any(), any(com.projectmodule.work.domain.event.PhaseDomainEvent.class));
        }

        @Test
        @DisplayName("archiving a missing phase reports not found")
        void archivingMissingPhaseReportsNotFound() {
            UUID id = UUID.randomUUID();
            when(phaseRepository.findByIdAndArchivedAtIsNull(id)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.archivePhase(context, id))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }
}
