package com.projectmodule.work.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.projectmodule.common.api.SortDirection;
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
import com.projectmodule.work.api.dto.CreateDecisionRequest;
import com.projectmodule.work.api.dto.UpdateDecisionRequest;
import com.projectmodule.work.domain.Decision;
import com.projectmodule.work.domain.DecisionSortField;
import com.projectmodule.work.infrastructure.DecisionRepository;
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
class DecisionApplicationServiceTest {

    private static final OrganizationId ORG = OrganizationId.of(UUID.randomUUID());
    private static final ExternalUserId USER = ExternalUserId.of(UUID.randomUUID());

    @Mock
    private DecisionRepository decisionRepository;

    @Mock
    private ProjectApplicationService projectApplicationService;

    @Mock
    private ProjectAuthorizationService projectAuthorizationService;

    private DecisionApplicationService service;
    private RequestContext context;
    private Project project;

    @BeforeEach
    void setUp() {
        service = new DecisionApplicationService(
                decisionRepository, projectApplicationService, projectAuthorizationService);
        context = new ImmutableRequestContext(
                Optional.of(USER), Optional.of(ORG), "corr-1", ActorType.HUMAN);
        project = Project.create(ORG, "Apollo", USER, ProjectPriority.MEDIUM);
    }

    @Nested
    class Create {

        @Test
        @DisplayName("creates a decision")
        void createsDecision() {
            when(projectApplicationService.findActiveProjectInCallerOrganization(context, project.getId()))
                    .thenReturn(project);
            when(decisionRepository.save(any(Decision.class))).thenAnswer(inv -> inv.getArgument(0));

            CreateDecisionRequest request = new CreateDecisionRequest(
                    "Use PostgreSQL for persistence", "Chosen for jsonb support", null);
            Decision created = service.createDecision(context, project.getId(), request);

            assertThat(created.getName()).isEqualTo("Use PostgreSQL for persistence");
            assertThat(created.getProjectId()).isEqualTo(project.getId());
            assertThat(created.getDecidedBy()).isEmpty();
        }

        @Test
        @DisplayName("creates a decision with a decider")
        void createsDecisionWithDecider() {
            UUID deciderId = UUID.randomUUID();
            when(projectApplicationService.findActiveProjectInCallerOrganization(context, project.getId()))
                    .thenReturn(project);
            when(decisionRepository.save(any(Decision.class))).thenAnswer(inv -> inv.getArgument(0));

            CreateDecisionRequest request = new CreateDecisionRequest(
                    "Use PostgreSQL for persistence", null, deciderId);
            Decision created = service.createDecision(context, project.getId(), request);

            assertThat(created.getDecidedBy()).contains(ExternalUserId.of(deciderId));
        }

        @Test
        @DisplayName("denies creation without EDIT_PROJECT")
        void deniesWithoutPermission() {
            when(projectApplicationService.findActiveProjectInCallerOrganization(context, project.getId()))
                    .thenReturn(project);
            doThrow(new AuthorizationException("denied"))
                    .when(projectAuthorizationService)
                    .requirePermission(USER, project.getId(), ProjectPermission.EDIT_PROJECT);

            CreateDecisionRequest request = new CreateDecisionRequest("Use PostgreSQL for persistence", null, null);

            assertThatThrownBy(() -> service.createDecision(context, project.getId(), request))
                    .isInstanceOf(AuthorizationException.class);
            verify(decisionRepository, never()).save(any());
        }
    }

    @Nested
    class List_ {

        @Test
        @DisplayName("lists decisions for the project")
        void listsDecisions() {
            Decision decision = Decision.create(project.getId(), "Use PostgreSQL for persistence");
            when(projectApplicationService.findActiveProjectInCallerOrganization(context, project.getId()))
                    .thenReturn(project);
            when(decisionRepository.findByProjectIdAndArchivedAtIsNullOrderByCreatedAtAsc(project.getId()))
                    .thenReturn(List.of(decision));

            assertThat(service.listDecisions(context, project.getId())).containsExactly(decision);
        }
    }

    /**
     * Coverage for {@code docs/project/14-FILTERS-SPEC.md}: the filtered {@code listDecisions}
     * overload.
     */
    @Nested
    class ListFiltered {

        private static final Sort DEFAULT_SORT = Sort.by(Sort.Direction.ASC, "createdAt");

        @Test
        @DisplayName("no filters supplied delegates to the repository with null/false and the default sort")
        void noFiltersDelegatesWithNulls() {
            Decision decision = Decision.create(project.getId(), "Use PostgreSQL for persistence");
            when(projectApplicationService.findActiveProjectInCallerOrganization(context, project.getId()))
                    .thenReturn(project);
            when(decisionRepository.findByFilters(project.getId(), null, false, DEFAULT_SORT))
                    .thenReturn(List.of(decision));

            assertThat(service.listDecisions(context, project.getId(), null, false, null, null))
                    .containsExactly(decision);
        }

        @Test
        @DisplayName("filters by decidedBy")
        void filtersByDecidedBy() {
            UUID deciderId = UUID.randomUUID();
            Decision decision = Decision.create(project.getId(), "Use PostgreSQL for persistence");
            when(projectApplicationService.findActiveProjectInCallerOrganization(context, project.getId()))
                    .thenReturn(project);
            when(decisionRepository.findByFilters(project.getId(), deciderId, false, DEFAULT_SORT))
                    .thenReturn(List.of(decision));

            assertThat(service.listDecisions(context, project.getId(), deciderId, false, null, null))
                    .containsExactly(decision);
        }

