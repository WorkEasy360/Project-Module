package com.projectmodule.work.api;

import com.projectmodule.common.api.SortDirection;
import com.projectmodule.common.context.RequestContext;
import com.projectmodule.project.domain.ProjectPriority;
import com.projectmodule.work.api.dto.BulkArchiveRequest;
import com.projectmodule.work.api.dto.BulkArchiveResponse;
import com.projectmodule.work.api.dto.CreateRiskRequest;
import com.projectmodule.work.api.dto.RiskResponse;
import com.projectmodule.work.api.dto.UpdateRiskRequest;
import com.projectmodule.work.application.BulkActionsApplicationService;
import com.projectmodule.work.application.RiskApplicationService;
import com.projectmodule.work.domain.Risk;
import com.projectmodule.work.domain.RiskSortField;
import com.projectmodule.work.domain.RiskStatus;
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
 * REST endpoints for risks.
 *
 * <p><b>Not specified in {@code docs/project/04-API.md}</b> at all — this extends the same
 * "collection nested under a project, item flat" pattern {@code Phase}/{@code Milestone} already
 * use, rather than inventing a different shape. Holds no business logic or authorization
 * decision; both are delegated to {@link RiskApplicationService}.
 */
@RestController
public class RiskController {

    private final RiskApplicationService riskApplicationService;
    private final BulkActionsApplicationService bulkActionsApplicationService;
    private final RiskMapper riskMapper;
    private final RequestContext requestContext;

    public RiskController(RiskApplicationService riskApplicationService,
                          BulkActionsApplicationService bulkActionsApplicationService,
                          RiskMapper riskMapper,
                          RequestContext requestContext) {
        this.riskApplicationService = riskApplicationService;
        this.bulkActionsApplicationService = bulkActionsApplicationService;
        this.riskMapper = riskMapper;
        this.requestContext = requestContext;
    }

    @PostMapping("/projects/{projectId}/risks")
    public ResponseEntity<RiskResponse> createRisk(@PathVariable UUID projectId,
                                                    @Valid @RequestBody CreateRiskRequest request) {
        Risk created = riskApplicationService.createRisk(requestContext, projectId, request);
        RiskResponse response = riskMapper.toResponse(created);

        URI location = ServletUriComponentsBuilder.fromCurrentContextPath()
                .path("/api/v1/risks/{id}")
                .buildAndExpand(created.getId())
                .toUri();

        return ResponseEntity.created(location).body(response);
    }

    /**
     * All filter and sort parameters are optional; a request with none behaves exactly as before
     * these features existed. See {@code docs/project/14-FILTERS-SPEC.md} and
     * {@code docs/project/15-LIST-SPEC.md}.
     */
    @GetMapping("/projects/{projectId}/risks")
    public List<RiskResponse> listRisks(@PathVariable UUID projectId,
                                        @RequestParam(required = false) RiskStatus status,
                                        @RequestParam(required = false) ProjectPriority priority,
                                        @RequestParam(defaultValue = "false") boolean archived,
                                        @RequestParam(required = false) RiskSortField sortBy,
                                        @RequestParam(required = false) SortDirection sortDir) {
        return riskApplicationService.listRisks(requestContext, projectId, status, priority, archived,
                        sortBy, sortDir).stream()
                .map(riskMapper::toResponse)
                .toList();
    }

    @PatchMapping("/risks/{id}")
    public RiskResponse updateRisk(@PathVariable UUID id, @Valid @RequestBody UpdateRiskRequest request) {
        Risk updated = riskApplicationService.updateRisk(requestContext, id, request);
        return riskMapper.toResponse(updated);
    }

    /** Soft-archives the risk. See {@link Risk#archive}. */
    @DeleteMapping("/risks/{id}")
    public ResponseEntity<Void> archiveRisk(@PathVariable UUID id) {
        riskApplicationService.archiveRisk(requestContext, id);
        return ResponseEntity.noContent().build();
    }

    /** Bulk-archives risks. See {@code docs/project/20-BULK-ACTIONS-SPEC.md}. */
    @PostMapping("/projects/{projectId}/risks/bulk-archive")
    public BulkArchiveResponse bulkArchiveRisks(@PathVariable UUID projectId,
                                                @Valid @RequestBody BulkArchiveRequest request) {
        return new BulkArchiveResponse(bulkActionsApplicationService.bulkArchiveRisks(requestContext, request.ids()));
    }
}
