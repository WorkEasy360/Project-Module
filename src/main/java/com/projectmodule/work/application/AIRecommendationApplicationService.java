package com.projectmodule.work.application;

import com.projectmodule.common.context.RequestContext;
import com.projectmodule.common.exception.ResourceNotFoundException;
import com.projectmodule.common.exception.ValidationException;
import com.projectmodule.integration.port.AIProviderPort;
import com.projectmodule.integration.port.AIProviderRequest;
import com.projectmodule.integration.port.AIProviderResponse;
import com.projectmodule.project.application.ProjectApplicationService;
import com.projectmodule.project.application.authorization.ProjectAuthorizationService;
import com.projectmodule.project.application.authorization.ProjectPermission;
import com.projectmodule.work.api.dto.ProjectHealthResponse;
import com.projectmodule.work.api.dto.ProjectSummaryReportResponse;
import com.projectmodule.work.domain.AIRecommendation;
import com.projectmodule.work.domain.RecommendationType;
import com.projectmodule.work.domain.Risk;
import com.projectmodule.work.domain.Task;
import com.projectmodule.work.infrastructure.AIRecommendationRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * AI recommendation lifecycle, per the approved {@code docs/project/26-AI-RECOMMENDATIONS-SPEC.md}.
 *
 * <p>Same authorization pattern as every other project-scoped service. Context for the
 * {@link AIProviderPort} call is assembled here from existing, unmodified read-only application
 * services — {@link TaskApplicationService}, {@link RiskApplicationService},
 * {@link ReportsApplicationService}, {@link ProjectHealthApplicationService} — never from a
 * repository directly, and never mutated. If the provider call fails (always, with
 * {@code UnavailableAIProviderAdapter} — see {@code docs/project/25-AI-ARCHITECTURE-SPEC.md}
 * §4/§6), nothing is persisted and the exception propagates unchanged.
 */
@Service
public class AIRecommendationApplicationService {

    private final AIRecommendationRepository aiRecommendationRepository;
    private final AIRecommendationEventRecorder aiRecommendationEventRecorder;
    private final AIProviderPort aiProviderPort;
    private final TaskApplicationService taskApplicationService;
    private final RiskApplicationService riskApplicationService;
    private final ReportsApplicationService reportsApplicationService;
    private final ProjectHealthApplicationService projectHealthApplicationService;
    private final ProjectApplicationService projectApplicationService;
    private final ProjectAuthorizationService projectAuthorizationService;

    public AIRecommendationApplicationService(AIRecommendationRepository aiRecommendationRepository,
                                              AIRecommendationEventRecorder aiRecommendationEventRecorder,
                                              AIProviderPort aiProviderPort,
                                              TaskApplicationService taskApplicationService,
                                              RiskApplicationService riskApplicationService,
                                              ReportsApplicationService reportsApplicationService,
                                              ProjectHealthApplicationService projectHealthApplicationService,
                                              ProjectApplicationService projectApplicationService,
                                              ProjectAuthorizationService projectAuthorizationService) {
        this.aiRecommendationRepository = aiRecommendationRepository;
        this.aiRecommendationEventRecorder = aiRecommendationEventRecorder;
        this.aiProviderPort = aiProviderPort;
        this.taskApplicationService = taskApplicationService;
        this.riskApplicationService = riskApplicationService;
        this.reportsApplicationService = reportsApplicationService;
        this.projectHealthApplicationService = projectHealthApplicationService;
        this.projectApplicationService = projectApplicationService;
        this.projectAuthorizationService = projectAuthorizationService;
    }

    @Transactional
    public AIRecommendation requestRecommendation(RequestContext context, UUID projectId, RecommendationType type,
                                                  UUID resourceId, String question) {
        projectApplicationService.findActiveProjectInCallerOrganization(context, projectId);
        projectAuthorizationService.requirePermission(
                context.requireUserId(), projectId, ProjectPermission.EDIT_PROJECT);

        String projectContext = gatherContext(context, projectId, type, resourceId, question);

        AIProviderResponse response = aiProviderPort.generate(
                new AIProviderRequest(type.name(), projectContext, question));

        AIRecommendation recommendation = AIRecommendation.create(projectId, type, resourceId, question,
                response.title(), response.rationale(), response.payload(), context.requireUserId());
        recommendation = aiRecommendationRepository.save(recommendation);
        aiRecommendationEventRecorder.recordCreated(context, recommendation);
        return recommendation;
    }

