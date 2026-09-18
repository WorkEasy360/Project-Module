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
import com.projectmodule.common.exception.IntegrationUnavailableException;
import com.projectmodule.common.exception.ResourceNotFoundException;
import com.projectmodule.common.exception.ValidationException;
import com.projectmodule.common.identity.ExternalUserId;
import com.projectmodule.common.identity.OrganizationId;
import com.projectmodule.integration.port.AIProviderPort;
import com.projectmodule.integration.port.AIProviderResponse;
import com.projectmodule.project.application.ProjectApplicationService;
import com.projectmodule.project.application.authorization.ProjectAuthorizationService;
import com.projectmodule.project.application.authorization.ProjectPermission;
import com.projectmodule.project.domain.Project;
import com.projectmodule.project.domain.ProjectPriority;
import com.projectmodule.work.api.dto.ProjectHealthResponse;
import com.projectmodule.work.api.dto.ProjectSummaryReportResponse;
import com.projectmodule.work.domain.AIRecommendation;
import com.projectmodule.work.domain.RecommendationStatus;
import com.projectmodule.work.domain.RecommendationType;
import com.projectmodule.work.domain.RiskStatus;
import com.projectmodule.work.domain.Task;
import com.projectmodule.work.domain.TaskStatus;
import com.projectmodule.work.infrastructure.AIRecommendationRepository;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
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
class AIRecommendationApplicationServiceTest {

    private static final OrganizationId ORG = OrganizationId.of(UUID.randomUUID());
    private static final ExternalUserId USER = ExternalUserId.of(UUID.randomUUID());

    @Mock
    private AIRecommendationRepository aiRecommendationRepository;

    @Mock
    private AIRecommendationEventRecorder aiRecommendationEventRecorder;

    @Mock
    private AIProviderPort aiProviderPort;

    @Mock
    private TaskApplicationService taskApplicationService;

    @Mock
    private RiskApplicationService riskApplicationService;

    @Mock
    private ReportsApplicationService reportsApplicationService;

    @Mock
    private ProjectHealthApplicationService projectHealthApplicationService;

    @Mock
    private ProjectApplicationService projectApplicationService;

    @Mock
    private ProjectAuthorizationService projectAuthorizationService;

    private AIRecommendationApplicationService service;
    private RequestContext context;
    private Project project;

    @BeforeEach
    void setUp() {
        service = new AIRecommendationApplicationService(aiRecommendationRepository, aiRecommendationEventRecorder,
                aiProviderPort, taskApplicationService, riskApplicationService, reportsApplicationService,
                projectHealthApplicationService, projectApplicationService, projectAuthorizationService);
        context = new ImmutableRequestContext(Optional.of(USER), Optional.of(ORG), "corr-1", ActorType.HUMAN);
        project = Project.create(ORG, "Apollo", USER, ProjectPriority.MEDIUM);
    }

    private static ProjectSummaryReportResponse emptySummary() {
        Map<TaskStatus, Long> taskCounts = new EnumMap<>(TaskStatus.class);
        for (TaskStatus s : TaskStatus.values()) {
            taskCounts.put(s, 0L);
        }
        Map<RiskStatus, Long> riskCounts = new EnumMap<>(RiskStatus.class);
        for (RiskStatus s : RiskStatus.values()) {
            riskCounts.put(s, 0L);
        }
        Map<ProjectPriority, Long> priorityCounts = new EnumMap<>(ProjectPriority.class);
        for (ProjectPriority p : ProjectPriority.values()) {
            priorityCounts.put(p, 0L);
        }
        return new ProjectSummaryReportResponse(taskCounts, riskCounts, priorityCounts, priorityCounts, 0, 0, 0);
    }

    private static ProjectHealthResponse emptyHealth() {
        return new ProjectHealthResponse(0, 0, 0, 0, 0);
    }

    @Nested
    class RequestRecommendation {

        @Test
        @DisplayName("persists nothing and propagates the error when the AI provider is unavailable")
        void providerFailurePersistsNothing() {
            when(projectApplicationService.findActiveProjectInCallerOrganization(context, project.getId()))
                    .thenReturn(project);
            when(reportsApplicationService.getSummary(context, project.getId())).thenReturn(emptySummary());
            when(projectHealthApplicationService.getHealth(context, project.getId())).thenReturn(emptyHealth());
            when(aiProviderPort.generate(any()))
                    .thenThrow(new IntegrationUnavailableException("AI provider", null));

            assertThatThrownBy(() -> service.requestRecommendation(
                    context, project.getId(), RecommendationType.PROJECT_SUMMARY, null, null))
                    .isInstanceOf(IntegrationUnavailableException.class);

            verify(aiRecommendationRepository, never()).save(any());
            verifyNoInteractions(aiRecommendationEventRecorder);
        }

