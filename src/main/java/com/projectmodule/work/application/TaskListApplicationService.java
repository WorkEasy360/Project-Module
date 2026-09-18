package com.projectmodule.work.application;

import com.projectmodule.common.context.RequestContext;
import com.projectmodule.common.exception.ConflictException;
import com.projectmodule.common.exception.ResourceNotFoundException;
import com.projectmodule.project.application.ProjectApplicationService;
import com.projectmodule.project.application.authorization.ProjectAuthorizationService;
import com.projectmodule.project.application.authorization.ProjectPermission;
import com.projectmodule.work.api.dto.CreateTaskListRequest;
import com.projectmodule.work.api.dto.UpdateTaskListRequest;
import com.projectmodule.work.domain.TaskList;
import com.projectmodule.work.infrastructure.TaskListRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Use cases for task list CRUD.
 *
 * <p>Same tenant/authorization pattern as {@link PhaseApplicationService}. Raises no domain
 * event: {@code docs/project/05-EVENTS.md} defines no Task List events, so there is nothing to
 * record — a deliberate absence, not an oversight.
 */
@Service
public class TaskListApplicationService {

    private final TaskListRepository taskListRepository;
    private final ProjectApplicationService projectApplicationService;
    private final ProjectAuthorizationService projectAuthorizationService;

    public TaskListApplicationService(TaskListRepository taskListRepository,
                                      ProjectApplicationService projectApplicationService,
                                      ProjectAuthorizationService projectAuthorizationService) {
        this.taskListRepository = taskListRepository;
        this.projectApplicationService = projectApplicationService;
        this.projectAuthorizationService = projectAuthorizationService;
    }

    @Transactional
    public TaskList createTaskList(RequestContext context, UUID projectId, CreateTaskListRequest request) {
        projectApplicationService.findActiveProjectInCallerOrganization(context, projectId);
        projectAuthorizationService.requirePermission(
                context.requireUserId(), projectId, ProjectPermission.EDIT_PROJECT);

        TaskList taskList = TaskList.create(projectId, request.phaseId(), request.name());
        taskList.describe(request.description());

        return taskListRepository.save(taskList);
    }

    @Transactional(readOnly = true)
    public List<TaskList> listTaskLists(RequestContext context, UUID projectId) {
        projectApplicationService.findActiveProjectInCallerOrganization(context, projectId);
        projectAuthorizationService.requirePermission(
                context.requireUserId(), projectId, ProjectPermission.VIEW_PROJECT);
        return taskListRepository.findByProjectIdAndArchivedAtIsNullOrderByCreatedAtAsc(projectId);
    }

    @Transactional
    public TaskList updateTaskList(RequestContext context, UUID taskListId, UpdateTaskListRequest request) {
        TaskList taskList = findActiveTaskList(taskListId);
        projectApplicationService.findActiveProjectInCallerOrganization(context, taskList.getProjectId());
        projectAuthorizationService.requirePermission(
                context.requireUserId(), taskList.getProjectId(), ProjectPermission.EDIT_PROJECT);

        if (taskList.getVersion() != request.version()) {
            throw new ConflictException(
                    "Task list " + taskListId + " has changed since version " + request.version()
                            + "; reload and try again");
        }

        if (request.name() != null) {
            taskList.rename(request.name());
        }
        if (request.description() != null) {
            taskList.describe(request.description());
        }
        if (request.phaseId() != null) {
            taskList.reassignPhase(request.phaseId());
        }

        return taskListRepository.save(taskList);
    }

    @Transactional
    public void archiveTaskList(RequestContext context, UUID taskListId) {
        TaskList taskList = findActiveTaskList(taskListId);
        projectApplicationService.findActiveProjectInCallerOrganization(context, taskList.getProjectId());
        projectAuthorizationService.requirePermission(
                context.requireUserId(), taskList.getProjectId(), ProjectPermission.EDIT_PROJECT);

        taskList.archive(context.requireUserId());
        taskListRepository.save(taskList);
    }

    private TaskList findActiveTaskList(UUID taskListId) {
        return taskListRepository.findByIdAndArchivedAtIsNull(taskListId)
                .orElseThrow(() -> new ResourceNotFoundException("TaskList", taskListId));
    }
}
