package com.projectmodule.work.application;

import com.projectmodule.common.context.RequestContext;
import com.projectmodule.common.exception.BusinessRuleViolationException;
import com.projectmodule.common.exception.ConflictException;
import com.projectmodule.common.exception.ResourceNotFoundException;
import com.projectmodule.project.application.authorization.ProjectAuthorizationService;
import com.projectmodule.project.application.authorization.ProjectPermission;
import com.projectmodule.work.api.dto.CreateDependencyRequest;
import com.projectmodule.work.api.dto.UpdateDependencyRequest;
import com.projectmodule.work.domain.Dependency;
import com.projectmodule.work.domain.Task;
import com.projectmodule.work.domain.event.DependencyDomainEvent;
import com.projectmodule.work.infrastructure.DependencyRepository;
import com.projectmodule.work.infrastructure.TaskRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Use cases for dependency CRUD.
 *
 * <p>The owning project is resolved through the <em>dependent</em> task, via
 * {@link TaskApplicationService#findActiveTaskInCallerOrganization} — the same tenant boundary,
 * existence check and reuse pattern {@code SubtaskApplicationService}/
 * {@code ChecklistApplicationService} already establish. Every mutation is gated by the
 * existing {@link ProjectAuthorizationService} — no second authorization system.
 *
 * <p>Validation beyond self-dependency (which {@link Dependency#create} already rejects, since
 * it needs no repository access) lives here, because it needs to look up both tasks and
 * existing dependencies:
 * <ul>
 *   <li>cross-project — the prerequisite task must belong to the same project as the dependent
 *       task;
 *   <li>exact duplicate — the same ordered pair must not already exist;
 *   <li>direct reverse — the swapped pair must not already exist either, which would create a
 *       two-task cycle.
 * </ul>
 * Deeper transitive cycle detection across the whole dependency graph is not implemented:
 * {@code docs/project/08-ROADMAP.md} lists "Dependency analysis" under P3 Intelligence, a later
 * phase separate from this P1 entity.
 */
@Service
public class DependencyApplicationService {

    private final DependencyRepository dependencyRepository;
    private final TaskRepository taskRepository;
    private final TaskApplicationService taskApplicationService;
    private final ProjectAuthorizationService projectAuthorizationService;
    private final WorkEventRecorder workEventRecorder;

    public DependencyApplicationService(DependencyRepository dependencyRepository,
                                        TaskRepository taskRepository,
                                        TaskApplicationService taskApplicationService,
                                        ProjectAuthorizationService projectAuthorizationService,
                                        WorkEventRecorder workEventRecorder) {
        this.dependencyRepository = dependencyRepository;
        this.taskRepository = taskRepository;
        this.taskApplicationService = taskApplicationService;
        this.projectAuthorizationService = projectAuthorizationService;
        this.workEventRecorder = workEventRecorder;
    }

    @Transactional
    public Dependency createDependency(RequestContext context, UUID dependentTaskId, CreateDependencyRequest request) {
        Task dependentTask = taskApplicationService.findActiveTaskInCallerOrganization(context, dependentTaskId);
        projectAuthorizationService.requirePermission(
                context.requireUserId(), dependentTask.getProjectId(), ProjectPermission.EDIT_PROJECT);

        UUID prerequisiteTaskId = request.prerequisiteTaskId();
        Task prerequisiteTask = taskRepository.findByIdAndArchivedAtIsNull(prerequisiteTaskId)
                .orElseThrow(() -> new ResourceNotFoundException("Task", prerequisiteTaskId));

        if (!prerequisiteTask.getProjectId().equals(dependentTask.getProjectId())) {
            throw new BusinessRuleViolationException(
                    "A task can only depend on a task within the same project");
        }
        if (dependencyRepository.existsByDependentTaskIdAndPrerequisiteTaskIdAndArchivedAtIsNull(
                dependentTaskId, prerequisiteTaskId)) {
            throw new ConflictException("This dependency already exists");
        }
        if (dependencyRepository.existsByDependentTaskIdAndPrerequisiteTaskIdAndArchivedAtIsNull(
                prerequisiteTaskId, dependentTaskId)) {
            throw new ConflictException(
                    "The reverse dependency already exists; this would create a cycle");
        }

        Dependency dependency = Dependency.create(dependentTaskId, prerequisiteTaskId);
        dependency = dependencyRepository.save(dependency);
        recordEvents(context, dependency, dependentTask.getProjectId());

        return dependency;
    }

    @Transactional(readOnly = true)
    public List<Dependency> listDependencies(RequestContext context, UUID dependentTaskId) {
        Task task = taskApplicationService.findActiveTaskInCallerOrganization(context, dependentTaskId);
        projectAuthorizationService.requirePermission(
                context.requireUserId(), task.getProjectId(), ProjectPermission.VIEW_PROJECT);
        return dependencyRepository.findByDependentTaskIdAndArchivedAtIsNullOrderByCreatedAtAsc(dependentTaskId);
    }

    @Transactional
    public Dependency updateDependency(RequestContext context, UUID dependencyId, UpdateDependencyRequest request) {
        Dependency dependency = findActiveDependency(dependencyId);
        Task dependentTask = taskApplicationService.findActiveTaskInCallerOrganization(
                context, dependency.getDependentTaskId());
        projectAuthorizationService.requirePermission(
                context.requireUserId(), dependentTask.getProjectId(), ProjectPermission.EDIT_PROJECT);

        if (dependency.getVersion() != request.version()) {
            throw new ConflictException(
                    "Dependency " + dependencyId + " has changed since version " + request.version()
                            + "; reload and try again");
        }

        if (request.broken() != null) {
            if (request.broken()) {
                dependency.markBroken();
            } else {
                throw new BusinessRuleViolationException(
                        "Cannot set broken back to false; no event exists for that transition");
            }
        }

        dependency = dependencyRepository.save(dependency);
        recordEvents(context, dependency, dependentTask.getProjectId());

        return dependency;
    }

    @Transactional
    public void archiveDependency(RequestContext context, UUID dependencyId) {
        Dependency dependency = findActiveDependency(dependencyId);
        Task dependentTask = taskApplicationService.findActiveTaskInCallerOrganization(
                context, dependency.getDependentTaskId());
        projectAuthorizationService.requirePermission(
                context.requireUserId(), dependentTask.getProjectId(), ProjectPermission.EDIT_PROJECT);

        dependency.archive(context.requireUserId());
        dependencyRepository.save(dependency);
        // No event: dependency.archived is not in the current event catalogue.
    }

    private void recordEvents(RequestContext context, Dependency dependency, UUID projectId) {
        for (DependencyDomainEvent event : dependency.pullDomainEvents()) {
            workEventRecorder.record(context, dependency, projectId, event);
        }
    }

    private Dependency findActiveDependency(UUID dependencyId) {
        return dependencyRepository.findByIdAndArchivedAtIsNull(dependencyId)
                .orElseThrow(() -> new ResourceNotFoundException("Dependency", dependencyId));
    }
}
