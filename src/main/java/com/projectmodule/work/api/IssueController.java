package com.projectmodule.work.api;

import com.projectmodule.common.api.SortDirection;
import com.projectmodule.common.context.RequestContext;
import com.projectmodule.project.domain.ProjectPriority;
import com.projectmodule.work.api.dto.BulkArchiveRequest;
import com.projectmodule.work.api.dto.BulkArchiveResponse;
import com.projectmodule.work.api.dto.CreateIssueRequest;
import com.projectmodule.work.api.dto.IssueResponse;
import com.projectmodule.work.api.dto.UpdateIssueRequest;
import com.projectmodule.work.application.BulkActionsApplicationService;
import com.projectmodule.work.application.IssueApplicationService;
import com.projectmodule.work.domain.Issue;
import com.projectmodule.work.domain.IssueSortField;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

/**
 * REST endpoints for issues.
 *
 * <p><b>Not specified in {@code docs/project/04-API.md}</b> at all — this extends the same
 * "collection nested under a project, item flat" pattern {@code Phase}/{@code Milestone}/
 * {@code Risk} already use, rather than inventing a different shape. Holds no business logic or
 * authorization decision; both are delegated to {@link IssueApplicationService}.
 */
@RestController
public class IssueController {

    private final IssueApplicationService issueApplicationService;
    private final BulkActionsApplicationService bulkActionsApplicationService;
    private final IssueMapper issueMapper;
    private final RequestContext requestContext;

    public IssueController(IssueApplicationService issueApplicationService,
                           BulkActionsApplicationService bulkActionsApplicationService,
                           IssueMapper issueMapper,
                           RequestContext requestContext) {
        this.issueApplicationService = issueApplicationService;
        this.bulkActionsApplicationService = bulkActionsApplicationService;
        this.issueMapper = issueMapper;
        this.requestContext = requestContext;
    }

    @PostMapping("/projects/{projectId}/issues")
    public ResponseEntity<IssueResponse> createIssue(@PathVariable UUID projectId,
                                                      @Valid @RequestBody CreateIssueRequest request) {
        Issue created = issueApplicationService.createIssue(requestContext, projectId, request);
        IssueResponse response = issueMapper.toResponse(created);

        URI location = ServletUriComponentsBuilder.fromCurrentContextPath()
                .path("/api/v1/issues/{id}")
                .buildAndExpand(created.getId())
                .toUri();

        return ResponseEntity.created(location).body(response);
    }

    /**
     * All filter and sort parameters are optional; a request with none behaves exactly as before
     * these features existed. See {@code docs/project/14-FILTERS-SPEC.md} and
     * {@code docs/project/15-LIST-SPEC.md}.
     */
    @GetMapping("/projects/{projectId}/issues")
    public List<IssueResponse> listIssues(@PathVariable UUID projectId,
                                          @RequestParam(required = false) ProjectPriority priority,
                                          @RequestParam(defaultValue = "false") boolean archived,
                                          @RequestParam(required = false) IssueSortField sortBy,
                                          @RequestParam(required = false) SortDirection sortDir) {
        return issueApplicationService.listIssues(requestContext, projectId, priority, archived,
                        sortBy, sortDir).stream()
                .map(issueMapper::toResponse)
                .toList();
    }

    @PatchMapping("/issues/{id}")
    public IssueResponse updateIssue(@PathVariable UUID id, @Valid @RequestBody UpdateIssueRequest request) {
        Issue updated = issueApplicationService.updateIssue(requestContext, id, request);
        return issueMapper.toResponse(updated);
    }

    /** Soft-archives the issue. See {@link Issue#archive}. */
    @DeleteMapping("/issues/{id}")
    public ResponseEntity<Void> archiveIssue(@PathVariable UUID id) {
        issueApplicationService.archiveIssue(requestContext, id);
        return ResponseEntity.noContent().build();
    }

    /** Bulk-archives issues. See {@code docs/project/20-BULK-ACTIONS-SPEC.md}. */
    @PostMapping("/projects/{projectId}/issues/bulk-archive")
    public BulkArchiveResponse bulkArchiveIssues(@PathVariable UUID projectId,
                                                 @Valid @RequestBody BulkArchiveRequest request) {
        return new BulkArchiveResponse(bulkActionsApplicationService.bulkArchiveIssues(requestContext, request.ids()));
    }
}