        @Test
        @DisplayName("archived=true returns only archived decisions")
        void archivedTrueReturnsOnlyArchived() {
            Decision decision = Decision.create(project.getId(), "Legacy decision");
            when(projectApplicationService.findActiveProjectInCallerOrganization(context, project.getId()))
                    .thenReturn(project);
            when(decisionRepository.findByFilters(project.getId(), null, true, DEFAULT_SORT))
                    .thenReturn(List.of(decision));

            assertThat(service.listDecisions(context, project.getId(), null, true, null, null))
                    .containsExactly(decision);
        }

        @Test
        @DisplayName("sorts by name descending when requested, per docs/project/15-LIST-SPEC.md")
        void sortsByRequestedFieldAndDirection() {
            Decision decision = Decision.create(project.getId(), "Use PostgreSQL for persistence");
            Sort nameDesc = Sort.by(Sort.Direction.DESC, "name");
            when(projectApplicationService.findActiveProjectInCallerOrganization(context, project.getId()))
                    .thenReturn(project);
            when(decisionRepository.findByFilters(project.getId(), null, false, nameDesc))
                    .thenReturn(List.of(decision));

            assertThat(service.listDecisions(context, project.getId(), null, false,
                    DecisionSortField.NAME, SortDirection.DESC)).containsExactly(decision);
        }

        @Test
        @DisplayName("requires VIEW_PROJECT")
        void requiresViewProjectPermission() {
            when(projectApplicationService.findActiveProjectInCallerOrganization(context, project.getId()))
                    .thenReturn(project);
            doThrow(new AuthorizationException("denied"))
                    .when(projectAuthorizationService)
                    .requirePermission(USER, project.getId(), ProjectPermission.VIEW_PROJECT);

            assertThatThrownBy(() -> service.listDecisions(context, project.getId(), null, false, null, null))
                    .isInstanceOf(AuthorizationException.class);
        }
    }

    @Nested
    class Update {

        @Test
        @DisplayName("applies a plain field edit")
        void appliesPlainEdit() {
            Decision decision = Decision.create(project.getId(), "Use PostgreSQL for persistence");
            when(decisionRepository.findByIdAndArchivedAtIsNull(decision.getId())).thenReturn(Optional.of(decision));
            when(projectApplicationService.findActiveProjectInCallerOrganization(context, project.getId()))
                    .thenReturn(project);
            when(decisionRepository.save(any(Decision.class))).thenAnswer(inv -> inv.getArgument(0));

            UpdateDecisionRequest request = new UpdateDecisionRequest(
                    "Use PostgreSQL 17", "Updated rationale", null, decision.getVersion());
            Decision updated = service.updateDecision(context, decision.getId(), request);

            assertThat(updated.getName()).isEqualTo("Use PostgreSQL 17");
            assertThat(updated.getDescription()).contains("Updated rationale");
        }

        @Test
        @DisplayName("attributes the decision to a decider")
        void attributesDecider() {
            Decision decision = Decision.create(project.getId(), "Use PostgreSQL for persistence");
            UUID deciderId = UUID.randomUUID();
            when(decisionRepository.findByIdAndArchivedAtIsNull(decision.getId())).thenReturn(Optional.of(decision));
            when(projectApplicationService.findActiveProjectInCallerOrganization(context, project.getId()))
                    .thenReturn(project);
            when(decisionRepository.save(any(Decision.class))).thenAnswer(inv -> inv.getArgument(0));

            UpdateDecisionRequest request = new UpdateDecisionRequest(null, null, deciderId, decision.getVersion());
            Decision updated = service.updateDecision(context, decision.getId(), request);

            assertThat(updated.getDecidedBy()).contains(ExternalUserId.of(deciderId));
        }

        @Test
        @DisplayName("rejects an update against a stale version")
        void rejectsStaleVersion() {
            Decision decision = Decision.create(project.getId(), "Use PostgreSQL for persistence");
            when(decisionRepository.findByIdAndArchivedAtIsNull(decision.getId())).thenReturn(Optional.of(decision));
            when(projectApplicationService.findActiveProjectInCallerOrganization(context, project.getId()))
                    .thenReturn(project);

            UpdateDecisionRequest request = new UpdateDecisionRequest(
                    "Renamed", null, null, decision.getVersion() + 1);

            assertThatThrownBy(() -> service.updateDecision(context, decision.getId(), request))
                    .isInstanceOf(ConflictException.class);
            verify(decisionRepository, never()).save(any());
        }

        @Test
        @DisplayName("reports not found for a missing decision")
        void reportsNotFound() {
            UUID id = UUID.randomUUID();
            when(decisionRepository.findByIdAndArchivedAtIsNull(id)).thenReturn(Optional.empty());

            UpdateDecisionRequest request = new UpdateDecisionRequest(null, null, null, 0L);

            assertThatThrownBy(() -> service.updateDecision(context, id, request))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    @Nested
    class Archive {

        @Test
        @DisplayName("archives the decision")
        void archivesDecision() {
            Decision decision = Decision.create(project.getId(), "Use PostgreSQL for persistence");
            when(decisionRepository.findByIdAndArchivedAtIsNull(decision.getId())).thenReturn(Optional.of(decision));
            when(projectApplicationService.findActiveProjectInCallerOrganization(context, project.getId()))
                    .thenReturn(project);
            when(decisionRepository.save(any(Decision.class))).thenAnswer(inv -> inv.getArgument(0));

            service.archiveDecision(context, decision.getId());

            assertThat(decision.isArchived()).isTrue();
        }
    }
}
