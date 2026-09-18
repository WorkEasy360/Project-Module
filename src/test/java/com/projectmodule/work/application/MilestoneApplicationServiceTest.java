package com.projectmodule.work.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
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
import com.projectmodule.project.application.ProjectApplicationService;
import com.projectmodule.project.application.authorization.ProjectAuthorizationService;
import com.projectmodule.project.domain.Project;
import com.projectmodule.project.domain.ProjectPriority;
import com.projectmodule.work.api.dto.CreateMilestoneRequest;
import com.projectmodule.work.api.dto.UpdateMilestoneRequest;
import com.projectmodule.work.domain.Milestone;
import com.projectmodule.work.domain.MilestoneStatus;
import com.projectmodule.work.infrastructure.MilestoneRepository;
import java.time.LocalDate;
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
class MilestoneApplicationServiceTest {

    private static final OrganizationId ORG = OrganizationId.of(UUID.randomUUID());
    private static final ExternalUserId USER = ExternalUserId.of(UUID.randomUUID());

    @Mock
    private MilestoneRepository milestoneRepository;

    @Mock
    private ProjectApplicationService projectApplicationService;

    @Mock
    private ProjectAuthorizationService projectAuthorizationService;

    @Mock
    private WorkEventRecorder workEventRecorder;

    private MilestoneApplicationService service;
    private RequestContext context;
    private Project project;

    @BeforeEach
    void setUp() {
        service = new MilestoneApplicationService(
                milestoneRepository, projectApplicationService, projectAuthorizationService, workEventRecorder);
        context = new ImmutableRequestContext(
                Optional.of(USER), Optional.of(ORG), "corr-1", ActorType.HUMAN);
        project = Project.create(ORG, "Apollo", USER, ProjectPriority.MEDIUM);
    }

    @Nested
    class Create {

        @Test
        @DisplayName("creates a milestone PENDING and records its event")
        void createsMilestone() {
            when(projectApplicationService.findActiveProjectInCallerOrganization(context, project.getId()))
                    .thenReturn(project);
            when(milestoneRepository.save(any(Milestone.class))).thenAnswer(inv -> inv.getArgument(0));

            CreateMilestoneRequest request = new CreateMilestoneRequest(
                    "Beta launch", null, LocalDate.of(2026, 3, 1), null);
            Milestone created = service.createMilestone(context, project.getId(), request);

            assertThat(created.getStatus()).isEqualTo(MilestoneStatus.PENDING);
            verify(workEventRecorder).record(eq(context), eq(created), any(com.projectmodule.work.domain.event.MilestoneDomainEvent.class));
        }
    }

    @Nested
    class Update {

        @Test
        @DisplayName("a plain field edit records no event")
        void plainEditRecordsNoEvent() {
            Milestone milestone = Milestone.create(project.getId(), null, "Beta", null);
            milestone.pullDomainEvents();
            when(milestoneRepository.findByIdAndArchivedAtIsNull(milestone.getId()))
                    .thenReturn(Optional.of(milestone));
            when(projectApplicationService.findActiveProjectInCallerOrganization(context, project.getId()))
                    .thenReturn(project);
            when(milestoneRepository.save(any(Milestone.class))).thenAnswer(inv -> inv.getArgument(0));

            UpdateMilestoneRequest request = new UpdateMilestoneRequest(
                    "Renamed", null, null, null, null, milestone.getVersion());
            Milestone updated = service.updateMilestone(context, milestone.getId(), request);

            assertThat(updated.getName()).isEqualTo("Renamed");
            verify(workEventRecorder, never()).record(any(), any(), any(com.projectmodule.work.domain.event.MilestoneDomainEvent.class));
        }

        @Test
        @DisplayName("status COMPLETED routes to complete() and records milestone.completed")
        void statusCompletedRoutesToComplete() {
            Milestone milestone = Milestone.create(project.getId(), null, "Beta", null);
            milestone.pullDomainEvents();
            when(milestoneRepository.findByIdAndArchivedAtIsNull(milestone.getId()))
                    .thenReturn(Optional.of(milestone));
            when(projectApplicationService.findActiveProjectInCallerOrganization(context, project.getId()))
                    .thenReturn(project);
            when(milestoneRepository.save(any(Milestone.class))).thenAnswer(inv -> inv.getArgument(0));

            UpdateMilestoneRequest request = new UpdateMilestoneRequest(
                    null, null, null, null, MilestoneStatus.COMPLETED, milestone.getVersion());
            Milestone updated = service.updateMilestone(context, milestone.getId(), request);

            assertThat(updated.getStatus()).isEqualTo(MilestoneStatus.COMPLETED);
            verify(workEventRecorder).record(eq(context), eq(updated), any(com.projectmodule.work.domain.event.MilestoneDomainEvent.class));
        }

