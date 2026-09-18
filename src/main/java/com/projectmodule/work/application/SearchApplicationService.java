package com.projectmodule.work.application;

import com.projectmodule.common.context.RequestContext;
import com.projectmodule.common.exception.BusinessRuleViolationException;
import com.projectmodule.project.application.ProjectApplicationService;
import com.projectmodule.project.application.authorization.ProjectAuthorizationService;
import com.projectmodule.project.application.authorization.ProjectPermission;
import com.projectmodule.work.api.dto.SearchResultResponse;
import com.projectmodule.work.domain.Decision;
import com.projectmodule.work.domain.Issue;
import com.projectmodule.work.domain.Risk;
import com.projectmodule.work.domain.Task;
import com.projectmodule.work.infrastructure.DecisionRepository;
import com.projectmodule.work.infrastructure.IssueRepository;
import com.projectmodule.work.infrastructure.RiskRepository;
import com.projectmodule.work.infrastructure.TaskRepository;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Search across Task, Risk, Issue and Decision within one project, per the approved
 * {@code docs/project/13-SEARCH-SPEC.md}.
 *
 * <p>Project-scoped, not organization-wide (approved Decision #1): the boundary is resolved
 * through {@code ProjectApplicationService.findActiveProjectInCallerOrganization}, the identical
 * pattern every work-management application service already uses, then gated by
 * {@code ProjectPermission.VIEW_PROJECT} — the same permission
 * {@code listTasks}/{@code listRisks}/{@code listIssues}/{@code listDecisions} each already
 * require individually. No new permission, no new authorization mechanism.
 *
 * <p>Read-only: queries the four existing repositories' new {@code search(...)} methods
 * directly, the same cross-cutting read-only pattern {@code DashboardApplicationService} already
 * establishes for aggregate counts. No entity's domain/validation/status-transition rules are
 * touched or duplicated — there is nothing to mutate.
 *
 * <p>Merging is done here, in the application layer (approved Decision #3), not as a database
 * {@code UNION}: each repository is queried independently for the whole (bounded, per-project)
 * set of matches, the four lists are concatenated, sorted by {@code updatedAt} descending, and
 * only then paginated in memory. This is the approved V1 approach; a database-level merge is an
 * explicitly out-of-scope future enhancement, not part of this service.
 */
@Service
public class SearchApplicationService {

    private final TaskRepository taskRepository;
    private final RiskRepository riskRepository;
    private final IssueRepository issueRepository;
    private final DecisionRepository decisionRepository;
    private final ProjectApplicationService projectApplicationService;
    private final ProjectAuthorizationService projectAuthorizationService;

    public SearchApplicationService(TaskRepository taskRepository,
                                    RiskRepository riskRepository,
                                    IssueRepository issueRepository,
                                    DecisionRepository decisionRepository,
                                    ProjectApplicationService projectApplicationService,
                                    ProjectAuthorizationService projectAuthorizationService) {
        this.taskRepository = taskRepository;
        this.riskRepository = riskRepository;
        this.issueRepository = issueRepository;
        this.decisionRepository = decisionRepository;
        this.projectApplicationService = projectApplicationService;
        this.projectAuthorizationService = projectAuthorizationService;
    }

    @Transactional(readOnly = true)
    public Page<SearchResultResponse> search(RequestContext context, UUID projectId, String term, Pageable pageable) {
        if (term == null || term.isBlank()) {
            throw new BusinessRuleViolationException("Search query must not be blank");
        }

        projectApplicationService.findActiveProjectInCallerOrganization(context, projectId);
        projectAuthorizationService.requirePermission(
                context.requireUserId(), projectId, ProjectPermission.VIEW_PROJECT);

        List<SearchResultResponse> results = new ArrayList<>();
        for (Task task : taskRepository.search(projectId, term)) {
            results.add(toResult("TASK", task.getId(), task.getProjectId(), task.getName(),
                    task.getDescription().orElse(null), task.getCreatedAt(), task.getUpdatedAt()));
        }
        for (Risk risk : riskRepository.search(projectId, term)) {
            results.add(toResult("RISK", risk.getId(), risk.getProjectId(), risk.getName(),
                    risk.getDescription().orElse(null), risk.getCreatedAt(), risk.getUpdatedAt()));
        }
        for (Issue issue : issueRepository.search(projectId, term)) {
            results.add(toResult("ISSUE", issue.getId(), issue.getProjectId(), issue.getName(),
                    issue.getDescription().orElse(null), issue.getCreatedAt(), issue.getUpdatedAt()));
        }
        for (Decision decision : decisionRepository.search(projectId, term)) {
            results.add(toResult("DECISION", decision.getId(), decision.getProjectId(), decision.getName(),
                    decision.getDescription().orElse(null), decision.getCreatedAt(), decision.getUpdatedAt()));
        }

        results.sort(Comparator.comparing(SearchResultResponse::updatedAt).reversed());

        return paginate(results, pageable);
    }

    private static SearchResultResponse toResult(String resourceType, UUID id, UUID projectId, String name,
                                                  String description, java.time.Instant createdAt,
                                                  java.time.Instant updatedAt) {
        return new SearchResultResponse(resourceType, id, projectId, name, description, false, createdAt, updatedAt);
    }

    /**
     * Pages an already-sorted, in-memory list — there is no single repository {@code Page<T>}
     * to page here, since results are merged from four independent queries (see class-level
     * documentation).
     */
    private static Page<SearchResultResponse> paginate(List<SearchResultResponse> sorted, Pageable pageable) {
        int start = (int) pageable.getOffset();
        if (start >= sorted.size()) {
            return new PageImpl<>(List.of(), pageable, sorted.size());
        }
        int end = Math.min(start + pageable.getPageSize(), sorted.size());
        return new PageImpl<>(sorted.subList(start, end), pageable, sorted.size());
    }
}
