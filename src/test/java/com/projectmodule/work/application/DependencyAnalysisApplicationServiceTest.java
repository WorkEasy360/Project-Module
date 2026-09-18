package com.projectmodule.work.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;

import com.projectmodule.common.context.ActorType;
import com.projectmodule.common.context.ImmutableRequestContext;
import com.projectmodule.common.context.RequestContext;
import com.projectmodule.common.exception.AuthorizationException;
import com.projectmodule.common.identity.ExternalUserId;
import com.projectmodule.common.identity.OrganizationId;
import com.projectmodule.project.application.ProjectApplicationService;
import com.projectmodule.project.application.authorization.ProjectAuthorizationService;
import com.projectmodule.project.application.authorization.ProjectPermission;
import com.projectmodule.project.domain.Project;
import com.projectmodule.project.domain.ProjectPriority;
import com.projectmodule.work.api.DependencyMapper;
import com.projectmodule.work.api.dto.DependencyAnalysisResponse;
import com.projectmodule.work.domain.Dependency;
import com.projectmodule.work.domain.Task;
import com.projectmodule.work.infrastructure.DependencyRepository;
import com.projectmodule.work.infrastructure.TaskRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DependencyAnalysisApplicationServiceTest {

    private static final OrganizationId ORG = OrganizationId.of(UUID.randomUUID());
    private static final ExternalUserId USER = ExternalUserId.of(UUID.randomUUID());

    @Mock
    private TaskRepository taskRepository;

    @Mock
    private DependencyRepository dependencyRepository;

    @Mock
    private ProjectApplicationService projectApplicationService;

    @Mock
    private ProjectAuthorizationService projectAuthorizationService;

    private DependencyAnalysisApplicationService service;
    private RequestContext context;
    private Project project;

    @BeforeEach
    void setUp() {
        service = new DependencyAnalysisApplicationService(taskRepository, dependencyRepository,
                new DependencyMapper(), projectApplicationService, projectAuthorizationService);
        context = new ImmutableRequestContext(Optional.of(USER), Optional.of(ORG), "corr-1", ActorType.HUMAN);
        project = Project.create(ORG, "Apollo", USER, ProjectPriority.MEDIUM);

        when(projectApplicationService.findActiveProjectInCallerOrganization(context, project.getId()))
                .thenReturn(project);
    }

    @Test
    @DisplayName("a task blocked by a not-yet-completed prerequisite is reported")
    void reportsBlockedTask() {
        Task dependent = Task.create(project.getId(), "Dependent", null, null);
        Task prerequisite = Task.create(project.getId(), "Prerequisite", null, null);
        Dependency dependency = Dependency.create(dependent.getId(), prerequisite.getId());

        when(taskRepository.findByProjectIdAndArchivedAtIsNullOrderByCreatedAtAsc(project.getId()))
                .thenReturn(List.of(dependent, prerequisite));
        when(dependencyRepository.findByDependentTaskIdInAndArchivedAtIsNull(any()))
                .thenReturn(List.of(dependency));
        when(taskRepository.findAllById(any())).thenReturn(List.of(prerequisite));

        DependencyAnalysisResponse result = service.getAnalysis(context, project.getId());

        assertThat(result.blockedTaskIds()).containsExactly(dependent.getId());
        assertThat(result.cycles()).isEmpty();
        assertThat(result.brokenDependencies()).isEmpty();
    }

    @Test
    @DisplayName("a task is not reported blocked once its prerequisite is completed")
    void doesNotReportBlockedWhenPrerequisiteCompleted() {
        Task dependent = Task.create(project.getId(), "Dependent", null, null);
        Task prerequisite = Task.create(project.getId(), "Prerequisite", null, null);
        prerequisite.complete();
        Dependency dependency = Dependency.create(dependent.getId(), prerequisite.getId());

        when(taskRepository.findByProjectIdAndArchivedAtIsNullOrderByCreatedAtAsc(project.getId()))
                .thenReturn(List.of(dependent, prerequisite));
        when(dependencyRepository.findByDependentTaskIdInAndArchivedAtIsNull(any()))
                .thenReturn(List.of(dependency));
        when(taskRepository.findAllById(any())).thenReturn(List.of(prerequisite));

        DependencyAnalysisResponse result = service.getAnalysis(context, project.getId());

        assertThat(result.blockedTaskIds()).isEmpty();
    }

    @Test
    @DisplayName("reports a broken dependency, excluded from blocked-task detection")
    void reportsBrokenDependency() {
        Task dependent = Task.create(project.getId(), "Dependent", null, null);
        Task prerequisite = Task.create(project.getId(), "Prerequisite", null, null);
        Dependency dependency = Dependency.create(dependent.getId(), prerequisite.getId());
        dependency.markBroken();

        when(taskRepository.findByProjectIdAndArchivedAtIsNullOrderByCreatedAtAsc(project.getId()))
                .thenReturn(List.of(dependent, prerequisite));
        when(dependencyRepository.findByDependentTaskIdInAndArchivedAtIsNull(any()))
                .thenReturn(List.of(dependency));
        when(taskRepository.findAllById(any())).thenReturn(List.of(prerequisite));

        DependencyAnalysisResponse result = service.getAnalysis(context, project.getId());

        assertThat(result.brokenDependencies()).extracting(r -> r.id()).containsExactly(dependency.getId());
        assertThat(result.blockedTaskIds()).isEmpty();
    }

    @Test
    @DisplayName("detects a 3-node cycle across dependencies")
    void detectsThreeNodeCycle() {
        Task a = Task.create(project.getId(), "A", null, null);
        Task b = Task.create(project.getId(), "B", null, null);
        Task c = Task.create(project.getId(), "C", null, null);
        Dependency aToB = Dependency.create(a.getId(), b.getId());
        Dependency bToC = Dependency.create(b.getId(), c.getId());
        Dependency cToA = Dependency.create(c.getId(), a.getId());

        when(taskRepository.findByProjectIdAndArchivedAtIsNullOrderByCreatedAtAsc(project.getId()))
                .thenReturn(List.of(a, b, c));
        when(dependencyRepository.findByDependentTaskIdInAndArchivedAtIsNull(any()))
                .thenReturn(List.of(aToB, bToC, cToA));
        when(taskRepository.findAllById(any())).thenReturn(List.of(a, b, c));

        DependencyAnalysisResponse result = service.getAnalysis(context, project.getId());

        assertThat(result.cycles()).hasSize(1);
        assertThat(result.cycles().get(0)).containsExactlyInAnyOrder(a.getId(), b.getId(), c.getId());
    }

    @Test
    @DisplayName("a simple chain with no cycle reports no false positive")
    void noFalsePositiveOnSimpleChain() {
        Task a = Task.create(project.getId(), "A", null, null);
        Task b = Task.create(project.getId(), "B", null, null);
        Dependency aToB = Dependency.create(a.getId(), b.getId());

        when(taskRepository.findByProjectIdAndArchivedAtIsNullOrderByCreatedAtAsc(project.getId()))
                .thenReturn(List.of(a, b));
        when(dependencyRepository.findByDependentTaskIdInAndArchivedAtIsNull(any()))
                .thenReturn(List.of(aToB));
        when(taskRepository.findAllById(any())).thenReturn(List.of(b));

        DependencyAnalysisResponse result = service.getAnalysis(context, project.getId());

        assertThat(result.cycles()).isEmpty();
    }

    @Test
    @DisplayName("requires VIEW_PROJECT")
    void requiresViewProjectPermission() {
        doThrow(new AuthorizationException("denied"))
                .when(projectAuthorizationService)
                .requirePermission(USER, project.getId(), ProjectPermission.VIEW_PROJECT);

        assertThatThrownBy(() -> service.getAnalysis(context, project.getId()))
                .isInstanceOf(AuthorizationException.class);
    }
}
