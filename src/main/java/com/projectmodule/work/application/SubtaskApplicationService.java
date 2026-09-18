package com.projectmodule.work.application;

import com.projectmodule.common.context.RequestContext;
import com.projectmodule.common.exception.ConflictException;
import com.projectmodule.common.exception.ResourceNotFoundException;
import com.projectmodule.project.application.authorization.ProjectAuthorizationService;
import com.projectmodule.project.application.authorization.ProjectPermission;
import com.projectmodule.work.api.dto.CreateSubtaskRequest;
import com.projectmodule.work.api.dto.UpdateSubtaskRequest;
import com.projectmodule.work.domain.Subtask;
import com.projectmodule.work.domain.Task;
import com.projectmodule.work.infrastructure.SubtaskRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Use cases for subtask CRUD.
 *
 * <p>The owning task is resolved through
 * {@link TaskApplicationService#findActiveTaskInCallerOrganization}, which also verifies the
 * task's project is in the caller's organization — the same tenant boundary and existence check
 * every other work-item service uses. Every mutation is gated by the existing
 * {@link ProjectAuthorizationService} — no second authorization system.
 *
 * <p>Raises no domain event: {@code docs/project/05-EVENTS.md} defines no Subtask events, the
 * same deliberate absence as {@code TaskList}.
 */
@Service
public class SubtaskApplicationService {

    private final SubtaskRepository subtaskRepository;
    private final TaskApplicationService taskApplicationService;
    private final ProjectAuthorizationService projectAuthorizationService;

    public SubtaskApplicationService(SubtaskRepository subtaskRepository,
                                     TaskApplicationService taskApplicationService,
                                     ProjectAuthorizationService projectAuthorizationService) {
        this.subtaskRepository = subtaskRepository;
        this.taskApplicationService = taskApplicationService;
        this.projectAuthorizationService = projectAuthorizationService;
    }

    @Transactional
    public Subtask createSubtask(RequestContext context, UUID taskId, CreateSubtaskRequest request) {
        Task task = taskApplicationService.findActiveTaskInCallerOrganization(context, taskId);
        projectAuthorizationService.requirePermission(
                context.requireUserId(), task.getProjectId(), ProjectPermission.EDIT_PROJECT);

        Subtask subtask = Subtask.create(taskId, request.name());
        subtask.describe(request.description());

        return subtaskRepository.save(subtask);
    }

    @Transactional(readOnly = true)
    public List<Subtask> listSubtasks(RequestContext context, UUID taskId) {
        Task task = taskApplicationService.findActiveTaskInCallerOrganization(context, taskId);
        projectAuthorizationService.requirePermission(
                context.requireUserId(), task.getProjectId(), ProjectPermission.VIEW_PROJECT);
        return subtaskRepository.findByTaskIdAndArchivedAtIsNullOrderByCreatedAtAsc(taskId);
    }

    @Transactional
    public Subtask updateSubtask(RequestContext context, UUID subtaskId, UpdateSubtaskRequest request) {
        Subtask subtask = findActiveSubtask(subtaskId);
        Task task = taskApplicationService.findActiveTaskInCallerOrganization(context, subtask.getTaskId());
        projectAuthorizationService.requirePermission(
                context.requireUserId(), task.getProjectId(), ProjectPermission.EDIT_PROJECT);

        if (subtask.getVersion() != request.version()) {
            throw new ConflictException(
                    "Subtask " + subtaskId + " has changed since version " + request.version()
                            + "; reload and try again");
        }

        if (request.name() != null) {
            subtask.rename(request.name());
        }
        if (request.description() != null) {
            subtask.describe(request.description());
        }
        if (request.completed() != null) {
            if (request.completed()) {
                subtask.complete();
            } else {
                subtask.reopen();
            }
        }

        return subtaskRepository.save(subtask);
    }

    @Transactional
    public void archiveSubtask(RequestContext context, UUID subtaskId) {
        Subtask subtask = findActiveSubtask(subtaskId);
        Task task = taskApplicationService.findActiveTaskInCallerOrganization(context, subtask.getTaskId());
        projectAuthorizationService.requirePermission(
                context.requireUserId(), task.getProjectId(), ProjectPermission.EDIT_PROJECT);

        subtask.archive(context.requireUserId());
        subtaskRepository.save(subtask);
    }

    private Subtask findActiveSubtask(UUID subtaskId) {
        return subtaskRepository.findByIdAndArchivedAtIsNull(subtaskId)
                .orElseThrow(() -> new ResourceNotFoundException("Subtask", subtaskId));
    }
}