        @Test
        @DisplayName("status AT_RISK routes to markAtRisk() and records milestone.at_risk")
        void statusAtRiskRoutesToMarkAtRisk() {
            Milestone milestone = Milestone.create(project.getId(), null, "Beta", null);
            when(milestoneRepository.findByIdAndArchivedAtIsNull(milestone.getId()))
                    .thenReturn(Optional.of(milestone));
            when(projectApplicationService.findActiveProjectInCallerOrganization(context, project.getId()))
                    .thenReturn(project);
            when(milestoneRepository.save(any(Milestone.class))).thenAnswer(inv -> inv.getArgument(0));

            UpdateMilestoneRequest request = new UpdateMilestoneRequest(
                    null, null, null, null, MilestoneStatus.AT_RISK, milestone.getVersion());
            Milestone updated = service.updateMilestone(context, milestone.getId(), request);

            assertThat(updated.getStatus()).isEqualTo(MilestoneStatus.AT_RISK);
        }

        @Test
        @DisplayName("status PENDING is rejected - no event exists for that transition")
        void statusPendingIsRejected() {
            Milestone milestone = Milestone.create(project.getId(), null, "Beta", null);
            milestone.complete();
            when(milestoneRepository.findByIdAndArchivedAtIsNull(milestone.getId()))
                    .thenReturn(Optional.of(milestone));
            when(projectApplicationService.findActiveProjectInCallerOrganization(context, project.getId()))
                    .thenReturn(project);

            UpdateMilestoneRequest request = new UpdateMilestoneRequest(
                    null, null, null, null, MilestoneStatus.PENDING, milestone.getVersion());

            assertThatThrownBy(() -> service.updateMilestone(context, milestone.getId(), request))
                    .isInstanceOf(BusinessRuleViolationException.class);
        }

        @Test
        @DisplayName("rejects an update against a stale version")
        void rejectsStaleVersion() {
            Milestone milestone = Milestone.create(project.getId(), null, "Beta", null);
            when(milestoneRepository.findByIdAndArchivedAtIsNull(milestone.getId()))
                    .thenReturn(Optional.of(milestone));
            when(projectApplicationService.findActiveProjectInCallerOrganization(context, project.getId()))
                    .thenReturn(project);

            UpdateMilestoneRequest request = new UpdateMilestoneRequest(
                    "Renamed", null, null, null, null, milestone.getVersion() + 1);

            assertThatThrownBy(() -> service.updateMilestone(context, milestone.getId(), request))
                    .isInstanceOf(ConflictException.class);
            verify(milestoneRepository, never()).save(any());
        }

        @Test
        @DisplayName("reports not found for a missing milestone")
        void reportsNotFound() {
            UUID id = UUID.randomUUID();
            when(milestoneRepository.findByIdAndArchivedAtIsNull(id)).thenReturn(Optional.empty());

            UpdateMilestoneRequest request = new UpdateMilestoneRequest(
                    "Renamed", null, null, null, null, 0L);

            assertThatThrownBy(() -> service.updateMilestone(context, id, request))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    @Nested
    class Archive {

        @Test
        @DisplayName("archives the milestone and records no event")
        void archivesWithNoEvent() {
            Milestone milestone = Milestone.create(project.getId(), null, "Beta", null);
            when(milestoneRepository.findByIdAndArchivedAtIsNull(milestone.getId()))
                    .thenReturn(Optional.of(milestone));
            when(projectApplicationService.findActiveProjectInCallerOrganization(context, project.getId()))
                    .thenReturn(project);
            when(milestoneRepository.save(any(Milestone.class))).thenAnswer(inv -> inv.getArgument(0));

            service.archiveMilestone(context, milestone.getId());

            assertThat(milestone.isArchived()).isTrue();
            verify(workEventRecorder, never()).record(any(), any(), any(com.projectmodule.work.domain.event.MilestoneDomainEvent.class));
        }
    }
}
