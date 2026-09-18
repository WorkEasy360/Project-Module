package com.projectmodule.work.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.projectmodule.common.identity.ExternalUserId;
import com.projectmodule.common.identity.OrganizationId;
import com.projectmodule.project.domain.Project;
import com.projectmodule.project.domain.ProjectPriority;
import com.projectmodule.project.infrastructure.ProjectRepository;
import com.projectmodule.work.domain.Risk;
import com.projectmodule.work.domain.RiskStatus;
import java.util.UUID;
import org.hibernate.exception.ConstraintViolationException;
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
 * Repository tests for Risk, run against a real PostgreSQL 17 instance — the same conventions
 * {@code TaskPersistenceTest}/{@code DependencyPersistenceTest} already establish. Starting this
 * context proves the entity mapping matches the {@code V5} migration, including its
 * database-level {@code CHECK} constraints.
 *
 * <p>Skipped when {@code PROJECTMODULE_TEST_DB_URL} is not set.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@EnabledIfEnvironmentVariable(named = "PROJECTMODULE_TEST_DB_URL", matches = ".+")
class RiskPersistenceTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private ProjectRepository projectRepository;

    @Autowired
    private RiskRepository riskRepository;

    private final OrganizationId organization = OrganizationId.of(UUID.randomUUID());
    private final ExternalUserId owner = ExternalUserId.of(UUID.randomUUID());

    private Project persistedProject(String name) {
        Project project = Project.create(organization, name, owner, ProjectPriority.MEDIUM);
        return projectRepository.saveAndFlush(project);
    }

    @Nested
    class RiskPersistence {

        @Test
        @DisplayName("round-trips a risk, including its priority and status")
        void roundTripsRisk() {
            Project project = persistedProject("Apollo");
            Risk risk = Risk.create(project.getId(), "Vendor delay", ProjectPriority.HIGH);
            UUID id = risk.getId();

            riskRepository.saveAndFlush(risk);
            entityManager.clear();

            Risk loaded = riskRepository.findById(id).orElseThrow();
            assertThat(loaded.getName()).isEqualTo("Vendor delay");
            assertThat(loaded.getProjectId()).isEqualTo(project.getId());
            assertThat(loaded.getPriority()).isEqualTo(ProjectPriority.HIGH);
            assertThat(loaded.getStatus()).isEqualTo(RiskStatus.OPEN);
        }

        @Test
        @DisplayName("soft-archived risks are still stored, with their archive detail")
        void softArchiveRetainsRow() {
            Project project = persistedProject("Apollo");
            Risk risk = riskRepository.saveAndFlush(
                    Risk.create(project.getId(), "Vendor delay", ProjectPriority.HIGH));

            risk.archive(owner);
            riskRepository.saveAndFlush(risk);
            entityManager.clear();

            Risk reloaded = riskRepository.findById(risk.getId()).orElseThrow();
            assertThat(reloaded.isArchived()).isTrue();
            assertThat(riskRepository.findByIdAndArchivedAtIsNull(risk.getId())).isEmpty();
        }

        @Test
        @DisplayName("cannot reference a project that does not exist")
        void enforcesProjectForeignKey() {
            Risk risk = Risk.create(UUID.randomUUID(), "Orphan", ProjectPriority.LOW);

            assertThatThrownBy(() -> riskRepository.saveAndFlush(risk))
                    .isInstanceOf(DataIntegrityViolationException.class);
        }

        @Test
        @DisplayName("the database rejects a status the application does not define")
        void databaseRejectsUnknownStatus() {
            Project project = persistedProject("Apollo");
            Risk risk = riskRepository.saveAndFlush(
                    Risk.create(project.getId(), "Vendor delay", ProjectPriority.HIGH));

            assertThatThrownBy(() -> {
                entityManager.getEntityManager()
                        .createNativeQuery("UPDATE risks SET status = 'NOT_A_STATUS' WHERE id = :id")
                        .setParameter("id", risk.getId())
                        .executeUpdate();
                entityManager.flush();
            }).isInstanceOf(ConstraintViolationException.class);
        }

        @Test
        @DisplayName("the database rejects a priority the application does not define")
        void databaseRejectsUnknownPriority() {
            Project project = persistedProject("Apollo");
            Risk risk = riskRepository.saveAndFlush(
                    Risk.create(project.getId(), "Vendor delay", ProjectPriority.HIGH));

            assertThatThrownBy(() -> {
                entityManager.getEntityManager()
                        .createNativeQuery("UPDATE risks SET priority = 'NOT_A_PRIORITY' WHERE id = :id")
                        .setParameter("id", risk.getId())
                        .executeUpdate();
                entityManager.flush();
            }).isInstanceOf(ConstraintViolationException.class);
        }

        @Test
        @DisplayName("lists a project's active risks, excluding archived ones")
        void listsActiveRisks() {
            Project project = persistedProject("Apollo");
            riskRepository.saveAndFlush(Risk.create(project.getId(), "Vendor delay", ProjectPriority.HIGH));
            Risk archived = riskRepository.saveAndFlush(
                    Risk.create(project.getId(), "Legacy", ProjectPriority.LOW));
            archived.archive(owner);
            riskRepository.saveAndFlush(archived);
            entityManager.clear();

            assertThat(riskRepository.findByProjectIdAndArchivedAtIsNullOrderByCreatedAtAsc(project.getId()))
                    .extracting(Risk::getName)
                    .containsExactly("Vendor delay");
        }
    }

    /**
     * Coverage for {@code RiskRepository.search}, per {@code docs/project/13-SEARCH-SPEC.md}.
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
            riskRepository.saveAndFlush(Risk.create(project.getId(), "Vendor Delay", ProjectPriority.HIGH));
            entityManager.clear();

            assertThat(riskRepository.search(project.getId(), "VENDOR"))
                    .extracting(Risk::getName)
                    .containsExactly("Vendor Delay");
        }

        @Test
        @DisplayName("matches a partial substring, not only a whole-word match")
        void matchesPartialSubstring() {
            Project project = persistedProject("Apollo");
            riskRepository.saveAndFlush(Risk.create(project.getId(), "Vendor delay", ProjectPriority.HIGH));
            entityManager.clear();

            assertThat(riskRepository.search(project.getId(), "end")).hasSize(1);
        }

        @Test
        @DisplayName("matches on name")
        void matchesOnName() {
            Project project = persistedProject("Apollo");
            Risk risk = Risk.create(project.getId(), "Vendor delay", ProjectPriority.HIGH);
            risk.describe("Unrelated text");
            riskRepository.saveAndFlush(risk);
            entityManager.clear();

            assertThat(riskRepository.search(project.getId(), "vendor")).hasSize(1);
        }

        @Test
        @DisplayName("matches on description")
        void matchesOnDescription() {
            Project project = persistedProject("Apollo");
            Risk risk = Risk.create(project.getId(), "Unrelated name", ProjectPriority.HIGH);
            risk.describe("Key vendor may miss the deadline");
            riskRepository.saveAndFlush(risk);
            entityManager.clear();

            assertThat(riskRepository.search(project.getId(), "vendor")).hasSize(1);
        }

        @Test
        @DisplayName("excludes archived risks")
        void excludesArchivedRisks() {
            Project project = persistedProject("Apollo");
            Risk risk = riskRepository.saveAndFlush(
                    Risk.create(project.getId(), "Vendor delay", ProjectPriority.HIGH));
            risk.archive(owner);
            riskRepository.saveAndFlush(risk);
            entityManager.clear();

            assertThat(riskRepository.search(project.getId(), "vendor")).isEmpty();
        }

        @Test
        @DisplayName("excludes matches belonging to a different project")
        void excludesOtherProjects() {
            Project projectA = persistedProject("Apollo");
            Project projectB = persistedProject("Artemis");
            riskRepository.saveAndFlush(Risk.create(projectB.getId(), "Vendor delay", ProjectPriority.HIGH));
            entityManager.clear();

            assertThat(riskRepository.search(projectA.getId(), "vendor")).isEmpty();
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
            riskRepository.saveAndFlush(Risk.create(project.getId(), "Vendor delay", ProjectPriority.HIGH));
            entityManager.clear();

            assertThat(riskRepository.findByFilters(project.getId(), null, null, false, DEFAULT_SORT))
                    .extracting(Risk::getName)
                    .containsExactlyElementsOf(riskRepository
                            .findByProjectIdAndArchivedAtIsNullOrderByCreatedAtAsc(project.getId())
                            .stream().map(Risk::getName).toList());
        }

        @Test
        @DisplayName("combines status and priority filters with AND")
        void combinesStatusAndPriority() {
            Project project = persistedProject("Apollo");
            Risk matching = riskRepository.saveAndFlush(
                    Risk.create(project.getId(), "Matches", ProjectPriority.HIGH));
            Risk wrongPriority = riskRepository.saveAndFlush(
                    Risk.create(project.getId(), "Wrong priority", ProjectPriority.LOW));
            Risk resolved = Risk.create(project.getId(), "Resolved risk", ProjectPriority.HIGH);
            resolved.resolve();
            riskRepository.saveAndFlush(resolved);
            entityManager.clear();

            assertThat(riskRepository.findByFilters(
                            project.getId(), RiskStatus.OPEN, ProjectPriority.HIGH, false, DEFAULT_SORT))
                    .extracting(Risk::getName)
                    .containsExactly("Matches");
        }

        @Test
        @DisplayName("archived=true returns only archived risks")
        void archivedTrueReturnsOnlyArchived() {
            Project project = persistedProject("Apollo");
            riskRepository.saveAndFlush(Risk.create(project.getId(), "Active", ProjectPriority.HIGH));
            Risk archived = riskRepository.saveAndFlush(
                    Risk.create(project.getId(), "Archived", ProjectPriority.HIGH));
            archived.archive(owner);
            riskRepository.saveAndFlush(archived);
            entityManager.clear();

            assertThat(riskRepository.findByFilters(project.getId(), null, null, true, DEFAULT_SORT))
                    .extracting(Risk::getName)
                    .containsExactly("Archived");
        }

        @Test
        @DisplayName("sorts by name descending, per docs/project/15-LIST-SPEC.md")
        void sortsByRequestedFieldAndDirection() {
            Project project = persistedProject("Apollo");
            riskRepository.saveAndFlush(Risk.create(project.getId(), "Alpha", ProjectPriority.HIGH));
            riskRepository.saveAndFlush(Risk.create(project.getId(), "Beta", ProjectPriority.HIGH));
            entityManager.clear();

            assertThat(riskRepository.findByFilters(project.getId(), null, null, false,
                            Sort.by(Sort.Direction.DESC, "name")))
                    .extracting(Risk::getName)
                    .containsExactly("Beta", "Alpha");
        }

        @Test
        @DisplayName("countActiveByStatus/countActiveByPriority group active risks, excluding archived")
        void countsActiveRisksByStatusAndPriority() {
            Project project = persistedProject("Apollo");
            riskRepository.saveAndFlush(Risk.create(project.getId(), "One", ProjectPriority.HIGH));
            riskRepository.saveAndFlush(Risk.create(project.getId(), "Two", ProjectPriority.HIGH));
            Risk archived = riskRepository.saveAndFlush(Risk.create(project.getId(), "Archived", ProjectPriority.LOW));
            archived.archive(owner);
            riskRepository.saveAndFlush(archived);
            entityManager.clear();

            var byStatus = riskRepository.countActiveByStatus(project.getId()).stream()
                    .collect(java.util.stream.Collectors.toMap(RiskRepository.StatusCount::getStatus,
                            RiskRepository.StatusCount::getTotal));
            var byPriority = riskRepository.countActiveByPriority(project.getId()).stream()
                    .collect(java.util.stream.Collectors.toMap(RiskRepository.PriorityCount::getPriority,
                            RiskRepository.PriorityCount::getTotal));

            assertThat(byStatus.get(RiskStatus.OPEN)).isEqualTo(2L);
            assertThat(byPriority.get(ProjectPriority.HIGH)).isEqualTo(2L);
            assertThat(byPriority).doesNotContainKey(ProjectPriority.LOW);
        }
    }
}
