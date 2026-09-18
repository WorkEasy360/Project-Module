package com.projectmodule.work.application;

import com.projectmodule.common.context.RequestContext;
import com.projectmodule.common.exception.ProjectModuleException;
import com.projectmodule.work.api.dto.BulkArchiveResultResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import org.springframework.stereotype.Service;

/**
 * Bulk-archive orchestration for Task/Risk/Issue/Decision, per the approved
 * {@code docs/project/20-BULK-ACTIONS-SPEC.md}.
 *
 * <p>Each id is archived by calling the existing, unmodified single-item
 * {@code archiveTask}/{@code archiveRisk}/{@code archiveIssue}/{@code archiveDecision} method on
 * its own injected (Spring-proxied) application service bean — the identical authorization,
 * organization-boundary and not-found handling an individual {@code DELETE} call already has, no
 * privileged bypass (§2). Calling through the injected bean, rather than from a method inside
 * {@code TaskApplicationService} itself, matters: it is what makes each call go through the real
 * Spring transactional proxy and therefore commit (or fail) independently in its own transaction
 * (§3) — a same-class self-invocation would silently skip {@code @Transactional} advice.
 *
 * <p>A failing id does not stop the batch: its {@link ProjectModuleException} is caught and
 * reported per-item; anything not a {@code ProjectModuleException} (this module's own sealed
 * exception hierarchy) is not expected and is allowed to propagate.
 */
@Service
public class BulkActionsApplicationService {

    private final TaskApplicationService taskApplicationService;
    private final RiskApplicationService riskApplicationService;
    private final IssueApplicationService issueApplicationService;
    private final DecisionApplicationService decisionApplicationService;

    public BulkActionsApplicationService(TaskApplicationService taskApplicationService,
                                         RiskApplicationService riskApplicationService,
                                         IssueApplicationService issueApplicationService,
                                         DecisionApplicationService decisionApplicationService) {
        this.taskApplicationService = taskApplicationService;
        this.riskApplicationService = riskApplicationService;
        this.issueApplicationService = issueApplicationService;
        this.decisionApplicationService = decisionApplicationService;
    }

    public List<BulkArchiveResultResponse> bulkArchiveTasks(RequestContext context, List<UUID> ids) {
        return archiveEach(ids, id -> taskApplicationService.archiveTask(context, id));
    }

    public List<BulkArchiveResultResponse> bulkArchiveRisks(RequestContext context, List<UUID> ids) {
        return archiveEach(ids, id -> riskApplicationService.archiveRisk(context, id));
    }

    public List<BulkArchiveResultResponse> bulkArchiveIssues(RequestContext context, List<UUID> ids) {
        return archiveEach(ids, id -> issueApplicationService.archiveIssue(context, id));
    }

    public List<BulkArchiveResultResponse> bulkArchiveDecisions(RequestContext context, List<UUID> ids) {
        return archiveEach(ids, id -> decisionApplicationService.archiveDecision(context, id));
    }

    private static List<BulkArchiveResultResponse> archiveEach(List<UUID> ids, Consumer<UUID> archiveOne) {
        List<BulkArchiveResultResponse> results = new ArrayList<>();
        for (UUID id : ids) {
            try {
                archiveOne.accept(id);
                results.add(new BulkArchiveResultResponse(id, true, null));
            } catch (ProjectModuleException e) {
                results.add(new BulkArchiveResultResponse(id, false, e.getMessage()));
            }
        }
        return results;
    }
}
