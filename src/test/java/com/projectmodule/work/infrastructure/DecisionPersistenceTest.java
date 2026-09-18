package com.projectmodule.work.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.projectmodule.common.identity.ExternalUserId;
import com.projectmodule.common.identity.OrganizationId;
import com.projectmodule.project.domain.Project;
import com.projectmodule.project.domain.ProjectPriority;
import com.projectmodule.project.infrastructure.ProjectRepository;
import com.projectmodule.work.domain.Decision;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Sort;
import org.springframework.test.context.ActiveProfiles;

/**
 * Repository tests for Decision, run against a real PostgreSQL 17 instance — the same
 * conventions {@code IssuePersistenceTest}/{@code RiskPersistenceTest} already establish.
 * Starting this context proves the entity mapping matches the {@code V7} migration.
 *
 * <p>Skipped when {@code PROJECTMODULE_TEST_DB_URL} is not set.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@EnabledIfEnvironmentVariable(named = "PROJECTMODULE_TEST_DB_URL", matches = ".+")
class DecisionPersistenceTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private ProjectRepository projectRepository;

    @Autowired
    private DecisionRepository decisionRepository;

    private final OrganizationId organization = OrganizationId.of(UUID.randomUUID());
    private final ExternalUserId owner = ExternalUserId.of(UUID.randomUUID());
    private final ExternalUserId decider = ExternalUserId.of(UUID.randomUUID());

    private Project persistedProject(String name) {
        Project project = Project.create(organization, name, owner, ProjectPriority.MEDIUM);
        return projectRepository.saveAndFlush(project);
    }

    @Nested
    class DecisionPersistence {

        @Test
        @DisplayName("round-trips a decision, including its decider")
        void roundTripsDecision() {
            Project project = persistedProject("Apollo");
            Decision decision = Decision.create(project.getId(), "Use PostgreSQL for persistence");
            decision.attributeTo(decider);
            UUID id = decision.getId();

            decisionRepository.saveAndFlush(decision);
            entityManager.clear();

            Decision loaded = decisionRepository.findById(id).orElseThrow();
            assertThat(loaded.getName()).isEqualTo("Use PostgreSQL for persistence");
            assertThat(loaded.getProjectId()).isEqualTo(project.getId());
            assertThat(loaded.getDecidedBy()).contains(decider);
        }

        @Test
        @DisplayName("a decision may be persisted with no decider at all")
        void persistsWithoutDecider() {
            Project project = persistedProject("Apollo");
            Decision decision = Decision.create(project.getId(), "Use PostgreSQL for persistence");

            decisionRepository.saveAndFlush(decision);
            entityManager.clear();

            assertThat(decisionRepository.findById(decision.getId()).orElseThrow().getDecidedBy()).isEmpty();
        }

        @Test
        @DisplayName("soft-archived decisions are still stored, with their archive detail")
        void softArchiveRetainsRow() {
            Project project = persistedProject("Apollo");
            Decision decision = decisionRepository.saveAndFlush(
                    Decision.create(project.getId(), "Use PostgreSQL for persistence"));

            decision.archive(owner);
            decisionRepository.saveAndFlush(decision);
            entityManager.clear();

            Decision reloaded = decisionRepository.findById(decision.getId()).orElseThrow();
            assertThat(reloaded.isArchived()).isTrue();
            assertThat(decisionRepository.findByIdAndArchivedAtIsNull(decision.getId())).isEmpty();
        }

        @Test
        @DisplayName("cannot reference a project that does not exist")
        void enforcesProjectForeignKey() {
            Decision decision = Decision.create(UUID.randomUUID(), "Orphan");

            assertThatThrownBy(() -> decisionRepository.saveAndFlush(decision))
                    .isInstanceOf(DataIntegrityViolationException.class);
        }

        @Test
        @DisplayName("lists a project's active decisions, excluding archived ones")
        void listsActiveDecisions() {
            Project project = persistedProject("Apollo");
            decisionRepository.saveAndFlush(Decision.create(project.getId(), "Use PostgreSQL for persistence"));
            Decision archived = decisionRepository.saveAndFlush(
                    Decision.create(project.getId(), "Legacy decision"));
            archived.archive(owner);
            decisionRepository.saveAndFlush(archived);
            entityManager.clear();

            assertThat(decisionRepository.findByProjectIdAndArchivedAtIsNullOrderByCreatedAtAsc(project.getId()))
                    .extracting(Decision::getName)
                    .containsExactly("Use PostgreSQL for persistence");
        }
    }

    /**
     * Coverage for {@code DecisionRepository.search}, per {@code docs/project/13-SEARCH-SPEC.md}.
     * The cross-entity-type merge/sort/pagination behavior is covered by
     * {@code SearchApplicationServiceTest} (Mockito); this only proves the query itself against
     * real PostgreSQL.
     */
    @Nested
    class Search {

        @Test
        @DisplayName("matches case-insensitively")
        void matchesCaseInsensitively() {
            Project project = persistedProject("Apollo");
            decisionRepository.saveAndFlush(Decision.create(project.getId(), "Use PostgreSQL For Persistence"));
            entityManager.clear();

            assertThat(decisionRepository.search(project.getId(), "POSTGRESQL"))
                    .extracting(Decision::getName)
                    .containsExactly("Use PostgreSQL For Persistence");
        }

        @Test
        @DisplayName("matches a partial substring, not only a whole-word match")
        void matchesPartialSubstring() {
            Project project = persistedProject("Apollo");
            decisionRepository.saveAndFlush(Decision.create(project.getId(), "Use PostgreSQL for persistence"));
            entityManager.clear();

            assertThat(decisionRepository.search(project.getId(), "gres")).hasSize(1);
        }

        @Test
        @DisplayName("matches on name")
        void matchesOnName() {
            Project project = persistedProject("Apollo");
            decisionRepository.saveAndFlush(Decision.create(project.getId(), "Use PostgreSQL for persistence"));
            entityManager.clear();

            assertThat(decisionRepository.search(project.getId(), "postgresql")).hasSize(1);
        }

        @Test
        @DisplayName("matches on description")
        void matchesOnDescription() {
            Project project = persistedProject("Apollo");
            Decision decision = Decision.create(project.getId(), "Unrelated name");
            decision.describe("Chosen for jsonb and partial index support in PostgreSQL");
            decisionRepository.saveAndFlush(decision);
            entityManager.clear();

            assertThat(decisionRepository.search(project.getId(), "postgresql")).hasSize(1);
        }

        @Test
        @DisplayName("excludes archived decisions")
        void excludesArchivedDecisions() {
            Project project = persistedProject("Apollo");
            Decision decision = decisionRepository.saveAndFlush(
                    Decision.create(project.getId(), "Use PostgreSQL for persistence"));
            decision.archive(owner);
            decisionRepository.saveAndFlush(decision);
            entityManager.clear();

            assertThat(decisionRepository.search(project.getId(), "postgresql")).isEmpty();
        }

        @Test
        @DisplayName("excludes matches belonging to a different project")
        void excludesOtherProjects() {
            Project projectA = persistedProject("Apollo");
            Project projectB = persistedProject("Artemis");
            decisionRepository.saveAndFlush(Decision.create(projectB.getId(), "Use PostgreSQL for persistence"));
            entityManager.clear();

            assertThat(decisionRepository.search(projectA.getId(), "postgresql")).isEmpty();
        }
    }

    /**
     * Repository-level coverage for {@code findByFilters}, per
     * {@code docs/project/14-FILTERS-SPEC.md}, against real PostgreSQL.
     */
    @Nested
    class Filters {

        private static final Sort DEFAULT_SORT = Sort.by(Sort.Direction.ASC, "createdAt");

        @Test
        @DisplayName("no filters supplied returns the same rows as the unfiltered active-only query")
        void noFiltersMatchesUnfilteredQuery() {
            Project project = persistedProject("Apollo");
            decisionRepository.saveAndFlush(Decision.create(project.getId(), "Use PostgreSQL for persistence"));
            entityManager.clear();

            assertThat(decisionRepository.findByFilters(project.getId(), null, false, DEFAULT_SORT))
                    .extracting(Decision::getName)
                    .containsExactlyElementsOf(decisionRepository
                            .findByProjectIdAndArchivedAtIsNullOrderByCreatedAtAsc(project.getId())
                            .stream().map(Decision::getName).toList());
        }

        @Test
        @DisplayName("filters by decidedBy")
        void filtersByDecidedBy() {
            Project project = persistedProject("Apollo");
            Decision matching = Decision.create(project.getId(), "Use PostgreSQL for persistence");
            matching.attributeTo(owner);
            decisionRepository.saveAndFlush(matching);
            decisionRepository.saveAndFlush(Decision.create(project.getId(), "Undecided decision"));
            entityManager.clear();

            assertThat(decisionRepository.findByFilters(project.getId(), owner.value(), false, DEFAULT_SORT))
                    .extracting(Decision::getName)
                    .containsExactly("Use PostgreSQL for persistence");
        }

        @Test
        @DisplayName("archived=true returns only archived decisions")
        void archivedTrueReturnsOnlyArchived() {
            Project project = persistedProject("Apollo");
            decisionRepository.saveAndFlush(Decision.create(project.getId(), "Active"));
            Decision archived = decisionRepository.saveAndFlush(Decision.create(project.getId(), "Archived"));
            archived.archive(owner);
            decisionRepository.saveAndFlush(archived);
            entityManager.clear();

            assertThat(decisionRepository.findByFilters(project.getId(), null, true, DEFAULT_SORT))
                    .extracting(Decision::getName)
                    .containsExactly("Archived");
        }

        @Test
        @DisplayName("sorts by name descending, per docs/project/15-LIST-SPEC.md")
        void sortsByRequestedFieldAndDirection() {
            Project project = persistedProject("Apollo");
            decisionRepository.saveAndFlush(Decision.create(project.getId(), "Alpha decision"));
            decisionRepository.saveAndFlush(Decision.create(project.getId(), "Beta decision"));
            entityManager.clear();

            assertThat(decisionRepository.findByFilters(project.getId(), null, false,
                            Sort.by(Sort.Direction.DESC, "name")))
                    .extracting(Decision::getName)
                    .containsExactly("Beta decision", "Alpha decision");
        }

        @Test
        @DisplayName("countByProjectIdAndArchivedAtIsNull counts active decisions, excluding archived")
        void countsActiveDecisions() {
            Project project = persistedProject("Apollo");
            decisionRepository.saveAndFlush(Decision.create(project.getId(), "One"));
            decisionRepository.saveAndFlush(Decision.create(project.getId(), "Two"));
            Decision archived = decisionRepository.saveAndFlush(Decision.create(project.getId(), "Archived"));
            archived.archive(owner);
            decisionRepository.saveAndFlush(archived);
            entityManager.clear();

            assertThat(decisionRepository.countByProjectIdAndArchivedAtIsNull(project.getId())).isEqualTo(2L);
        }
    }
}
