package com.projectmodule.work.application;

import com.projectmodule.common.api.SortDirection;
import com.projectmodule.common.context.RequestContext;
import com.projectmodule.common.exception.ConflictException;
import com.projectmodule.common.exception.ResourceNotFoundException;
import com.projectmodule.project.application.ProjectApplicationService;
import com.projectmodule.project.application.authorization.ProjectAuthorizationService;
import com.projectmodule.project.application.authorization.ProjectPermission;
import com.projectmodule.project.domain.ProjectPriority;
import com.projectmodule.work.api.dto.CreateIssueRequest;
import com.projectmodule.work.api.dto.UpdateIssueRequest;
import com.projectmodule.work.domain.Issue;
import com.projectmodule.work.domain.IssueSortField;
import com.projectmodule.work.infrastructure.IssueRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Use cases for issue CRUD.
 *
 * <p>Same tenant/authorization pattern as {@link PhaseApplicationService}/
 * {@link RiskApplicationService}: the owning project is resolved through
 * {@code ProjectApplicationService.findActiveProjectInCallerOrganization}, and every mutation is
 * gated by the existing {@link ProjectAuthorizationService} — no second authorization system.
 *
 * <p>Unlike every other work-management service, this one has no {@link WorkEventRecorder}
 * dependency: {@code docs/project/05-EVENTS.md} documents no Issue events at all (no
 * {@code ## Issue} section), so there is nothing to record. Issue mutations do not appear in the
 * outbox or the project activity audit trail.
 */
@Service
public class IssueApplicationService {

    private final IssueRepository issueRepository;
    private final ProjectApplicationService projectApplicationService;
    private final ProjectAuthorizationService projectAuthorizationService;

    public IssueApplicationService(IssueRepository issueRepository,
                                   ProjectApplicationService projectApplicationService,
                                   ProjectAuthorizationService projectAuthorizationService) {
        this.issueRepository = issueRepository;
        this.projectApplicationService = projectApplicationService;
        this.projectAuthorizationService = projectAuthorizationService;
    }

    @Transactional
    public Issue createIssue(RequestContext context, UUID projectId, CreateIssueRequest request) {
        projectApplicationService.findActiveProjectInCallerOrganization(context, projectId);
        projectAuthorizationService.requirePermission(
                context.requireUserId(), projectId, ProjectPermission.EDIT_PROJECT);

        Issue issue = Issue.create(projectId, request.name(), request.priority());
        issue.describe(request.description());

        return issueRepository.save(issue);
    }

    @Transactional(readOnly = true)
    public List<Issue> listIssues(RequestContext context, UUID projectId) {
        projectApplicationService.findActiveProjectInCallerOrganization(context, projectId);
        projectAuthorizationService.requirePermission(
                context.requireUserId(), projectId, ProjectPermission.VIEW_PROJECT);
        return issueRepository.findByProjectIdAndArchivedAtIsNullOrderByCreatedAtAsc(projectId);
    }

    /**
     * Lists issues in the project matching every supplied filter (AND), per
     * {@code docs/project/14-FILTERS-SPEC.md}, in the order requested per
     * {@code docs/project/15-LIST-SPEC.md}. See {@code TaskApplicationService.listTasks}'s
     * filtered overload for the {@code archived} semantics and the sort-default behavior.
     */
    @Transactional(readOnly = true)
    public List<Issue> listIssues(RequestContext context, UUID projectId, ProjectPriority priority, boolean archived,
                                  IssueSortField sortBy, SortDirection sortDir) {
        projectApplicationService.findActiveProjectInCallerOrganization(context, projectId);
        projectAuthorizationService.requirePermission(
                context.requireUserId(), projectId, ProjectPermission.VIEW_PROJECT);
        return issueRepository.findByFilters(projectId, priority, archived, toSort(sortBy, sortDir));
    }

    private static Sort toSort(IssueSortField sortBy, SortDirection sortDir) {
        Sort.Direction direction = sortDir == SortDirection.DESC ? Sort.Direction.DESC : Sort.Direction.ASC;
        String property = switch (sortBy == null ? IssueSortField.CREATED_AT : sortBy) {
            case CREATED_AT -> "createdAt";
            case NAME -> "name";
            case PRIORITY -> "priority";
        };
        return Sort.by(direction, property);
    }

    @Transactional
    public Issue updateIssue(RequestContext context, UUID issueId, UpdateIssueRequest request) {
        Issue issue = findActiveIssue(issueId);
        projectApplicationService.findActiveProjectInCallerOrganization(context, issue.getProjectId());
        projectAuthorizationService.requirePermission(
                context.requireUserId(), issue.getProjectId(), ProjectPermission.EDIT_PROJECT);

        if (issue.getVersion() != request.version()) {
            throw new ConflictException(
                    "Issue " + issueId + " has changed since version " + request.version()
                            + "; reload and try again");
        }

        applyUpdates(issue, request);

        return issueRepository.save(issue);
    }

    @Transactional
    public void archiveIssue(RequestContext context, UUID issueId) {
        Issue issue = findActiveIssue(issueId);
        projectApplicationService.findActiveProjectInCallerOrganization(context, issue.getProjectId());
        projectAuthorizationService.requirePermission(
                context.requireUserId(), issue.getProjectId(), ProjectPermission.EDIT_PROJECT);

        issue.archive(context.requireUserId());
        issueRepository.save(issue);
    }

    private void applyUpdates(Issue issue, UpdateIssueRequest request) {
        if (request.name() != null) {
            issue.rename(request.name());
        }
        if (request.description() != null) {
            issue.describe(request.description());
        }
        if (request.priority() != null) {
            issue.reprioritize(request.priority());
        }
    }

    /**
     * Finds an issue by id. Unlike {@code ProjectApplicationService}'s equivalent, this does not
     * also check the caller's organization: {@code PATCH}/{@code DELETE /issues/:id} carry no
     * project id, so the owning project is not known until after this lookup.
     */
    private Issue findActiveIssue(UUID issueId) {
        return issueRepository.findByIdAndArchivedAtIsNull(issueId)
                .orElseThrow(() -> new ResourceNotFoundException("Issue", issueId));
    }
}
