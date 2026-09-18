package com.projectmodule.work.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.projectmodule.common.identity.ExternalUserId;
import com.projectmodule.common.identity.OrganizationId;
import com.projectmodule.project.domain.Project;
import com.projectmodule.project.domain.ProjectPriority;
import com.projectmodule.project.infrastructure.ProjectRepository;
import com.projectmodule.work.domain.AIRecommendation;
import com.projectmodule.work.domain.RecommendationStatus;
import com.projectmodule.work.domain.RecommendationType;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;

/**
 * Repository tests for AIRecommendation, run against a real PostgreSQL 17 instance — the same
 * conventions {@code DecisionPersistenceTest} already establishes. Starting this context proves
 * the entity mapping matches the {@code V11} migration.
 *
 * <p>Skipped when {@code PROJECTMODULE_TEST_DB_URL} is not set.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@EnabledIfEnvironmentVariable(named = "PROJECTMODULE_TEST_DB_URL", matches = ".+")
class AIRecommendationPersistenceTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private ProjectRepository projectRepository;

    @Autowired
    private AIRecommendationRepository aiRecommendationRepository;

    private final OrganizationId organization = OrganizationId.of(UUID.randomUUID());
    private final ExternalUserId owner = ExternalUserId.of(UUID.randomUUID());
    private final ExternalUserId requester = ExternalUserId.of(UUID.randomUUID());

    private Project persistedProject(String name) {
        Project project = Project.create(organization, name, owner, ProjectPriority.MEDIUM);
        return projectRepository.saveAndFlush(project);
    }

    @Test
    @DisplayName("round-trips a recommendation, including its resourceId, question and payload")
    void roundTripsRecommendation() {
        Project project = persistedProject("Apollo");
        UUID taskId = UUID.randomUUID();
        AIRecommendation recommendation = AIRecommendation.create(project.getId(), RecommendationType.TASK_BREAKDOWN,
                taskId, null, "Break this task down", "It spans multiple concerns", "{\"subtasks\":3}", requester);
        UUID id = recommendation.getId();

        aiRecommendationRepository.saveAndFlush(recommendation);
        entityManager.clear();

        AIRecommendation loaded = aiRecommendationRepository.findById(id).orElseThrow();
        assertThat(loaded.getProjectId()).isEqualTo(project.getId());
        assertThat(loaded.getType()).isEqualTo(RecommendationType.TASK_BREAKDOWN);
        assertThat(loaded.getResourceId()).contains(taskId);
        assertThat(loaded.getQuestion()).isEmpty();
        assertThat(loaded.getTitle()).isEqualTo("Break this task down");
        assertThat(loaded.getPayload()).contains("{\"subtasks\":3}");
        assertThat(loaded.getStatus()).isEqualTo(RecommendationStatus.PENDING);
        assertThat(loaded.getRequestedBy()).isEqualTo(requester);
        assertThat(loaded.getRespondedBy()).isEmpty();
    }

    @Test
    @DisplayName("persists an accepted recommendation with its responder and timestamp")
    void persistsAcceptedRecommendation() {
        Project project = persistedProject("Apollo");
        AIRecommendation recommendation = aiRecommendationRepository.saveAndFlush(
                AIRecommendation.create(project.getId(), RecommendationType.RISK_ANALYSIS,
                        null, null, "Risk summary", "Two HIGH risks are open", null, requester));

        ExternalUserId responder = ExternalUserId.of(UUID.randomUUID());
        recommendation.accept(responder);
        aiRecommendationRepository.saveAndFlush(recommendation);
        entityManager.clear();

        AIRecommendation reloaded = aiRecommendationRepository.findById(recommendation.getId()).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(RecommendationStatus.ACCEPTED);
        assertThat(reloaded.getRespondedBy()).contains(responder);
        assertThat(reloaded.getRespondedAt()).isPresent();
    }

    @Test
    @DisplayName("cannot reference a project that does not exist")
    void enforcesProjectForeignKey() {
        AIRecommendation orphan = AIRecommendation.create(UUID.randomUUID(), RecommendationType.RISK_ANALYSIS,
                null, null, "title", "rationale", null, requester);

        assertThatThrownBy(() -> aiRecommendationRepository.saveAndFlush(orphan))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("lists a project's recommendations, most recent first")
    void listsRecommendationsMostRecentFirst() throws InterruptedException {
        Project project = persistedProject("Apollo");
        AIRecommendation first = aiRecommendationRepository.saveAndFlush(
                AIRecommendation.create(project.getId(), RecommendationType.RISK_ANALYSIS,
                        null, null, "First", "r", null, requester));
        Thread.sleep(5);
        AIRecommendation second = aiRecommendationRepository.saveAndFlush(
                AIRecommendation.create(project.getId(), RecommendationType.RISK_ANALYSIS,
                        null, null, "Second", "r", null, requester));
        entityManager.clear();

        assertThat(aiRecommendationRepository.findByProjectIdOrderByCreatedAtDesc(project.getId()))
                .extracting(AIRecommendation::getTitle)
                .containsExactly("Second", "First");
    }
}
