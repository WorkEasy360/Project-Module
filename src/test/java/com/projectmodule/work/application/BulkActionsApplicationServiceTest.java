package com.projectmodule.work.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

import com.projectmodule.common.context.ActorType;
import com.projectmodule.common.context.ImmutableRequestContext;
import com.projectmodule.common.context.RequestContext;
import com.projectmodule.common.exception.ResourceNotFoundException;
import com.projectmodule.common.identity.ExternalUserId;
import com.projectmodule.common.identity.OrganizationId;
import com.projectmodule.work.api.dto.BulkArchiveResultResponse;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class BulkActionsApplicationServiceTest {

    private static final OrganizationId ORG = OrganizationId.of(UUID.randomUUID());
    private static final ExternalUserId USER = ExternalUserId.of(UUID.randomUUID());

    @Mock
    private TaskApplicationService taskApplicationService;

    @Mock
    private RiskApplicationService riskApplicationService;

    @Mock
    private IssueApplicationService issueApplicationService;

    @Mock
    private DecisionApplicationService decisionApplicationService;

    private BulkActionsApplicationService service;
    private RequestContext context;

    @BeforeEach
    void setUp() {
        service = new BulkActionsApplicationService(
                taskApplicationService, riskApplicationService, issueApplicationService, decisionApplicationService);
        context = new ImmutableRequestContext(Optional.of(USER), Optional.of(ORG), "corr-1", ActorType.HUMAN);
    }

    @Test
    @DisplayName("bulkArchiveTasks: all ids succeed")
    void bulkArchiveTasksAllSucceed() {
        UUID id1 = UUID.randomUUID();
        UUID id2 = UUID.randomUUID();

        List<BulkArchiveResultResponse> results = service.bulkArchiveTasks(context, List.of(id1, id2));

        assertThat(results).containsExactly(
                new BulkArchiveResultResponse(id1, true, null),
                new BulkArchiveResultResponse(id2, true, null));
        verify(taskApplicationService).archiveTask(context, id1);
        verify(taskApplicationService).archiveTask(context, id2);
    }

    @Test
    @DisplayName("bulkArchiveTasks: one id fails, the rest still succeed and are reported individually")
    void bulkArchiveTasksPartialFailure() {
        UUID succeeds = UUID.randomUUID();
        UUID fails = UUID.randomUUID();
        doNothing().when(taskApplicationService).archiveTask(context, succeeds);
        doThrow(new ResourceNotFoundException("Task", fails))
                .when(taskApplicationService).archiveTask(context, fails);

        List<BulkArchiveResultResponse> results = service.bulkArchiveTasks(context, List.of(succeeds, fails));

        assertThat(results).hasSize(2);
        assertThat(results.get(0)).isEqualTo(new BulkArchiveResultResponse(succeeds, true, null));
        assertThat(results.get(1).id()).isEqualTo(fails);
        assertThat(results.get(1).succeeded()).isFalse();
        assertThat(results.get(1).error()).contains("Task");
        verify(taskApplicationService).archiveTask(context, succeeds);
    }

    @Test
    @DisplayName("bulkArchiveRisks delegates to RiskApplicationService.archiveRisk per id")
    void bulkArchiveRisksDelegates() {
        UUID id = UUID.randomUUID();

        List<BulkArchiveResultResponse> results = service.bulkArchiveRisks(context, List.of(id));

        assertThat(results).containsExactly(new BulkArchiveResultResponse(id, true, null));
        verify(riskApplicationService).archiveRisk(context, id);
    }

    @Test
    @DisplayName("bulkArchiveIssues delegates to IssueApplicationService.archiveIssue per id")
    void bulkArchiveIssuesDelegates() {
        UUID id = UUID.randomUUID();

        List<BulkArchiveResultResponse> results = service.bulkArchiveIssues(context, List.of(id));

        assertThat(results).containsExactly(new BulkArchiveResultResponse(id, true, null));
        verify(issueApplicationService).archiveIssue(context, id);
    }

    @Test
    @DisplayName("bulkArchiveDecisions delegates to DecisionApplicationService.archiveDecision per id")
    void bulkArchiveDecisionsDelegates() {
        UUID id = UUID.randomUUID();

        List<BulkArchiveResultResponse> results = service.bulkArchiveDecisions(context, List.of(id));

        assertThat(results).containsExactly(new BulkArchiveResultResponse(id, true, null));
        verify(decisionApplicationService).archiveDecision(context, id);
    }
}