    @Transactional(readOnly = true)
    public AIRecommendation getRecommendation(RequestContext context, UUID recommendationId) {
        AIRecommendation recommendation = findRecommendation(recommendationId);
        projectApplicationService.findActiveProjectInCallerOrganization(context, recommendation.getProjectId());
        projectAuthorizationService.requirePermission(
                context.requireUserId(), recommendation.getProjectId(), ProjectPermission.VIEW_PROJECT);
        return recommendation;
    }

    @Transactional(readOnly = true)
    public List<AIRecommendation> listRecommendations(RequestContext context, UUID projectId) {
        projectApplicationService.findActiveProjectInCallerOrganization(context, projectId);
        projectAuthorizationService.requirePermission(
                context.requireUserId(), projectId, ProjectPermission.VIEW_PROJECT);
        return aiRecommendationRepository.findByProjectIdOrderByCreatedAtDesc(projectId);
    }

    @Transactional
    public AIRecommendation acceptRecommendation(RequestContext context, UUID recommendationId) {
        AIRecommendation recommendation = findRecommendation(recommendationId);
        projectApplicationService.findActiveProjectInCallerOrganization(context, recommendation.getProjectId());
        projectAuthorizationService.requirePermission(
                context.requireUserId(), recommendation.getProjectId(), ProjectPermission.EDIT_PROJECT);

        recommendation.accept(context.requireUserId());
        recommendation = aiRecommendationRepository.save(recommendation);
        aiRecommendationEventRecorder.recordAccepted(context, recommendation);
        return recommendation;
    }

    @Transactional
    public AIRecommendation rejectRecommendation(RequestContext context, UUID recommendationId) {
        AIRecommendation recommendation = findRecommendation(recommendationId);
        projectApplicationService.findActiveProjectInCallerOrganization(context, recommendation.getProjectId());
        projectAuthorizationService.requirePermission(
                context.requireUserId(), recommendation.getProjectId(), ProjectPermission.EDIT_PROJECT);

        recommendation.reject(context.requireUserId());
        recommendation = aiRecommendationRepository.save(recommendation);
        aiRecommendationEventRecorder.recordRejected(context, recommendation);
        return recommendation;
    }

    private AIRecommendation findRecommendation(UUID recommendationId) {
        return aiRecommendationRepository.findById(recommendationId)
                .orElseThrow(() -> new ResourceNotFoundException("AIRecommendation", recommendationId));
    }

    /**
     * Builds the plain-text context passed to the AI provider, per
     * {@code docs/project/26-AI-RECOMMENDATIONS-SPEC.md} §3 — reusing existing read-only
     * application services exclusively, never a repository directly.
     */
    private String gatherContext(RequestContext context, UUID projectId, RecommendationType type,
                                 UUID resourceId, String question) {
        return switch (type) {
            case TASK_BREAKDOWN, TASK_IMPROVEMENT -> {
                if (resourceId == null) {
                    throw new ValidationException("resourceId is required for " + type);
                }
                Task task = taskApplicationService.getTask(context, resourceId);
                if (!task.getProjectId().equals(projectId)) {
                    throw new ValidationException("resourceId does not belong to this project");
                }
                yield "Task: " + task.getName() + "\nDescription: " + task.getDescription().orElse("(none)")
                        + "\nStatus: " + task.getStatus();
            }
            case PROJECT_SUMMARY -> projectSummaryContext(context, projectId);
            case RISK_ANALYSIS -> {
                List<Risk> risks = riskApplicationService.listRisks(context, projectId);
                StringBuilder sb = new StringBuilder("Risks:\n");
                for (Risk risk : risks) {
                    sb.append("- ").append(risk.getName()).append(" [").append(risk.getPriority())
                            .append(", ").append(risk.getStatus()).append("]\n");
                }
                yield sb.toString();
            }
            case WHAT_IF -> {
                if (question == null || question.isBlank()) {
                    throw new ValidationException("question is required for WHAT_IF");
                }
                yield projectSummaryContext(context, projectId) + "\nQuestion: " + question;
            }
        };
    }

    private String projectSummaryContext(RequestContext context, UUID projectId) {
        ProjectSummaryReportResponse summary = reportsApplicationService.getSummary(context, projectId);
        ProjectHealthResponse health = projectHealthApplicationService.getHealth(context, projectId);
        return "Task counts by status: " + summary.taskCountByStatus()
                + "\nDecision count: " + summary.decisionCount()
                + "\nOverdue items: " + health.overdueItemCount()
                + "\nUnresolved risks: " + health.unresolvedRiskCount()
                + "\nUnresolved issues: " + health.unresolvedIssueCount()
                + "\nBlocked tasks: " + health.blockedTaskCount()
                + "\nBroken dependencies: " + health.brokenDependencyCount();
    }
}
