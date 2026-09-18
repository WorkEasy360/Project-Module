package com.projectmodule.work.application;

import com.projectmodule.common.api.SortDirection;
import com.projectmodule.common.context.RequestContext;
import com.projectmodule.common.exception.BusinessRuleViolationException;
import com.projectmodule.common.exception.ConflictException;
import com.projectmodule.common.exception.ResourceNotFoundException;
import com.projectmodule.project.application.ProjectApplicationService;
import com.projectmodule.project.application.authorization.ProjectAuthorizationService;
import com.projectmodule.project.application.authorization.ProjectPermission;
import com.projectmodule.project.domain.ProjectPriority;
import com.projectmodule.work.api.dto.CreateRiskRequest;
import com.projectmodule.work.api.dto.UpdateRiskRequest;
import com.projectmodule.work.domain.Risk;
import com.projectmodule.work.domain.RiskSortField;
import com.projectmodule.work.domain.RiskStatus;
import com.projectmodule.work.domain.event.RiskDomainEvent;
import com.projectmodule.work.infrastructure.RiskRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Use cases for risk CRUD.
 *
 * <p>Same tenant/authorization pattern as {@link PhaseApplicationService}/
 * {@link TaskApplicationService}: the owning project is resolved through
 * {@code ProjectApplicationService.findActiveProjectInCallerOrganization}, and every mutation is
 * gated by the existing {@link ProjectAuthorizationService} — no second authorization system.
 *
 * <p>The status transition ({@code resolve}) goes through the same {@code PATCH} this service
 * uses for plain field edits, since {@code docs/project/04-API.md} defines no Risk endpoints at
 * all, let alone a separate action one. A plain field edit raises {@code risk.updated}; the
 * {@code OPEN -> RESOLVED} transition raises exactly the one event
 * {@code docs/project/05-EVENTS.md} defines for it — see {@link Risk}.
 */
@Service
public class RiskApplicationService {

    private final RiskRepository riskRepository;
    private final ProjectApplicationService projectApplicationService;
    private final ProjectAuthorizationService projectAuthorizationService;
    private final WorkEventRecorder workEventRecorder;

    public RiskApplicationService(RiskRepository riskRepository,
                                  ProjectApplicationService projectApplicationService,
                                  ProjectAuthorizationService projectAuthorizationService,
                                  WorkEventRecorder workEventRecorder) {
        this.riskRepository = riskRepository;
        this.projectApplicationService = projectApplicationService;
        this.projectAuthorizationService = projectAuthorizationService;
        this.workEventRecorder = workEventRecorder;
    }

    @Transactional
    public Risk createRisk(RequestContext context, UUID projectId, CreateRiskRequest request) {
        projectApplicationService.findActiveProjectInCallerOrganization(context, projectId);
        projectAuthorizationService.requirePermission(
                context.requireUserId(), projectId, ProjectPermission.EDIT_PROJECT);

        Risk risk = Risk.create(projectId, request.name(), request.priority());
        risk.describe(request.description());

        risk = riskRepository.save(risk);
        recordEvents(context, risk);

        return risk;
    }

    @Transactional(readOnly = true)
    public List<Risk> listRisks(RequestContext context, UUID projectId) {
        projectApplicationService.findActiveProjectInCallerOrganization(context, projectId);
        projectAuthorizationService.requirePermission(
                context.requireUserId(), projectId, ProjectPermission.VIEW_PROJECT);
        return riskRepository.findByProjectIdAndArchivedAtIsNullOrderByCreatedAtAsc(projectId);
    }

