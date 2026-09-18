package com.projectmodule.work.application;

import com.projectmodule.common.context.RequestContext;
import com.projectmodule.common.exception.ConflictException;
import com.projectmodule.common.exception.ResourceNotFoundException;
import com.projectmodule.project.application.ProjectApplicationService;
import com.projectmodule.project.application.authorization.ProjectAuthorizationService;
import com.projectmodule.project.application.authorization.ProjectPermission;
import com.projectmodule.work.api.dto.CreatePhaseRequest;
import com.projectmodule.work.api.dto.UpdatePhaseRequest;
import com.projectmodule.work.domain.Phase;
import com.projectmodule.work.domain.event.PhaseDomainEvent;
import com.projectmodule.work.infrastructure.PhaseRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Use cases for phase CRUD.
 *
 * <p>Every operation resolves the owning project through
 * {@link ProjectApplicationService#findActiveProjectInCallerOrganization}, the same tenant
 * boundary and existence check Project CRUD and {@code ProjectMemberApplicationService} already
 * use, then applies the existing {@link ProjectAuthorizationService} — there is no separate
 * permission system for work items. {@code EDIT_PROJECT} gates every mutation here, including
 * archive: unlike a whole project, archiving one phase is project content management, not the
 * severity {@code ARCHIVE_PROJECT} (owner-only) represents.
 *
 * <p>Domain events are collected and persisted the same way {@code ProjectApplicationService}
 * does: after the triggering domain operation and its repository save both succeed, inside the
 * same transaction, through {@link WorkEventRecorder}.
 */
@Service
public class PhaseApplicationService {

    private final PhaseRepository phaseRepository;
    private final ProjectApplicationService projectApplicationService;
    private final ProjectAuthorizationService projectAuthorizationService;
    private final WorkEventRecorder workEventRecorder;

    public PhaseApplicationService(PhaseRepository phaseRepository,
                                   ProjectApplicationService projectApplicationService,
                                   ProjectAuthorizationService projectAuthorizationService,
                                   WorkEventRecorder workEventRecorder) {
        this.phaseRepository = phaseRepository;
        this.projectApplicationService = projectApplicationService;
        this.projectAuthorizationService = projectAuthorizationService;
        this.workEventRecorder = workEventRecorder;
    }

    @Transactional
    public Phase createPhase(RequestContext context, UUID projectId, CreatePhaseRequest request) {
        projectApplicationService.findActiveProjectInCallerOrganization(context, projectId);
        projectAuthorizationService.requirePermission(
                context.requireUserId(), projectId, ProjectPermission.EDIT_PROJECT);

        Phase phase = Phase.create(projectId, request.name());
        phase.describe(request.description());
        if (request.startDate() != null || request.endDate() != null) {
            phase.schedule(request.startDate(), request.endDate());
        }

        phase = phaseRepository.save(phase);
        recordEvents(context, phase);

        return phase;
    }

    @Transactional(readOnly = true)
    public List<Phase> listPhases(RequestContext context, UUID projectId) {
        projectApplicationService.findActiveProjectInCallerOrganization(context, projectId);
        projectAuthorizationService.requirePermission(
                context.requireUserId(), projectId, ProjectPermission.VIEW_PROJECT);
        return phaseRepository.findByProjectIdAndArchivedAtIsNullOrderByCreatedAtAsc(projectId);
    }

    @Transactional
    public Phase updatePhase(RequestContext context, UUID phaseId, UpdatePhaseRequest request) {
        Phase phase = findActivePhase(phaseId);
        projectApplicationService.findActiveProjectInCallerOrganization(context, phase.getProjectId());
        projectAuthorizationService.requirePermission(
                context.requireUserId(), phase.getProjectId(), ProjectPermission.EDIT_PROJECT);

        if (phase.getVersion() != request.version()) {
            throw new ConflictException(
                    "Phase " + phaseId + " has changed since version " + request.version()
                            + "; reload and try again");
        }

        applyUpdates(phase, request);
        phase.recordUpdated();

        phase = phaseRepository.save(phase);
        recordEvents(context, phase);

        return phase;
    }

    @Transactional
    public void archivePhase(RequestContext context, UUID phaseId) {
        Phase phase = findActivePhase(phaseId);
        projectApplicationService.findActiveProjectInCallerOrganization(context, phase.getProjectId());
        projectAuthorizationService.requirePermission(
                context.requireUserId(), phase.getProjectId(), ProjectPermission.EDIT_PROJECT);

        phase.archive(context.requireUserId());
        phaseRepository.save(phase);
        // No event: phase.archived is not in the current event catalogue.
    }

    private void applyUpdates(Phase phase, UpdatePhaseRequest request) {
        if (request.name() != null) {
            phase.rename(request.name());
        }
        if (request.description() != null) {
            phase.describe(request.description());
        }
        if (request.startDate() != null || request.endDate() != null) {
            LocalDate newStart = request.startDate() != null
                    ? request.startDate() : phase.getStartDate().orElse(null);
            LocalDate newEnd = request.endDate() != null
                    ? request.endDate() : phase.getEndDate().orElse(null);
            phase.schedule(newStart, newEnd);
        }
    }

    private void recordEvents(RequestContext context, Phase phase) {
        for (PhaseDomainEvent event : phase.pullDomainEvents()) {
            workEventRecorder.record(context, phase, event);
        }
    }

    /**
     * Finds a phase by id. Unlike {@code ProjectApplicationService}'s equivalent, this does not
     * also check the caller's organization: {@code PATCH}/{@code DELETE /phases/:id} carry no
     * project id, so the owning project is not known until after this lookup, and the
     * organization/authorization check follows immediately at every call site.
     */
    private Phase findActivePhase(UUID phaseId) {
        return phaseRepository.findByIdAndArchivedAtIsNull(phaseId)
                .orElseThrow(() -> new ResourceNotFoundException("Phase", phaseId));
    }
}
