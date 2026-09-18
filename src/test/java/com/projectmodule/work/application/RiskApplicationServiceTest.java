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
import com.projectmodule.work.api.dto.CreateRiskRequest;
import com.projectmodule.work.api.dto.UpdateRiskRequest;
import com.projectmodule.work.domain.Risk;
import com.projectmodule.work.domain.RiskSortField;
import com.projectmodule.work.domain.RiskStatus;
import com.projectmodule.work.domain.event.RiskDomainEvent;
import com.projectmodule.work.infrastructure.RiskRepository;
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
class RiskApplicationServiceTest {

    private static final OrganizationId ORG = OrganizationId.of(UUID.randomUUID());
    private static final ExternalUserId USER = ExternalUserId.of(UUID.randomUUID());

    @Mock
    private RiskRepository riskRepository;

    @Mock
    private ProjectApplicationService projectApplicationService;

    @Mock
    private ProjectAuthorizationService projectAuthorizationService;

    @Mock
    private WorkEventRecorder workEventRecorder;

    private RiskApplicationService service;
    private RequestContext context;
    private Project project;

    @BeforeEach
    void setUp() {
        service = new RiskApplicationService(
                riskRepository, projectApplicationService, projectAuthorizationService, workEventRecorder);
        context = new ImmutableRequestContext(
                Optional.of(USER), Optional.of(ORG), "corr-1", ActorType.HUMAN);
        project = Project.create(ORG, "Apollo", USER, ProjectPriority.MEDIUM);
    }

    @Nested
    class Create {

        @Test
        @DisplayName("creates a risk OPEN and records exactly one event")
        void createsRisk() {
            when(projectApplicationService.findActiveProjectInCallerOrganization(context, project.getId()))
                    .thenReturn(project);
            when(riskRepository.save(any(Risk.class))).thenAnswer(inv -> inv.getArgument(0));

            CreateRiskRequest request = new CreateRiskRequest("Vendor delay", "Key vendor slipping", ProjectPriority.HIGH);
            Risk created = service.createRisk(context, project.getId(), request);

            assertThat(created.getStatus()).isEqualTo(RiskStatus.OPEN);
            assertThat(created.getName()).isEqualTo("Vendor delay");
            assertThat(created.getPriority()).isEqualTo(ProjectPriority.HIGH);
            verify(workEventRecorder).record(eq(context), eq(created), any(RiskDomainEvent.class));
        }

        @Test
        @DisplayName("denies creation without EDIT_PROJECT")
        void deniesWithoutPermission() {
            when(projectApplicationService.findActiveProjectInCallerOrganization(context, project.getId()))
                    .thenReturn(project);
            doThrow(new AuthorizationException("denied"))
                    .when(projectAuthorizationService)
                    .requirePermission(USER, project.getId(), ProjectPermission.EDIT_PROJECT);

            CreateRiskRequest request = new CreateRiskRequest("Vendor delay", null, ProjectPriority.HIGH);

            assertThatThrownBy(() -> service.createRisk(context, project.getId(), request))
                    .isInstanceOf(AuthorizationException.class);
            verify(riskRepository, never()).save(any());
        }
    }

    @Nested
    class List_ {

        @Test
        @DisplayName("lists risks for the project")
        void listsRisks() {
            Risk risk = Risk.create(project.getId(), "Vendor delay", ProjectPriority.HIGH);
            when(projectApplicationService.findActiveProjectInCallerOrganization(context, project.getId()))
                    .thenReturn(project);
            when(riskRepository.findByProjectIdAndArchivedAtIsNullOrderByCreatedAtAsc(project.getId()))
                    .thenReturn(List.of(risk));

            assertThat(service.listRisks(context, project.getId())).containsExactly(risk);
        }
    }

    /**
     * Coverage for {@code docs/project/14-FILTERS-SPEC.md}: the filtered {@code listRisks}
     * overload.
     */
    @Nested
    class ListFiltered {

        private static final Sort DEFAULT_SORT = Sort.by(Sort.Direction.ASC, "createdAt");

