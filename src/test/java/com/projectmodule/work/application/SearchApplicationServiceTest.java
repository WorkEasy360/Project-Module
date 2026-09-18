package com.projectmodule.work.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.projectmodule.common.context.ActorType;
import com.projectmodule.common.context.ImmutableRequestContext;
import com.projectmodule.common.context.RequestContext;
import com.projectmodule.common.exception.AuthorizationException;
import com.projectmodule.common.exception.BusinessRuleViolationException;
import com.projectmodule.common.exception.ResourceNotFoundException;
import com.projectmodule.common.identity.ExternalUserId;
import com.projectmodule.common.identity.OrganizationId;
import com.projectmodule.project.application.ProjectApplicationService;
import com.projectmodule.project.application.authorization.ProjectAuthorizationService;
import com.projectmodule.project.application.authorization.ProjectPermission;
import com.projectmodule.project.domain.Project;
import com.projectmodule.project.domain.ProjectPriority;
import com.projectmodule.work.api.dto.SearchResultResponse;
import com.projectmodule.work.domain.Decision;
import com.projectmodule.work.domain.Issue;
import com.projectmodule.work.domain.Risk;
import com.projectmodule.work.domain.Task;
import com.projectmodule.work.infrastructure.DecisionRepository;
import com.projectmodule.work.infrastructure.IssueRepository;
import com.projectmodule.work.infrastructure.RiskRepository;
import com.projectmodule.work.infrastructure.TaskRepository;
import java.time.Instant;
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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class SearchApplicationServiceTest {

    private static final OrganizationId ORG = OrganizationId.of(UUID.randomUUID());
    private static final ExternalUserId USER = ExternalUserId.of(UUID.randomUUID());

    @Mock
    private TaskRepository taskRepository;

    @Mock
    private RiskRepository riskRepository;

    @Mock
    private IssueRepository issueRepository;

    @Mock
    private DecisionRepository decisionRepository;

    @Mock
    private ProjectApplicationService projectApplicationService;

    @Mock
    private ProjectAuthorizationService projectAuthorizationService;

    private SearchApplicationService service;
    private RequestContext context;
    private Project project;

    @BeforeEach
    void setUp() {
        service = new SearchApplicationService(taskRepository, riskRepository, issueRepository,
                decisionRepository, projectApplicationService, projectAuthorizationService);
        context = new ImmutableRequestContext(
                Optional.of(USER), Optional.of(ORG), "corr-1", ActorType.HUMAN);
        project = Project.create(ORG, "Apollo", USER, ProjectPriority.MEDIUM);
    }

    /**
     * {@code createdAt}/{@code updatedAt} are only populated by {@code BaseEntity}'s
     * {@code @PrePersist} callback, which never fires for an entity built via {@code .create(...)}
     * and handed straight to a mock — this codebase has no other precedent for populating a
     * JPA-managed timestamp outside a real persistence context, so {@code ReflectionTestUtils}
     * (already on the classpath via {@code spring-boot-starter-test}, no new dependency) is used
     * here purely to give the mocked repository results a timestamp for
     * {@link SearchApplicationService} to sort by, the same way a real, already-persisted row
     * loaded from the database always would have one.
     */
    private static void stampUpdatedAt(Object entity, Instant updatedAt) {
        ReflectionTestUtils.setField(entity, "updatedAt", updatedAt);
    }

    @Nested
    class BlankQuery {

        @Test
        @DisplayName("rejects a blank query before touching any repository or authorization")
        void rejectsBlankQuery() {
            assertThatThrownBy(() -> service.search(context, project.getId(), "   ", PageRequest.of(0, 20)))
                    .isInstanceOf(BusinessRuleViolationException.class);

            verifyNoInteractions(taskRepository, riskRepository, issueRepository, decisionRepository,
                    projectApplicationService, projectAuthorizationService);
        }

        @Test
        @DisplayName("rejects a null query")
        void rejectsNullQuery() {
            assertThatThrownBy(() -> service.search(context, project.getId(), null, PageRequest.of(0, 20)))
                    .isInstanceOf(BusinessRuleViolationException.class);
        }
    }

    @Nested
    class Authorization {

        @Test
        @DisplayName("denies search without VIEW_PROJECT")
        void deniesWithoutPermission() {
            when(projectApplicationService.findActiveProjectInCallerOrganization(context, project.getId()))
                    .thenReturn(project);
            doThrow(new AuthorizationException("denied"))
                    .when(projectAuthorizationService)
                    .requirePermission(USER, project.getId(), ProjectPermission.VIEW_PROJECT);

            assertThatThrownBy(() -> service.search(context, project.getId(), "term", PageRequest.of(0, 20)))
                    .isInstanceOf(AuthorizationException.class);
            verifyNoInteractions(taskRepository, riskRepository, issueRepository, decisionRepository);
        }

        @Test
        @DisplayName("denies search across an organization boundary")
        void deniesCrossOrganization() {
            when(projectApplicationService.findActiveProjectInCallerOrganization(context, project.getId()))
                    .thenThrow(new ResourceNotFoundException("Project", project.getId()));

            assertThatThrownBy(() -> service.search(context, project.getId(), "term", PageRequest.of(0, 20)))
                    .isInstanceOf(ResourceNotFoundException.class);
            verifyNoInteractions(taskRepository, riskRepository, issueRepository, decisionRepository);
        }
    }

    @Nested
    class MergingAndSorting {

        @Test
        @DisplayName("merges results from all four entity types with the correct resourceType")
        void mergesAllFourTypes() {
            when(projectApplicationService.findActiveProjectInCallerOrganization(context, project.getId()))
                    .thenReturn(project);

            Task task = Task.create(project.getId(), "Fix login bug", null, null);
            Risk risk = Risk.create(project.getId(), "Login risk", ProjectPriority.HIGH);
            Issue issue = Issue.create(project.getId(), "Login issue", ProjectPriority.HIGH);
            Decision decision = Decision.create(project.getId(), "Login decision");
            Instant now = Instant.now();
            stampUpdatedAt(task, now);
            stampUpdatedAt(risk, now);
            stampUpdatedAt(issue, now);
            stampUpdatedAt(decision, now);

            when(taskRepository.search(project.getId(), "login")).thenReturn(List.of(task));
            when(riskRepository.search(project.getId(), "login")).thenReturn(List.of(risk));
            when(issueRepository.search(project.getId(), "login")).thenReturn(List.of(issue));
            when(decisionRepository.search(project.getId(), "login")).thenReturn(List.of(decision));

            Page<SearchResultResponse> page = service.search(context, project.getId(), "login", PageRequest.of(0, 20));

            assertThat(page.getContent())
                    .extracting(SearchResultResponse::resourceType)
                    .containsExactlyInAnyOrder("TASK", "RISK", "ISSUE", "DECISION");
            assertThat(page.getContent()).allMatch(r -> !r.archived());
            assertThat(page.getTotalElements()).isEqualTo(4);
        }

        @Test
        @DisplayName("sorts the merged results by updatedAt descending")
        void sortsByUpdatedAtDescending() {
            when(projectApplicationService.findActiveProjectInCallerOrganization(context, project.getId()))
                    .thenReturn(project);

            Task older = Task.create(project.getId(), "Older task", null, null);
            Risk newer = Risk.create(project.getId(), "Newer risk", ProjectPriority.HIGH);
            Instant now = Instant.now();
            stampUpdatedAt(older, now.minusSeconds(3600));
            stampUpdatedAt(newer, now);

            when(taskRepository.search(project.getId(), "term")).thenReturn(List.of(older));
            when(riskRepository.search(project.getId(), "term")).thenReturn(List.of(newer));
            when(issueRepository.search(project.getId(), "term")).thenReturn(List.of());
            when(decisionRepository.search(project.getId(), "term")).thenReturn(List.of());

            Page<SearchResultResponse> page = service.search(context, project.getId(), "term", PageRequest.of(0, 20));

            assertThat(page.getContent()).hasSize(2);
            assertThat(page.getContent().get(0).resourceType()).isEqualTo("RISK");
            assertThat(page.getContent().get(1).resourceType()).isEqualTo("TASK");
        }
    }

    @Nested
    class Pagination {

        @Test
        @DisplayName("paginates the merged in-memory result")
        void paginatesMergedResult() {
            when(projectApplicationService.findActiveProjectInCallerOrganization(context, project.getId()))
                    .thenReturn(project);

            Task task1 = Task.create(project.getId(), "Task one", null, null);
            Task task2 = Task.create(project.getId(), "Task two", null, null);
            Risk risk1 = Risk.create(project.getId(), "Risk one", ProjectPriority.HIGH);
            Risk risk2 = Risk.create(project.getId(), "Risk two", ProjectPriority.HIGH);
            Instant now = Instant.now();
            stampUpdatedAt(task1, now.minusSeconds(1));
            stampUpdatedAt(task2, now.minusSeconds(2));
            stampUpdatedAt(risk1, now.minusSeconds(3));
            stampUpdatedAt(risk2, now.minusSeconds(4));

            when(taskRepository.search(project.getId(), "one two")).thenReturn(List.of(task1, task2));
            when(riskRepository.search(project.getId(), "one two")).thenReturn(List.of(risk1, risk2));
            when(issueRepository.search(project.getId(), "one two")).thenReturn(List.of());
            when(decisionRepository.search(project.getId(), "one two")).thenReturn(List.of());

            Pageable firstPage = PageRequest.of(0, 2);
            Page<SearchResultResponse> page = service.search(context, project.getId(), "one two", firstPage);

            assertThat(page.getContent()).hasSize(2);
            assertThat(page.getTotalElements()).isEqualTo(4);
            assertThat(page.getTotalPages()).isEqualTo(2);
        }

        @Test
        @DisplayName("returns an empty page when the requested page is beyond the result count")
        void returnsEmptyPageBeyondResults() {
            when(projectApplicationService.findActiveProjectInCallerOrganization(context, project.getId()))
                    .thenReturn(project);
            when(taskRepository.search(project.getId(), "term")).thenReturn(List.of());
            when(riskRepository.search(project.getId(), "term")).thenReturn(List.of());
            when(issueRepository.search(project.getId(), "term")).thenReturn(List.of());
            when(decisionRepository.search(project.getId(), "term")).thenReturn(List.of());

            Page<SearchResultResponse> page = service.search(context, project.getId(), "term", PageRequest.of(5, 20));

            assertThat(page.getContent()).isEmpty();
            assertThat(page.getTotalElements()).isZero();
        }
    }
}