        @Test
        @DisplayName("on success, persists the recommendation and records the created event")
        void successPersistsRecommendation() {
            when(projectApplicationService.findActiveProjectInCallerOrganization(context, project.getId()))
                    .thenReturn(project);
            when(reportsApplicationService.getSummary(context, project.getId())).thenReturn(emptySummary());
            when(projectHealthApplicationService.getHealth(context, project.getId())).thenReturn(emptyHealth());
            when(aiProviderPort.generate(any()))
                    .thenReturn(new AIProviderResponse("Summary", "Because...", null));
            when(aiRecommendationRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            AIRecommendation created = service.requestRecommendation(
                    context, project.getId(), RecommendationType.PROJECT_SUMMARY, null, null);

            assertThat(created.getTitle()).isEqualTo("Summary");
            assertThat(created.getStatus()).isEqualTo(RecommendationStatus.PENDING);
            verify(aiRecommendationRepository).save(any());
            verify(aiRecommendationEventRecorder).recordCreated(context, created);
        }

        @Test
        @DisplayName("TASK_BREAKDOWN requires resourceId")
        void taskBreakdownRequiresResourceId() {
            when(projectApplicationService.findActiveProjectInCallerOrganization(context, project.getId()))
                    .thenReturn(project);

            assertThatThrownBy(() -> service.requestRecommendation(
                    context, project.getId(), RecommendationType.TASK_BREAKDOWN, null, null))
                    .isInstanceOf(ValidationException.class);
            verifyNoInteractions(aiProviderPort);
        }

        @Test
        @DisplayName("TASK_BREAKDOWN rejects a resourceId belonging to a different project")
        void taskBreakdownRejectsCrossProjectResource() {
            UUID otherProjectId = UUID.randomUUID();
            Task foreignTask = Task.create(otherProjectId, "Foreign task", null, null);
            when(projectApplicationService.findActiveProjectInCallerOrganization(context, project.getId()))
                    .thenReturn(project);
            when(taskApplicationService.getTask(context, foreignTask.getId())).thenReturn(foreignTask);

            assertThatThrownBy(() -> service.requestRecommendation(
                    context, project.getId(), RecommendationType.TASK_BREAKDOWN, foreignTask.getId(), null))
                    .isInstanceOf(ValidationException.class);
            verifyNoInteractions(aiProviderPort);
        }

        @Test
        @DisplayName("WHAT_IF requires a question")
        void whatIfRequiresQuestion() {
            when(projectApplicationService.findActiveProjectInCallerOrganization(context, project.getId()))
                    .thenReturn(project);

            assertThatThrownBy(() -> service.requestRecommendation(
                    context, project.getId(), RecommendationType.WHAT_IF, null, "  "))
                    .isInstanceOf(ValidationException.class);
            verifyNoInteractions(aiProviderPort);
        }

        @Test
        @DisplayName("requires EDIT_PROJECT")
        void requiresEditProjectPermission() {
            when(projectApplicationService.findActiveProjectInCallerOrganization(context, project.getId()))
                    .thenReturn(project);
            doThrow(new AuthorizationException("denied"))
                    .when(projectAuthorizationService)
                    .requirePermission(USER, project.getId(), ProjectPermission.EDIT_PROJECT);

            assertThatThrownBy(() -> service.requestRecommendation(
                    context, project.getId(), RecommendationType.RISK_ANALYSIS, null, null))
                    .isInstanceOf(AuthorizationException.class);
            verifyNoInteractions(aiProviderPort);
        }
    }

    @Nested
    class ListRecommendations {

        @Test
        @DisplayName("requires VIEW_PROJECT")
        void requiresViewProjectPermission() {
            when(projectApplicationService.findActiveProjectInCallerOrganization(context, project.getId()))
                    .thenReturn(project);
            doThrow(new AuthorizationException("denied"))
                    .when(projectAuthorizationService)
                    .requirePermission(USER, project.getId(), ProjectPermission.VIEW_PROJECT);

            assertThatThrownBy(() -> service.listRecommendations(context, project.getId()))
                    .isInstanceOf(AuthorizationException.class);
        }

        @Test
        @DisplayName("lists recommendations for the project")
        void listsRecommendations() {
            AIRecommendation recommendation = AIRecommendation.create(project.getId(),
                    RecommendationType.RISK_ANALYSIS, null, null, "t", "r", null, USER);
            when(projectApplicationService.findActiveProjectInCallerOrganization(context, project.getId()))
                    .thenReturn(project);
            when(aiRecommendationRepository.findByProjectIdOrderByCreatedAtDesc(project.getId()))
                    .thenReturn(List.of(recommendation));

            assertThat(service.listRecommendations(context, project.getId())).containsExactly(recommendation);
        }
    }

    @Nested
    class AcceptReject {

        @Test
        @DisplayName("accept: not found")
        void acceptReportsNotFound() {
            UUID id = UUID.randomUUID();
            when(aiRecommendationRepository.findById(id)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.acceptRecommendation(context, id))
                    .isInstanceOf(ResourceNotFoundException.class);
        }

        @Test
        @DisplayName("accept: transitions to ACCEPTED and records the event")
        void acceptTransitionsAndRecords() {
            AIRecommendation recommendation = AIRecommendation.create(project.getId(),
                    RecommendationType.RISK_ANALYSIS, null, null, "t", "r", null, USER);
            when(aiRecommendationRepository.findById(recommendation.getId())).thenReturn(Optional.of(recommendation));
            when(projectApplicationService.findActiveProjectInCallerOrganization(context, project.getId()))
                    .thenReturn(project);
            when(aiRecommendationRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            AIRecommendation accepted = service.acceptRecommendation(context, recommendation.getId());

            assertThat(accepted.getStatus()).isEqualTo(RecommendationStatus.ACCEPTED);
            verify(aiRecommendationEventRecorder).recordAccepted(context, accepted);
        }

        @Test
        @DisplayName("reject: transitions to REJECTED and records the event")
        void rejectTransitionsAndRecords() {
            AIRecommendation recommendation = AIRecommendation.create(project.getId(),
                    RecommendationType.RISK_ANALYSIS, null, null, "t", "r", null, USER);
            when(aiRecommendationRepository.findById(recommendation.getId())).thenReturn(Optional.of(recommendation));
            when(projectApplicationService.findActiveProjectInCallerOrganization(context, project.getId()))
                    .thenReturn(project);
            when(aiRecommendationRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            AIRecommendation rejected = service.rejectRecommendation(context, recommendation.getId());

            assertThat(rejected.getStatus()).isEqualTo(RecommendationStatus.REJECTED);
            verify(aiRecommendationEventRecorder).recordRejected(context, rejected);
        }
    }
}
