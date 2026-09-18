package com.projectmodule.work.application;

import com.projectmodule.common.api.SortDirection;
import com.projectmodule.common.context.RequestContext;
import com.projectmodule.common.exception.ConflictException;
import com.projectmodule.common.exception.ResourceNotFoundException;
import com.projectmodule.common.identity.ExternalUserId;
import com.projectmodule.project.application.ProjectApplicationService;
import com.projectmodule.project.application.authorization.ProjectAuthorizationService;
import com.projectmodule.project.application.authorization.ProjectPermission;
import com.projectmodule.work.api.dto.CreateDecisionRequest;
import com.projectmodule.work.api.dto.UpdateDecisionRequest;
import com.projectmodule.work.domain.Decision;
import com.projectmodule.work.domain.DecisionSortField;
import com.projectmodule.work.infrastructure.DecisionRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Use cases for decision CRUD.
 *
 * <p>Same tenant/authorization pattern as {@link IssueApplicationService}/
 * {@link RiskApplicationService}: the owning project is resolved through
 * {@code ProjectApplicationService.findActiveProjectInCallerOrganization}, and every mutation is
 * gated by the existing {@link ProjectAuthorizationService} — no second authorization system.
 *
 * <p>Like {@link IssueApplicationService}, this has no {@link WorkEventRecorder} dependency: the
 * approved specification ({@code docs/project/09-DECISION-SPEC.md}) defines no Decision events,
 * so there is nothing to record. Decision mutations do not appear in the outbox or the project
 * activity audit trail.
 */
@Service
public class DecisionApplicationService {

    private final DecisionRepository decisionRepository;
    private final ProjectApplicationService projectApplicationService;
    private final ProjectAuthorizationService projectAuthorizationService;

    public DecisionApplicationService(DecisionRepository decisionRepository,
                                      ProjectApplicationService projectApplicationService,
                                      ProjectAuthorizationService projectAuthorizationService) {
        this.decisionRepository = decisionRepository;
        this.projectApplicationService = projectApplicationService;
        this.projectAuthorizationService = projectAuthorizationService;
    }

    @Transactional
    public Decision createDecision(RequestContext context, UUID projectId, CreateDecisionRequest request) {
        projectApplicationService.findActiveProjectInCallerOrganization(context, projectId);
        projectAuthorizationService.requirePermission(
                context.requireUserId(), projectId, ProjectPermission.EDIT_PROJECT);

        Decision decision = Decision.create(projectId, request.name());
        decision.describe(request.description());
        if (request.decidedBy() != null) {
            decision.attributeTo(ExternalUserId.of(request.decidedBy()));
        }

        return decisionRepository.save(decision);
    }

    @Transactional(readOnly = true)
    public List<Decision> listDecisions(RequestContext context, UUID projectId) {
        projectApplicationService.findActiveProjectInCallerOrganization(context, projectId);
        projectAuthorizationService.requirePermission(
                context.requireUserId(), projectId, ProjectPermission.VIEW_PROJECT);
        return decisionRepository.findByProjectIdAndArchivedAtIsNullOrderByCreatedAtAsc(projectId);
    }

    /**
     * Lists decisions in the project matching every supplied filter (AND), per
     * {@code docs/project/14-FILTERS-SPEC.md}, in the order requested per
     * {@code docs/project/15-LIST-SPEC.md}. See {@code TaskApplicationService.listTasks}'s
     * filtered overload for the {@code archived} semantics and the sort-default behavior.
     */
    @Transactional(readOnly = true)
    public List<Decision> listDecisions(RequestContext context, UUID projectId, UUID decidedBy, boolean archived,
                                        DecisionSortField sortBy, SortDirection sortDir) {
        projectApplicationService.findActiveProjectInCallerOrganization(context, projectId);
        projectAuthorizationService.requirePermission(
                context.requireUserId(), projectId, ProjectPermission.VIEW_PROJECT);
        return decisionRepository.findByFilters(projectId, decidedBy, archived, toSort(sortBy, sortDir));
    }

    private static Sort toSort(DecisionSortField sortBy, SortDirection sortDir) {
        Sort.Direction direction = sortDir == SortDirection.DESC ? Sort.Direction.DESC : Sort.Direction.ASC;
        String property = switch (sortBy == null ? DecisionSortField.CREATED_AT : sortBy) {
            case CREATED_AT -> "createdAt";
            case NAME -> "name";
        };
        return Sort.by(direction, property);
    }

    @Transactional
    public Decision updateDecision(RequestContext context, UUID decisionId, UpdateDecisionRequest request) {
        Decision decision = findActiveDecision(decisionId);
        projectApplicationService.findActiveProjectInCallerOrganization(context, decision.getProjectId());
        projectAuthorizationService.requirePermission(
                context.requireUserId(), decision.getProjectId(), ProjectPermission.EDIT_PROJECT);

        if (decision.getVersion() != request.version()) {
            throw new ConflictException(
                    "Decision " + decisionId + " has changed since version " + request.version()
                            + "; reload and try again");
        }

        applyUpdates(decision, request);

        return decisionRepository.save(decision);
    }

    @Transactional
    public void archiveDecision(RequestContext context, UUID decisionId) {
        Decision decision = findActiveDecision(decisionId);
        projectApplicationService.findActiveProjectInCallerOrganization(context, decision.getProjectId());
        projectAuthorizationService.requirePermission(
                context.requireUserId(), decision.getProjectId(), ProjectPermission.EDIT_PROJECT);

        decision.archive(context.requireUserId());
        decisionRepository.save(decision);
    }

    private void applyUpdates(Decision decision, UpdateDecisionRequest request) {
        if (request.name() != null) {
            decision.rename(request.name());
        }
        if (request.description() != null) {
            decision.describe(request.description());
        }
        if (request.decidedBy() != null) {
            decision.attributeTo(ExternalUserId.of(request.decidedBy()));
        }
    }

    /**
     * Finds a decision by id. Unlike {@code ProjectApplicationService}'s equivalent, this does
     * not also check the caller's organization: {@code PATCH}/{@code DELETE /decisions/:id}
     * carry no project id, so the owning project is not known until after this lookup.
     */
    private Decision findActiveDecision(UUID decisionId) {
        return decisionRepository.findByIdAndArchivedAtIsNull(decisionId)
                .orElseThrow(() -> new ResourceNotFoundException("Decision", decisionId));
    }
}
