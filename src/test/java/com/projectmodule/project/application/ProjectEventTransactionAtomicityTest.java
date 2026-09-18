package com.projectmodule.project.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.projectmodule.collaboration.infrastructure.ProjectActivityRepository;
import com.projectmodule.common.context.ActorType;
import com.projectmodule.common.context.ImmutableRequestContext;
import com.projectmodule.common.context.RequestContext;
import com.projectmodule.common.identity.ExternalUserId;
import com.projectmodule.common.identity.OrganizationId;
import com.projectmodule.events.infrastructure.OutboxMessageRepository;
import com.projectmodule.project.api.dto.CreateProjectRequest;
import com.projectmodule.project.api.dto.UpdateProjectRequest;
import com.projectmodule.project.domain.Project;
import com.projectmodule.project.domain.ProjectPriority;
import com.projectmodule.project.infrastructure.ProjectMemberRepository;
import com.projectmodule.project.infrastructure.ProjectRepository;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Proves, against a real PostgreSQL 17 instance, that a project state change, its
 * {@code ProjectActivity} audit entry and its {@code OutboxMessage} commit or roll back
 * together — not with Mockito, with an actual forced rollback and a fresh read afterward.
 *
 * <p>{@link ProjectApplicationService#createProject} and {@code #updateProject} are called
 * inside a {@link TransactionTemplate}-managed transaction whose callback then throws. Spring's
 * {@code @Transactional} on the service method defaults to propagation {@code REQUIRED}, so it
 * joins this outer transaction rather than opening its own; when the callback's exception
 * forces that outer transaction to roll back, everything the service call did rolls back with
 * it. The test itself is deliberately not {@code @Transactional} — the only transaction
 * boundary is the one under test, so the assertions afterward read genuinely committed (or, as
 * expected here, never-committed) state.
 *
 * <p>Skipped unless {@code PROJECTMODULE_TEST_DB_URL} is set, matching every other real-database
 * test in this module.
 */
@SpringBootTest
@ActiveProfiles("test")
@EnabledIfEnvironmentVariable(named = "PROJECTMODULE_TEST_DB_URL", matches = ".+")
class ProjectEventTransactionAtomicityTest {

    @Autowired
    private ProjectApplicationService projectApplicationService;

    @Autowired
    private ProjectRepository projectRepository;

    @Autowired
    private ProjectMemberRepository projectMemberRepository;

    @Autowired
    private ProjectActivityRepository projectActivityRepository;

    @Autowired
    private OutboxMessageRepository outboxMessageRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private final OrganizationId organization = OrganizationId.of(UUID.randomUUID());
    private final ExternalUserId owner = ExternalUserId.of(UUID.randomUUID());
    private final RequestContext context = new ImmutableRequestContext(
            Optional.of(owner), Optional.of(organization), "corr-atomicity", ActorType.HUMAN);

    @Test
    @DisplayName("a forced failure after creation rolls back the project, its membership, its "
            + "activity entry and its outbox record together")
    void createIsAtomicAcrossProjectMemberActivityAndOutbox() {
        TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);
        CreateProjectRequest request = new CreateProjectRequest(
                "Atomicity Check", "should never survive", ProjectPriority.MEDIUM, null, null);
        UUID[] createdId = new UUID[1];

        assertThatThrownBy(() -> transactionTemplate.executeWithoutResult(status -> {
            Project created = projectApplicationService.createProject(context, request);
            createdId[0] = created.getId();
            throw new IllegalStateException("forced failure after create, to prove rollback");
        })).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("forced failure after create");

        UUID projectId = createdId[0];
        assertThat(projectId).as("the domain object still got an id in memory").isNotNull();

        assertThat(projectRepository.findById(projectId))
                .as("the project row must not exist after rollback")
                .isEmpty();
        assertThat(projectMemberRepository.findByProjectId(projectId))
                .as("the OWNER membership row must not exist after rollback")
                .isEmpty();
        assertThat(projectActivityRepository
                        .findByProjectIdOrderByOccurredAtDesc(projectId, PageRequest.of(0, 10))
                        .getContent())
                .as("no ProjectActivity row must exist after rollback")
                .isEmpty();
        assertThat(outboxMessageRepository.findByAggregateIdOrderByOccurredAtAsc(projectId))
                .as("no OutboxMessage row must exist after rollback")
                .isEmpty();
    }

    @Test
    @DisplayName("a forced failure after update rolls back the field change, the activity entry "
            + "and the outbox record together, leaving the prior committed state intact")
    void updateIsAtomicAcrossProjectActivityAndOutbox() {
        TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);

        // Committed first, in its own transaction, so there is a real prior state to protect.
        Project project = transactionTemplate.execute(status -> projectApplicationService
                .createProject(context, new CreateProjectRequest(
                        "Update Atomicity Check", null, ProjectPriority.LOW, null, null)));
        UUID projectId = project.getId();
        long versionBeforeFailedUpdate = project.getVersion();

        UpdateProjectRequest badUpdate = new UpdateProjectRequest(
                "Should not stick", null, null, null,
                LocalDate.of(2026, 1, 1), null, versionBeforeFailedUpdate);

        assertThatThrownBy(() -> transactionTemplate.executeWithoutResult(status -> {
            projectApplicationService.updateProject(context, projectId, badUpdate);
            throw new IllegalStateException("forced failure after update, to prove rollback");
        })).isInstanceOf(IllegalStateException.class);

        Project reloaded = projectRepository.findById(projectId).orElseThrow();
        assertThat(reloaded.getName())
                .as("the name change must not have survived the rollback")
                .isEqualTo("Update Atomicity Check");
        assertThat(reloaded.getVersion())
                .as("the version must not have advanced")
                .isEqualTo(versionBeforeFailedUpdate);

        // Exactly the one activity/outbox row from the successful create; none from the
        // rolled-back update.
        assertThat(projectActivityRepository
                        .findByProjectIdOrderByOccurredAtDesc(projectId, PageRequest.of(0, 10))
                        .getContent())
                .hasSize(1);
        assertThat(outboxMessageRepository.findByAggregateIdOrderByOccurredAtAsc(projectId))
                .hasSize(1);
    }
}