        @Test
        @DisplayName("no filters supplied delegates to the repository with all null/false and the default sort")
        void noFiltersDelegatesWithNulls() {
            Risk risk = Risk.create(project.getId(), "Vendor delay", ProjectPriority.HIGH);
            when(projectApplicationService.findActiveProjectInCallerOrganization(context, project.getId()))
                    .thenReturn(project);
            when(riskRepository.findByFilters(project.getId(), null, null, false, DEFAULT_SORT))
                    .thenReturn(List.of(risk));

            assertThat(service.listRisks(context, project.getId(), null, null, false, null, null))
                    .containsExactly(risk);
        }

        @Test
        @DisplayName("filters by status and priority combined (AND)")
        void combinesStatusAndPriority() {
            Risk risk = Risk.create(project.getId(), "Vendor delay", ProjectPriority.HIGH);
            when(projectApplicationService.findActiveProjectInCallerOrganization(context, project.getId()))
                    .thenReturn(project);
            when(riskRepository.findByFilters(project.getId(), RiskStatus.OPEN, ProjectPriority.HIGH, false, DEFAULT_SORT))
                    .thenReturn(List.of(risk));

            assertThat(service.listRisks(context, project.getId(), RiskStatus.OPEN, ProjectPriority.HIGH, false, null, null))
                    .containsExactly(risk);
            verify(riskRepository).findByFilters(
                    project.getId(), RiskStatus.OPEN, ProjectPriority.HIGH, false, DEFAULT_SORT);
        }

        @Test
        @DisplayName("archived=true returns only archived risks")
        void archivedTrueReturnsOnlyArchived() {
            Risk risk = Risk.create(project.getId(), "Legacy", ProjectPriority.LOW);
            when(projectApplicationService.findActiveProjectInCallerOrganization(context, project.getId()))
                    .thenReturn(project);
            when(riskRepository.findByFilters(project.getId(), null, null, true, DEFAULT_SORT))
                    .thenReturn(List.of(risk));

            assertThat(service.listRisks(context, project.getId(), null, null, true, null, null))
                    .containsExactly(risk);
        }

        @Test
        @DisplayName("sorts by priority descending when requested, per docs/project/15-LIST-SPEC.md")
        void sortsByRequestedFieldAndDirection() {
            Risk risk = Risk.create(project.getId(), "Vendor delay", ProjectPriority.HIGH);
            Sort priorityDesc = Sort.by(Sort.Direction.DESC, "priority");
            when(projectApplicationService.findActiveProjectInCallerOrganization(context, project.getId()))
                    .thenReturn(project);
            when(riskRepository.findByFilters(project.getId(), null, null, false, priorityDesc))
                    .thenReturn(List.of(risk));

            assertThat(service.listRisks(context, project.getId(), null, null, false,
                    RiskSortField.PRIORITY, SortDirection.DESC)).containsExactly(risk);
        }

        @Test
        @DisplayName("requires VIEW_PROJECT")
        void requiresViewProjectPermission() {
            when(projectApplicationService.findActiveProjectInCallerOrganization(context, project.getId()))
                    .thenReturn(project);
            doThrow(new AuthorizationException("denied"))
                    .when(projectAuthorizationService)
                    .requirePermission(USER, project.getId(), ProjectPermission.VIEW_PROJECT);

            assertThatThrownBy(() -> service.listRisks(context, project.getId(), null, null, false, null, null))
                    .isInstanceOf(AuthorizationException.class);
        }
    }

    @Nested
    class Update {

        @Test
        @DisplayName("a plain field edit records exactly one risk.updated event")
        void plainEditRecordsOneEvent() {
            Risk risk = Risk.create(project.getId(), "Vendor delay", ProjectPriority.HIGH);
            risk.pullDomainEvents();
            when(riskRepository.findByIdAndArchivedAtIsNull(risk.getId())).thenReturn(Optional.of(risk));
            when(projectApplicationService.findActiveProjectInCallerOrganization(context, project.getId()))
                    .thenReturn(project);
            when(riskRepository.save(any(Risk.class))).thenAnswer(inv -> inv.getArgument(0));

            UpdateRiskRequest request = new UpdateRiskRequest(
                    "Supplier delay", "Updated", ProjectPriority.CRITICAL, null, risk.getVersion());
            Risk updated = service.updateRisk(context, risk.getId(), request);

            assertThat(updated.getName()).isEqualTo("Supplier delay");
            assertThat(updated.getPriority()).isEqualTo(ProjectPriority.CRITICAL);
            verify(workEventRecorder).record(eq(context), eq(updated), any(RiskDomainEvent.class));
        }