    /**
     * Lists risks in the project matching every supplied filter (AND), per
     * {@code docs/project/14-FILTERS-SPEC.md}, in the order requested per
     * {@code docs/project/15-LIST-SPEC.md}. See {@code TaskApplicationService.listTasks}'s
     * filtered overload for the {@code archived} semantics and the sort-default behavior.
     */
    @Transactional(readOnly = true)
    public List<Risk> listRisks(RequestContext context, UUID projectId, RiskStatus status,
                                ProjectPriority priority, boolean archived,
                                RiskSortField sortBy, SortDirection sortDir) {
        projectApplicationService.findActiveProjectInCallerOrganization(context, projectId);
        projectAuthorizationService.requirePermission(
                context.requireUserId(), projectId, ProjectPermission.VIEW_PROJECT);
        return riskRepository.findByFilters(projectId, status, priority, archived, toSort(sortBy, sortDir));
    }

    private static Sort toSort(RiskSortField sortBy, SortDirection sortDir) {
        Sort.Direction direction = sortDir == SortDirection.DESC ? Sort.Direction.DESC : Sort.Direction.ASC;
        String property = switch (sortBy == null ? RiskSortField.CREATED_AT : sortBy) {
            case CREATED_AT -> "createdAt";
            case NAME -> "name";
            case PRIORITY -> "priority";
            case STATUS -> "status";
        };
        return Sort.by(direction, property);
    }

    @Transactional
    public Risk updateRisk(RequestContext context, UUID riskId, UpdateRiskRequest request) {
        Risk risk = findActiveRisk(riskId);
        projectApplicationService.findActiveProjectInCallerOrganization(context, risk.getProjectId());
        projectAuthorizationService.requirePermission(
                context.requireUserId(), risk.getProjectId(), ProjectPermission.EDIT_PROJECT);

        if (risk.getVersion() != request.version()) {
            throw new ConflictException(
                    "Risk " + riskId + " has changed since version " + request.version()
                            + "; reload and try again");
        }

        applyUpdates(risk, request);

        risk = riskRepository.save(risk);
        recordEvents(context, risk);

        return risk;
    }

    @Transactional
    public void archiveRisk(RequestContext context, UUID riskId) {
        Risk risk = findActiveRisk(riskId);
        projectApplicationService.findActiveProjectInCallerOrganization(context, risk.getProjectId());
        projectAuthorizationService.requirePermission(
                context.requireUserId(), risk.getProjectId(), ProjectPermission.EDIT_PROJECT);

        risk.archive(context.requireUserId());
        riskRepository.save(risk);
        // No event: risk.archived is not in the current event catalogue.
    }

    private void applyUpdates(Risk risk, UpdateRiskRequest request) {
        boolean plainFieldChanged = false;

        if (request.name() != null) {
            risk.rename(request.name());
            plainFieldChanged = true;
        }
        if (request.description() != null) {
            risk.describe(request.description());
            plainFieldChanged = true;
        }
        if (request.priority() != null) {
            risk.reprioritize(request.priority());
            plainFieldChanged = true;
        }
        if (plainFieldChanged) {
            risk.recordUpdated();
        }
        if (request.status() != null) {
            applyStatusTransition(risk, request.status());
        }
    }

    private void applyStatusTransition(Risk risk, RiskStatus requestedStatus) {
        switch (requestedStatus) {
            case RESOLVED -> risk.resolve();
            case OPEN -> throw new BusinessRuleViolationException(
                    "Cannot set risk status back to OPEN; no event exists for that transition");
        }
    }

    private void recordEvents(RequestContext context, Risk risk) {
        for (RiskDomainEvent event : risk.pullDomainEvents()) {
            workEventRecorder.record(context, risk, event);
        }
    }

    /**
     * Finds a risk by id. Unlike {@code ProjectApplicationService}'s equivalent, this does not
     * also check the caller's organization: {@code PATCH}/{@code DELETE /risks/:id} carry no
     * project id, so the owning project is not known until after this lookup.
     */
    private Risk findActiveRisk(UUID riskId) {
        return riskRepository.findByIdAndArchivedAtIsNull(riskId)
                .orElseThrow(() -> new ResourceNotFoundException("Risk", riskId));
    }
}
