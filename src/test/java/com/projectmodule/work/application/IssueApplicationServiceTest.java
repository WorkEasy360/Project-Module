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
import com.projectmodule.work.api.dto.CreateIssueRequest;
import com.projectmodule.work.api.dto.UpdateIssueRequest;
import com.projectmodule.work.domain.Issue;
import com.projectmodule.work.domain.IssueSortField;
import com.projectmodule.work.infrastructure.IssueRepository;
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
class IssueApplicationServiceTest {

    private static final OrganizationId ORG = OrganizationId.of(UUID.randomUUID());
    private static final ExternalUserId USER = ExternalUserId.of(UUID.randomUUID());

    @Mock
    private IssueRepository issueRepository;

    @Mock
    private ProjectApplicationService projectApplicationService;

    @Mock
    private ProjectAuthorizationService projectAuthorizationService;

    private IssueApplicationService service;
    private RequestContext context;
    private Project project;

    @BeforeEach
    void setUp() {
        service = new IssueApplicationService(issueRepository, projectApplicationService, projectAuthorizationService);
        context = new ImmutableRequestContext(
                Optional.of(USER), Optional.of(ORG), "corr-1", ActorType.HUMAN);
        project = Project.create(ORG, "Apollo", USER, ProjectPriority.MEDIUM);
    }

    @Nested
    class Create {

        @Test
        @DisplayName("creates an issue")
        void createsIssue() {
            when(projectApplicationService.findActiveProjectInCallerOrganization(context, project.getId()))
                    .thenReturn(project);
            when(issueRepository.save(any(Issue.class))).thenAnswer(inv -> inv.getArgument(0));

            CreateIssueRequest request = new CreateIssueRequest(
                    "Payment gateway down", "Checkout failing for all users", ProjectPriority.CRITICAL);
            Issue created = service.createIssue(context, project.getId(), request);

            assertThat(created.getName()).isEqualTo("Payment gateway down");
            assertThat(created.getPriority()).isEqualTo(ProjectPriority.CRITICAL);
            assertThat(created.getProjectId()).isEqualTo(project.getId());
        }

        @Test
        @DisplayName("denies creation without EDIT_PROJECT")
        void deniesWithoutPermission() {
            when(projectApplicationService.findActiveProjectInCallerOrganization(context, project.getId()))
                    .thenReturn(project);
            doThrow(new AuthorizationException("denied"))
                    .when(projectAuthorizationService)
                    .requirePermission(USER, project.getId(), ProjectPermission.EDIT_PROJECT);

            CreateIssueRequest request = new CreateIssueRequest("Payment gateway down", null, ProjectPriority.CRITICAL);

            assertThatThrownBy(() -> service.createIssue(context, project.getId(), request))
                    .isInstanceOf(AuthorizationException.class);
            verify(issueRepository, never()).save(any());
        }
    }

    @Nested
    class List_ {

        @Test
        @DisplayName("lists issues for the project")
        void listsIssues() {
            Issue issue = Issue.create(project.getId(), "Payment gateway down", ProjectPriority.CRITICAL);
            when(projectApplicationService.findActiveProjectInCallerOrganization(context, project.getId()))
                    .thenReturn(project);
            when(issueRepository.findByProjectIdAndArchivedAtIsNullOrderByCreatedAtAsc(project.getId()))
                    .thenReturn(List.of(issue));

            assertThat(service.listIssues(context, project.getId())).containsExactly(issue);
        }
    }

    /**
     * Coverage for {@code docs/project/14-FILTERS-SPEC.md}: the filtered {@code listIssues}
     * overload.
     */
    @Nested
    class ListFiltered {

        private static final Sort DEFAULT_SORT = Sort.by(Sort.Direction.ASC, "createdAt");

        @Test
        @DisplayName("no filters supplied delegates to the repository with null/false and the default sort")
        void noFiltersDelegatesWithNulls() {
            Issue issue = Issue.create(project.getId(), "Payment gateway down", ProjectPriority.CRITICAL);
            when(projectApplicationService.findActiveProjectInCallerOrganization(context, project.getId()))
                    .thenReturn(project);
            when(issueRepository.findByFilters(project.getId(), null, false, DEFAULT_SORT))
                    .thenReturn(List.of(issue));

            assertThat(service.listIssues(context, project.getId(), null, false, null, null)).containsExactly(issue);
        }

        @Test
        @DisplayName("filters by priority")
        void filtersByPriority() {
            Issue issue = Issue.create(project.getId(), "Payment gateway down", ProjectPriority.CRITICAL);
            when(projectApplicationService.findActiveProjectInCallerOrganization(context, project.getId()))
                    .thenReturn(project);
            when(issueRepository.findByFilters(project.getId(), ProjectPriority.CRITICAL, false, DEFAULT_SORT))
                    .thenReturn(List.of(issue));

            assertThat(service.listIssues(context, project.getId(), ProjectPriority.CRITICAL, false, null, null))
                    .containsExactly(issue);
        }

