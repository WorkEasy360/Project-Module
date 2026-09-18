package com.projectmodule.work.api;

import com.projectmodule.common.api.SortDirection;
import com.projectmodule.common.context.RequestContext;
import com.projectmodule.work.api.dto.BulkArchiveRequest;
import com.projectmodule.work.api.dto.BulkArchiveResponse;
import com.projectmodule.work.api.dto.CreateDecisionRequest;
import com.projectmodule.work.api.dto.DecisionResponse;
import com.projectmodule.work.api.dto.UpdateDecisionRequest;
import com.projectmodule.work.application.BulkActionsApplicationService;
import com.projectmodule.work.application.DecisionApplicationService;
import com.projectmodule.work.domain.Decision;
import com.projectmodule.work.domain.DecisionSortField;
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
 * REST endpoints for decisions, exactly as {@code docs/project/09-DECISION-SPEC.md} §8 defines
 * them — the "collection nested under a project, item flat" pattern Phase/Milestone/Risk/Issue
 * already use, with no single-item {@code GET}, matching Phase/Milestone/Risk/Issue. Holds no
 * business logic or authorization decision; both are delegated to
 * {@link DecisionApplicationService}.
 */
@RestController
public class DecisionController {

    private final DecisionApplicationService decisionApplicationService;
    private final BulkActionsApplicationService bulkActionsApplicationService;
    private final DecisionMapper decisionMapper;
    private final RequestContext requestContext;

    public DecisionController(DecisionApplicationService decisionApplicationService,
                              BulkActionsApplicationService bulkActionsApplicationService,
                              DecisionMapper decisionMapper,
                              RequestContext requestContext) {
        this.decisionApplicationService = decisionApplicationService;
        this.bulkActionsApplicationService = bulkActionsApplicationService;
        this.decisionMapper = decisionMapper;
        this.requestContext = requestContext;
    }

    @PostMapping("/projects/{projectId}/decisions")
    public ResponseEntity<DecisionResponse> createDecision(@PathVariable UUID projectId,
                                                            @Valid @RequestBody CreateDecisionRequest request) {
        Decision created = decisionApplicationService.createDecision(requestContext, projectId, request);
        DecisionResponse response = decisionMapper.toResponse(created);

        URI location = ServletUriComponentsBuilder.fromCurrentContextPath()
                .path("/api/v1/decisions/{id}")
                .buildAndExpand(created.getId())
                .toUri();

        return ResponseEntity.created(location).body(response);
    }

    /**
     * All filter and sort parameters are optional; a request with none behaves exactly as before
     * these features existed. See {@code docs/project/14-FILTERS-SPEC.md} and
     * {@code docs/project/15-LIST-SPEC.md}.
     */
    @GetMapping("/projects/{projectId}/decisions")
    public List<DecisionResponse> listDecisions(@PathVariable UUID projectId,
                                                @RequestParam(required = false) UUID decidedBy,
                                                @RequestParam(defaultValue = "false") boolean archived,
                                                @RequestParam(required = false) DecisionSortField sortBy,
                                                @RequestParam(required = false) SortDirection sortDir) {
        return decisionApplicationService.listDecisions(requestContext, projectId, decidedBy, archived,
                        sortBy, sortDir).stream()
                .map(decisionMapper::toResponse)
                .toList();
    }

    @PatchMapping("/decisions/{id}")
    public DecisionResponse updateDecision(@PathVariable UUID id, @Valid @RequestBody UpdateDecisionRequest request) {
        Decision updated = decisionApplicationService.updateDecision(requestContext, id, request);
        return decisionMapper.toResponse(updated);
    }

    /** Soft-archives the decision. See {@link Decision#archive}. */
    @DeleteMapping("/decisions/{id}")
    public ResponseEntity<Void> archiveDecision(@PathVariable UUID id) {
        decisionApplicationService.archiveDecision(requestContext, id);
        return ResponseEntity.noContent().build();
    }

    /** Bulk-archives decisions. See {@code docs/project/20-BULK-ACTIONS-SPEC.md}. */
    @PostMapping("/projects/{projectId}/decisions/bulk-archive")
    public BulkArchiveResponse bulkArchiveDecisions(@PathVariable UUID projectId,
                                                    @Valid @RequestBody BulkArchiveRequest request) {
        return new BulkArchiveResponse(
                bulkActionsApplicationService.bulkArchiveDecisions(requestContext, request.ids()));
    }
}
