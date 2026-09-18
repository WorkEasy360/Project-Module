package com.projectmodule.work.application;

import com.projectmodule.common.context.RequestContext;
import com.projectmodule.common.exception.ConflictException;
import com.projectmodule.common.exception.ResourceNotFoundException;
import com.projectmodule.project.application.authorization.ProjectAuthorizationService;
import com.projectmodule.project.application.authorization.ProjectPermission;
import com.projectmodule.work.api.dto.CreateChecklistRequest;
import com.projectmodule.work.api.dto.UpdateChecklistRequest;
import com.projectmodule.work.domain.Checklist;
import com.projectmodule.work.domain.Task;
import com.projectmodule.work.infrastructure.ChecklistRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Use cases for checklist-line CRUD.
 *
 * <p>The owning task is resolved through
 * {@link TaskApplicationService#findActiveTaskInCallerOrganization}, the same tenant boundary
 * and existence check every other work-item service uses. Every mutation is gated by the
 * existing {@link ProjectAuthorizationService} — no second authorization system.
 *
 * <p>Raises no domain event: {@code docs/project/05-EVENTS.md} defines no Checklist events, the
 * same deliberate absence as {@code TaskList} and {@code Subtask}.
 */
@Service
public class ChecklistApplicationService {

    private final ChecklistRepository checklistRepository;
    private final TaskApplicationService taskApplicationService;
    private final ProjectAuthorizationService projectAuthorizationService;

    public ChecklistApplicationService(ChecklistRepository checklistRepository,
                                       TaskApplicationService taskApplicationService,
                                       ProjectAuthorizationService projectAuthorizationService) {
        this.checklistRepository = checklistRepository;
        this.taskApplicationService = taskApplicationService;
        this.projectAuthorizationService = projectAuthorizationService;
    }

    @Transactional
    public Checklist createChecklist(RequestContext context, UUID taskId, CreateChecklistRequest request) {
        Task task = taskApplicationService.findActiveTaskInCallerOrganization(context, taskId);
        projectAuthorizationService.requirePermission(
                context.requireUserId(), task.getProjectId(), ProjectPermission.EDIT_PROJECT);

        Checklist checklist = Checklist.create(taskId, request.subtaskId(), request.text());

        return checklistRepository.save(checklist);
    }

    @Transactional(readOnly = true)
    public List<Checklist> listChecklists(RequestContext context, UUID taskId) {
        Task task = taskApplicationService.findActiveTaskInCallerOrganization(context, taskId);
        projectAuthorizationService.requirePermission(
                context.requireUserId(), task.getProjectId(), ProjectPermission.VIEW_PROJECT);
        return checklistRepository.findByTaskIdAndArchivedAtIsNullOrderByCreatedAtAsc(taskId);
    }

    @Transactional
    public Checklist updateChecklist(RequestContext context, UUID checklistId, UpdateChecklistRequest request) {
        Checklist checklist = findActiveChecklist(checklistId);
        Task task = taskApplicationService.findActiveTaskInCallerOrganization(context, checklist.getTaskId());
        projectAuthorizationService.requirePermission(
                context.requireUserId(), task.getProjectId(), ProjectPermission.EDIT_PROJECT);

        if (checklist.getVersion() != request.version()) {
            throw new ConflictException(
                    "Checklist item " + checklistId + " has changed since version " + request.version()
                            + "; reload and try again");
        }

        if (request.text() != null) {
            checklist.relabel(request.text());
        }
        if (request.checked() != null) {
            if (request.checked()) {
                checklist.check();
            } else {
                checklist.uncheck();
            }
        }
        if (request.subtaskId() != null) {
            checklist.reassignSubtask(request.subtaskId());
        }

        return checklistRepository.save(checklist);
    }

    @Transactional
    public void archiveChecklist(RequestContext context, UUID checklistId) {
        Checklist checklist = findActiveChecklist(checklistId);
        Task task = taskApplicationService.findActiveTaskInCallerOrganization(context, checklist.getTaskId());
        projectAuthorizationService.requirePermission(
                context.requireUserId(), task.getProjectId(), ProjectPermission.EDIT_PROJECT);

        checklist.archive(context.requireUserId());
        checklistRepository.save(checklist);
    }

    private Checklist findActiveChecklist(UUID checklistId) {
        return checklistRepository.findByIdAndArchivedAtIsNull(checklistId)
                .orElseThrow(() -> new ResourceNotFoundException("Checklist", checklistId));
    }
}