        @Test
        @DisplayName("archived=true returns only archived issues")
        void archivedTrueReturnsOnlyArchived() {
            Issue issue = Issue.create(project.getId(), "Legacy", ProjectPriority.LOW);
            when(projectApplicationService.findActiveProjectInCallerOrganization(context, project.getId()))
                    .thenReturn(project);
            when(issueRepository.findByFilters(project.getId(), null, true, DEFAULT_SORT))
                    .thenReturn(List.of(issue));

            assertThat(service.listIssues(context, project.getId(), null, true, null, null)).containsExactly(issue);
        }

        @Test
        @DisplayName("sorts by name descending when requested, per docs/project/15-LIST-SPEC.md")
        void sortsByRequestedFieldAndDirection() {
            Issue issue = Issue.create(project.getId(), "Payment gateway down", ProjectPriority.CRITICAL);
            Sort nameDesc = Sort.by(Sort.Direction.DESC, "name");
            when(projectApplicationService.findActiveProjectInCallerOrganization(context, project.getId()))
                    .thenReturn(project);
            when(issueRepository.findByFilters(project.getId(), null, false, nameDesc))
                    .thenReturn(List.of(issue));

            assertThat(service.listIssues(context, project.getId(), null, false,
                    IssueSortField.NAME, SortDirection.DESC)).containsExactly(issue);
        }

        @Test
        @DisplayName("requires VIEW_PROJECT")
        void requiresViewProjectPermission() {
            when(projectApplicationService.findActiveProjectInCallerOrganization(context, project.getId()))
                    .thenReturn(project);
            doThrow(new AuthorizationException("denied"))
                    .when(projectAuthorizationService)
                    .requirePermission(USER, project.getId(), ProjectPermission.VIEW_PROJECT);

            assertThatThrownBy(() -> service.listIssues(context, project.getId(), null, false, null, null))
                    .isInstanceOf(AuthorizationException.class);
        }
    }

    @Nested
    class Update {

        @Test
        @DisplayName("applies a plain field edit")
        void appliesPlainEdit() {
            Issue issue = Issue.create(project.getId(), "Payment gateway down", ProjectPriority.CRITICAL);
            when(issueRepository.findByIdAndArchivedAtIsNull(issue.getId())).thenReturn(Optional.of(issue));
            when(projectApplicationService.findActiveProjectInCallerOrganization(context, project.getId()))
                    .thenReturn(project);
            when(issueRepository.save(any(Issue.class))).thenAnswer(inv -> inv.getArgument(0));

            UpdateIssueRequest request = new UpdateIssueRequest(
                    "Gateway partially restored", "Updated", ProjectPriority.MEDIUM, issue.getVersion());
            Issue updated = service.updateIssue(context, issue.getId(), request);

            assertThat(updated.getName()).isEqualTo("Gateway partially restored");
            assertThat(updated.getPriority()).isEqualTo(ProjectPriority.MEDIUM);
        }

        @Test
        @DisplayName("rejects an update against a stale version")
        void rejectsStaleVersion() {
            Issue issue = Issue.create(project.getId(), "Payment gateway down", ProjectPriority.CRITICAL);
            when(issueRepository.findByIdAndArchivedAtIsNull(issue.getId())).thenReturn(Optional.of(issue));
            when(projectApplicationService.findActiveProjectInCallerOrganization(context, project.getId()))
                    .thenReturn(project);

            UpdateIssueRequest request = new UpdateIssueRequest(
                    "Renamed", null, null, issue.getVersion() + 1);

            assertThatThrownBy(() -> service.updateIssue(context, issue.getId(), request))
                    .isInstanceOf(ConflictException.class);
            verify(issueRepository, never()).save(any());
        }

        @Test
        @DisplayName("reports not found for a missing issue")
        void reportsNotFound() {
            UUID id = UUID.randomUUID();
            when(issueRepository.findByIdAndArchivedAtIsNull(id)).thenReturn(Optional.empty());

            UpdateIssueRequest request = new UpdateIssueRequest(null, null, null, 0L);

            assertThatThrownBy(() -> service.updateIssue(context, id, request))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    @Nested
    class Archive {

        @Test
        @DisplayName("archives the issue")
        void archivesIssue() {
            Issue issue = Issue.create(project.getId(), "Payment gateway down", ProjectPriority.CRITICAL);
            when(issueRepository.findByIdAndArchivedAtIsNull(issue.getId())).thenReturn(Optional.of(issue));
            when(projectApplicationService.findActiveProjectInCallerOrganization(context, project.getId()))
                    .thenReturn(project);
            when(issueRepository.save(any(Issue.class))).thenAnswer(inv -> inv.getArgument(0));

            service.archiveIssue(context, issue.getId());

            assertThat(issue.isArchived()).isTrue();
        }
    }
}