        @Test
        @DisplayName("status RESOLVED routes to resolve()")
        void statusResolvedRoutesToResolve() {
            Risk risk = Risk.create(project.getId(), "Vendor delay", ProjectPriority.HIGH);
            when(riskRepository.findByIdAndArchivedAtIsNull(risk.getId())).thenReturn(Optional.of(risk));
            when(projectApplicationService.findActiveProjectInCallerOrganization(context, project.getId()))
                    .thenReturn(project);
            when(riskRepository.save(any(Risk.class))).thenAnswer(inv -> inv.getArgument(0));

            UpdateRiskRequest request = new UpdateRiskRequest(null, null, null, RiskStatus.RESOLVED, risk.getVersion());
            Risk updated = service.updateRisk(context, risk.getId(), request);

            assertThat(updated.getStatus()).isEqualTo(RiskStatus.RESOLVED);
        }

        @Test
        @DisplayName("status OPEN is rejected - no event exists for that transition")
        void statusOpenIsRejected() {
            Risk risk = Risk.create(project.getId(), "Vendor delay", ProjectPriority.HIGH);
            risk.resolve();
            when(riskRepository.findByIdAndArchivedAtIsNull(risk.getId())).thenReturn(Optional.of(risk));
            when(projectApplicationService.findActiveProjectInCallerOrganization(context, project.getId()))
                    .thenReturn(project);

            UpdateRiskRequest request = new UpdateRiskRequest(null, null, null, RiskStatus.OPEN, risk.getVersion());

            assertThatThrownBy(() -> service.updateRisk(context, risk.getId(), request))
                    .isInstanceOf(BusinessRuleViolationException.class);
        }

        @Test
        @DisplayName("rejects an update against a stale version")
        void rejectsStaleVersion() {
            Risk risk = Risk.create(project.getId(), "Vendor delay", ProjectPriority.HIGH);
            when(riskRepository.findByIdAndArchivedAtIsNull(risk.getId())).thenReturn(Optional.of(risk));
            when(projectApplicationService.findActiveProjectInCallerOrganization(context, project.getId()))
                    .thenReturn(project);

            UpdateRiskRequest request = new UpdateRiskRequest(
                    "Renamed", null, null, null, risk.getVersion() + 1);

            assertThatThrownBy(() -> service.updateRisk(context, risk.getId(), request))
                    .isInstanceOf(ConflictException.class);
            verify(riskRepository, never()).save(any());
        }

        @Test
        @DisplayName("reports not found for a missing risk")
        void reportsNotFound() {
            UUID id = UUID.randomUUID();
            when(riskRepository.findByIdAndArchivedAtIsNull(id)).thenReturn(Optional.empty());

            UpdateRiskRequest request = new UpdateRiskRequest(null, null, null, null, 0L);

            assertThatThrownBy(() -> service.updateRisk(context, id, request))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    @Nested
    class Archive {

        @Test
        @DisplayName("archives the risk and records no event")
        void archivesWithNoEvent() {
            Risk risk = Risk.create(project.getId(), "Vendor delay", ProjectPriority.HIGH);
            when(riskRepository.findByIdAndArchivedAtIsNull(risk.getId())).thenReturn(Optional.of(risk));
            when(projectApplicationService.findActiveProjectInCallerOrganization(context, project.getId()))
                    .thenReturn(project);
            when(riskRepository.save(any(Risk.class))).thenAnswer(inv -> inv.getArgument(0));

            service.archiveRisk(context, risk.getId());

            assertThat(risk.isArchived()).isTrue();
            verify(workEventRecorder, never()).record(any(), any(), any(RiskDomainEvent.class));
        }
    }
}
