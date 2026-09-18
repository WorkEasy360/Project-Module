package com.projectmodule.project.application;

import com.projectmodule.common.context.RequestContext;
import com.projectmodule.common.exception.ConflictException;
import com.projectmodule.common.exception.MissingIdentityException;
import com.projectmodule.common.exception.ResourceNotFoundException;
import com.projectmodule.common.identity.ExternalUserId;
import com.projectmodule.common.identity.OrganizationId;
import com.projectmodule.project.api.dto.CreateProjectRequest;
import com.projectmodule.project.api.dto.UpdateProjectRequest;
import com.projectmodule.project.application.authorization.ProjectAuthorizationService;
import com.projectmodule.project.application.authorization.ProjectPermission;
import com.projectmodule.project.domain.Project;
import com.projectmodule.project.domain.ProjectMember;
import com.projectmodule.project.domain.ProjectRole;
import com.projectmodule.project.domain.event.ProjectDomainEvent;
import com.projectmodule.project.infrastructure.ProjectMemberRepository;
import com.projectmodule.project.infrastructure.ProjectRepository;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Use cases for project CRUD.
 *
 * <p>This is the application layer of the module architecture: it orchestrates the domain and
 * the repository and owns the transaction boundary, but holds no business rule itself. Every
 * rule enforced here is enforced by calling a {@link Project} behaviour method, never by
 * setting a field directly, so a future caller that is not a controller — an AI tool, an
 * automation run — is bound by exactly the same invariants.
 *
 * <p>Every operation is scoped to the caller's organization, taken from the
 * {@link RequestContext}. This is tenant-boundary enforcement using the {@code organizationId}
 * column, distinct from project-level role authorization: a project outside the caller's
 * organization is reported not found (its existence is not revealed), while a project inside
 * the caller's organization that the caller lacks permission for is reported forbidden through
 * {@link ProjectAuthorizationService}.
 *
 * <p>Creation is not gated through {@link ProjectAuthorizationService}: a project being created
 * has no {@code projectId} yet for that interface to check against. The creator becomes the
 * project's {@code OWNER}, recorded as both {@code Project.ownerId} and a {@link ProjectMember}
 * row created in the same transaction — without that row the creator would hold no permission
 * on the project they just created.
 *
 * <p>Every operation that changes state records its domain events after the change succeeds:
 * see {@link #recordEvents}. Because that call sits inside the same {@code @Transactional}
 * method as the state change, the project row, its {@code ProjectActivity} audit entry and its
 * {@code OutboxMessage} commit together or not at all — a validation or authorization failure
 * earlier in the method never reaches this call, so no event is ever recorded for a change that
 * did not happen.
 */
@Service
public class ProjectApplicationService {

    private final ProjectRepository projectRepository;
    private final ProjectMemberRepository projectMemberRepository;
    private final ProjectAuthorizationService projectAuthorizationService;
    private final ProjectEventRecorder projectEventRecorder;

    public ProjectApplicationService(ProjectRepository projectRepository,
                                     ProjectMemberRepository projectMemberRepository,
                                     ProjectAuthorizationService projectAuthorizationService,
                                     ProjectEventRecorder projectEventRecorder) {
        this.projectRepository = projectRepository;
        this.projectMemberRepository = projectMemberRepository;
        this.projectAuthorizationService = projectAuthorizationService;
        this.projectEventRecorder = projectEventRecorder;
    }

    @Transactional
    public Project createProject(RequestContext context, CreateProjectRequest request) {
        OrganizationId organizationId = requireOrganizationId(context);
        ExternalUserId ownerId = context.requireUserId();

        Project project = Project.create(organizationId, request.name(), ownerId, request.priority());
        project.describe(request.description());
        if (request.startDate() != null || request.targetEndDate() != null) {
            project.schedule(request.startDate(), request.targetEndDate());
        }

        project = projectRepository.save(project);
        projectMemberRepository.save(ProjectMember.join(project.getId(), ownerId, ProjectRole.OWNER));
        recordEvents(context, project);

        return project;
    }

    @Transactional(readOnly = true)
    public Project getProject(RequestContext context, UUID id) {
        Project project = findActiveProjectInCallerOrganization(context, id);
        projectAuthorizationService.requirePermission(
                context.requireUserId(), id, ProjectPermission.VIEW_PROJECT);
        return project;
    }

    @Transactional(readOnly = true)
    public Page<Project> listProjects(RequestContext context, Pageable pageable) {
        OrganizationId organizationId = requireOrganizationId(context);
        return projectRepository.findByOrganizationIdAndArchivedAtIsNull(
                organizationId.value(), pageable);
    }

    @Transactional
    public Project updateProject(RequestContext context, UUID id, UpdateProjectRequest request) {
        Project project = findActiveProjectInCallerOrganization(context, id);
        projectAuthorizationService.requirePermission(
                context.requireUserId(), id, ProjectPermission.EDIT_PROJECT);

        if (project.getVersion() != request.version()) {
            throw new ConflictException(
                    "Project " + id + " has changed since version " + request.version()
                            + "; reload and try again");
        }

        applyUpdates(project, request);
        project.recordUpdated();

        project = projectRepository.save(project);
        recordEvents(context, project);

        return project;
    }

    @Transactional
    public void archiveProject(RequestContext context, UUID id) {
        Project project = findActiveProjectInCallerOrganization(context, id);
        projectAuthorizationService.requirePermission(
                context.requireUserId(), id, ProjectPermission.ARCHIVE_PROJECT);
        project.archive(context.requireUserId());
        project = projectRepository.save(project);
        recordEvents(context, project);
    }

    /**
     * Collects the events {@code project} has raised since the last call and persists the
     * audit entry and outbox record for each, through {@link ProjectEventRecorder}.
     *
     * <p>Called only after the triggering domain operation and its {@code repository.save}
     * have both succeeded, and always before the enclosing {@code @Transactional} method
     * returns — so these records are never written for a change that failed partway through,
     * and always commit atomically with the change they describe.
     */
    private void recordEvents(RequestContext context, Project project) {
        for (ProjectDomainEvent event : project.pullDomainEvents()) {
            projectEventRecorder.record(context, project, event);
        }
    }

    private void applyUpdates(Project project, UpdateProjectRequest request) {
        if (request.name() != null) {
            project.rename(request.name());
        }
        if (request.description() != null) {
            project.describe(request.description());
        }
        if (request.status() != null) {
            project.changeStatus(request.status());
        }
        if (request.priority() != null) {
            project.changePriority(request.priority());
        }
        if (request.startDate() != null || request.targetEndDate() != null) {
            LocalDate newStart = request.startDate() != null
                    ? request.startDate() : project.getStartDate().orElse(null);
            LocalDate newTargetEnd = request.targetEndDate() != null
                    ? request.targetEndDate() : project.getTargetEndDate().orElse(null);
            project.schedule(newStart, newTargetEnd);
        }
    }

    /**
     * Finds a project by id, scoped to the caller's organization.
     *
     * <p>An archived project is reported not found, the same as one that never existed: the
     * standard CRUD surface treats a soft-archived project as absent, consistent with
     * {@code DELETE} meaning archive. A project belonging to a different organization is also
     * reported not found rather than forbidden, so its existence is not revealed across the
     * tenant boundary.
     *
     * <p>Public so {@link ProjectMemberApplicationService} and the {@code work} package's
     * application services (Phase, Milestone, Task List) can apply the same existence and
     * tenant-boundary check before their own authorization check, without duplicating this
     * logic.
     */
    public Project findActiveProjectInCallerOrganization(RequestContext context, UUID id) {
        OrganizationId organizationId = requireOrganizationId(context);

        Project project = projectRepository.findByIdAndArchivedAtIsNull(id)
                .orElseThrow(() -> new ResourceNotFoundException("Project", id));

        if (!project.getOrganizationId().equals(organizationId)) {
            throw new ResourceNotFoundException("Project", id);
        }

        return project;
    }

    private static OrganizationId requireOrganizationId(RequestContext context) {
        return context.organizationId().orElseThrow(() -> new MissingIdentityException(
                "No organization was supplied with this request"));
    }
}
