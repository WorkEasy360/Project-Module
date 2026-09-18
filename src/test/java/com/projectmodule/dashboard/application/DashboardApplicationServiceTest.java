package com.projectmodule.dashboard.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.projectmodule.common.context.ActorType;
import com.projectmodule.common.context.ImmutableRequestContext;
import com.projectmodule.common.context.RequestContext;
import com.projectmodule.common.exception.MissingIdentityException;
import com.projectmodule.common.identity.ExternalUserId;
import com.projectmodule.common.identity.OrganizationId;
import com.projectmodule.dashboard.api.dto.DashboardResponse;
import com.projectmodule.project.domain.ProjectPriority;
import com.projectmodule.project.domain.ProjectStatus;
import com.projectmodule.project.infrastructure.ProjectRepository;
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
class DashboardApplicationServiceTest {

    private static final OrganizationId ORG = OrganizationId.of(UUID.randomUUID());
    private static final ExternalUserId USER = ExternalUserId.of(UUID.randomUUID());

    @Mock
    private ProjectRepository projectRepository;

    private DashboardApplicationService service;
    private RequestContext context;

    @BeforeEach
    void setUp() {
        service = new DashboardApplicationService(projectRepository);
        context = new ImmutableRequestContext(
                Optional.of(USER), Optional.of(ORG), "corr-1", ActorType.HUMAN);
    }

    @Test
    @DisplayName("reports active and archived counts scoped to the caller's organization")
    void reportsCounts() {
        when(projectRepository.countByOrganizationIdAndArchivedAtIsNull(ORG.value())).thenReturn(7L);
        when(projectRepository.countByOrganizationIdAndArchivedAtIsNotNull(ORG.value())).thenReturn(2L);
        when(projectRepository.countActiveByStatus(ORG.value())).thenReturn(List.of());
        when(projectRepository.countActiveByPriority(ORG.value())).thenReturn(List.of());

        DashboardResponse dashboard = service.getDashboard(context);

        assertThat(dashboard.activeProjectCount()).isEqualTo(7L);
        assertThat(dashboard.archivedProjectCount()).isEqualTo(2L);
    }

    @Test
    @DisplayName("byStatus includes every status, zero-filled when the query returns no row for it")
    void byStatusIsZeroFilledForEveryEnumValue() {
        when(projectRepository.countByOrganizationIdAndArchivedAtIsNull(ORG.value())).thenReturn(3L);
        when(projectRepository.countByOrganizationIdAndArchivedAtIsNotNull(ORG.value())).thenReturn(0L);
        when(projectRepository.countActiveByStatus(ORG.value())).thenReturn(List.of(
                statusRow(ProjectStatus.ACTIVE, 2L),
                statusRow(ProjectStatus.PLANNING, 1L)));
        when(projectRepository.countActiveByPriority(ORG.value())).thenReturn(List.of());

        DashboardResponse dashboard = service.getDashboard(context);

        assertThat(dashboard.byStatus())
                .hasSize(ProjectStatus.values().length)
                .containsEntry(ProjectStatus.ACTIVE, 2L)
                .containsEntry(ProjectStatus.PLANNING, 1L)
                .containsEntry(ProjectStatus.ON_HOLD, 0L)
                .containsEntry(ProjectStatus.COMPLETED, 0L)
                .containsEntry(ProjectStatus.CANCELLED, 0L);
    }

    @Test
    @DisplayName("byPriority includes every priority, zero-filled when the query returns no row for it")
    void byPriorityIsZeroFilledForEveryEnumValue() {
        when(projectRepository.countByOrganizationIdAndArchivedAtIsNull(ORG.value())).thenReturn(1L);
        when(projectRepository.countByOrganizationIdAndArchivedAtIsNotNull(ORG.value())).thenReturn(0L);
        when(projectRepository.countActiveByStatus(ORG.value())).thenReturn(List.of());
        when(projectRepository.countActiveByPriority(ORG.value())).thenReturn(List.of(
                priorityRow(ProjectPriority.CRITICAL, 1L)));

        DashboardResponse dashboard = service.getDashboard(context);

        assertThat(dashboard.byPriority())
                .hasSize(ProjectPriority.values().length)
                .containsEntry(ProjectPriority.CRITICAL, 1L)
                .containsEntry(ProjectPriority.LOW, 0L)
                .containsEntry(ProjectPriority.MEDIUM, 0L)
                .containsEntry(ProjectPriority.HIGH, 0L);
    }

    @Test
    @DisplayName("refuses to build a dashboard without an organization in context")
    void requiresOrganization() {
        RequestContext anonymous = new ImmutableRequestContext(
                Optional.of(USER), Optional.empty(), "corr-2", ActorType.HUMAN);

        assertThatThrownBy(() -> service.getDashboard(anonymous))
                .isInstanceOf(MissingIdentityException.class);
    }

    private static ProjectRepository.StatusCount statusRow(ProjectStatus status, long total) {
        return new ProjectRepository.StatusCount() {
            @Override
            public ProjectStatus getStatus() {
                return status;
            }

            @Override
            public long getTotal() {
                return total;
            }
        };
    }

    private static ProjectRepository.PriorityCount priorityRow(ProjectPriority priority, long total) {
        return new ProjectRepository.PriorityCount() {
            @Override
            public ProjectPriority getPriority() {
                return priority;
            }

            @Override
            public long getTotal() {
                return total;
            }
        };
    }
}
