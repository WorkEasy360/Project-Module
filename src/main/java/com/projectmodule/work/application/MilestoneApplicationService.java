package com.projectmodule.work.application;

import com.projectmodule.common.context.RequestContext;
import com.projectmodule.common.exception.BusinessRuleViolationException;
import com.projectmodule.common.exception.ConflictException;
import com.projectmodule.common.exception.ResourceNotFoundException;
import com.projectmodule.project.application.ProjectApplicationService;
import com.projectmodule.project.application.authorization.ProjectAuthorizationService;
import com.projectmodule.project.application.authorization.ProjectPermission;
import com.projectmodule.work.api.dto.CreateMilestoneRequest;
import com.projectmodule.work.api.dto.UpdateMilestoneRequest;
import com.projectmodule.work.domain.Milestone;
import com.projectmodule.work.domain.MilestoneStatus;
import com.projectmodule.work.domain.event.MilestoneDomainEvent;
import com.projectmodule.work.infrastructure.MilestoneRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Use cases for milestone CRUD.
 *
 * <p>Same tenant/authorization pattern as {@link PhaseApplicationService}: the owning project is
 * resolved through {@code ProjectApplicationService.findActiveProjectInCallerOrganization}, and
 * every mutation is gated by the existing {@code ProjectAuthorizationService} — no second
 * authorization system.
 *
 * <p>Status transitions ({@code complete}/{@code markAtRisk}) go through the same
 * {@code PATCH} this service uses for plain field edits, since
 * {@code docs/project/04-API.md} defines no separate action endpoint for a milestone. A plain
 * field edit raises no event; a status transition raises exactly the one event
 * {@code docs/project/05-EVENTS.md} defines for it — see {@link Milestone}.
 */
@Service
public class MilestoneApplicationService {

    private final MilestoneRepository milestoneRepository;
    private final ProjectApplicationService projectApplicationService;
    private final ProjectAuthorizationService projectAuthorizationService;
    private final WorkEventRecorder workEventRecorder;

    public MilestoneApplicationService(MilestoneRepository milestoneRepository,
                                       ProjectApplicationService projectApplicationService,
                                       ProjectAuthorizationService projectAuthorizationService,
                                       WorkEventRecorder workEventRecorder) {
        this.milestoneRepository = milestoneRepository;
        this.projectApplicationService = projectApplicationService;
        this.projectAuthorizationService = projectAuthorizationService;
        this.workEventRecorder = workEventRecorder;
    }

    @Transactional
    public Milestone createMilestone(RequestContext context, UUID projectId, CreateMilestoneRequest request) {
        projectApplicationService.findActiveProjectInCallerOrganization(context, projectId);
        projectAuthorizationService.requirePermission(
                context.requireUserId(), projectId, ProjectPermission.EDIT_PROJECT);

        Milestone milestone = Milestone.create(projectId, request.phaseId(), request.name(), request.dueDate());
        milestone.describe(request.description());

        milestone = milestoneRepository.save(milestone);
        recordEvents(context, milestone);

        return milestone;
    }

    @Transactional(readOnly = true)
    public List<Milestone> listMilestones(RequestContext context, UUID projectId) {
        projectApplicationService.findActiveProjectInCallerOrganization(context, projectId);
        projectAuthorizationService.requirePermission(
                context.requireUserId(), projectId, ProjectPermission.VIEW_PROJECT);
        return milestoneRepository.findByProjectIdAndArchivedAtIsNullOrderByCreatedAtAsc(projectId);
    }

    @Transactional
    public Milestone updateMilestone(RequestContext context, UUID milestoneId, UpdateMilestoneRequest request) {
        Milestone milestone = findActiveMilestone(milestoneId);
        projectApplicationService.findActiveProjectInCallerOrganization(context, milestone.getProjectId());
        projectAuthorizationService.requirePermission(
                context.requireUserId(), milestone.getProjectId(), ProjectPermission.EDIT_PROJECT);

        if (milestone.getVersion() != request.version()) {
            throw new ConflictException(
                    "Milestone " + milestoneId + " has changed since version " + request.version()
                            + "; reload and try again");
        }

        applyUpdates(milestone, request);

        milestone = milestoneRepository.save(milestone);
        recordEvents(context, milestone);

        return milestone;
    }

    @Transactional
    public void archiveMilestone(RequestContext context, UUID milestoneId) {
        Milestone milestone = findActiveMilestone(milestoneId);
        projectApplicationService.findActiveProjectInCallerOrganization(context, milestone.getProjectId());
        projectAuthorizationService.requirePermission(
                context.requireUserId(), milestone.getProjectId(), ProjectPermission.EDIT_PROJECT);

        milestone.archive(context.requireUserId());
        milestoneRepository.save(milestone);
        // No event: milestone.archived is not in the current event catalogue.
    }

    private void applyUpdates(Milestone milestone, UpdateMilestoneRequest request) {
        if (request.name() != null) {
            milestone.rename(request.name());
        }
        if (request.description() != null) {
            milestone.describe(request.description());
        }
        if (request.dueDate() != null) {
            milestone.reschedule(request.dueDate());
        }
        if (request.phaseId() != null) {
            milestone.reassignPhase(request.phaseId());
        }
        if (request.status() != null) {
            applyStatusTransition(milestone, request.status());
        }
    }

    private void applyStatusTransition(Milestone milestone, MilestoneStatus requestedStatus) {
        switch (requestedStatus) {
            case COMPLETED -> milestone.complete();
            case AT_RISK -> milestone.markAtRisk();
            case PENDING -> throw new BusinessRuleViolationException(
                    "Cannot set milestone status back to PENDING; no event exists for that transition");
        }
    }

    private void recordEvents(RequestContext context, Milestone milestone) {
        for (MilestoneDomainEvent event : milestone.pullDomainEvents()) {
            workEventRecorder.record(context, milestone, event);
        }
    }

    /**
     * Finds a milestone by id. Unlike {@code ProjectApplicationService}'s equivalent, this does
     * not also check the caller's organization: {@code PATCH}/{@code DELETE /milestones/:id}
     * carry no project id, so the owning project is not known until after this lookup.
     */
    private Milestone findActiveMilestone(UUID milestoneId) {
        return milestoneRepository.findByIdAndArchivedAtIsNull(milestoneId)
                .orElseThrow(() -> new ResourceNotFoundException("Milestone", milestoneId));
    }
}
