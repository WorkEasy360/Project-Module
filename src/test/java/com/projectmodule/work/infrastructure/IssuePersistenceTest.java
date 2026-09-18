package com.projectmodule.work.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.projectmodule.common.identity.ExternalUserId;
import com.projectmodule.common.identity.OrganizationId;
import com.projectmodule.project.domain.Project;
import com.projectmodule.project.domain.ProjectPriority;
import com.projectmodule.project.infrastructure.ProjectRepository;
import com.projectmodule.work.domain.Issue;
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
 * Repository tests for Issue, run against a real PostgreSQL 17 instance — the same conventions
 * {@code RiskPersistenceTest}/{@code TaskPersistenceTest} already establish. Starting this
 * context proves the entity mapping matches the {@code V6} migration, including its
 * database-level {@code CHECK} constraints.
 *
 * <p>Skipped when {@code PROJECTMODULE_TEST_DB_URL} is not set.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@EnabledIfEnvironmentVariable(named = "PROJECTMODULE_TEST_DB_URL", matches = ".+")
class IssuePersistenceTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private ProjectRepository projectRepository;

    @Autowired
    private IssueRepository issueRepository;

    private final OrganizationId organization = OrganizationId.of(UUID.randomUUID());
    private final ExternalUserId owner = ExternalUserId.of(UUID.randomUUID());

    private Project persistedProject(String name) {
        Project project = Project.create(organization, name, owner, ProjectPriority.MEDIUM);
        return projectRepository.saveAndFlush(project);
    }

    @Nested
    class IssuePersistence {

        @Test
        @DisplayName("round-trips an issue, including its priority")
        void roundTripsIssue() {
            Project project = persistedProject("Apollo");
            Issue issue = Issue.create(project.getId(), "Payment gateway down", ProjectPriority.CRITICAL);
            UUID id = issue.getId();

            issueRepository.saveAndFlush(issue);
            entityManager.clear();

            Issue loaded = issueRepository.findById(id).orElseThrow();
            assertThat(loaded.getName()).isEqualTo("Payment gateway down");
            assertThat(loaded.getProjectId()).isEqualTo(project.getId());
            assertThat(loaded.getPriority()).isEqualTo(ProjectPriority.CRITICAL);
        }

        @Test
        @DisplayName("soft-archived issues are still stored, with their archive detail")
        void softArchiveRetainsRow() {
            Project project = persistedProject("Apollo");
            Issue issue = issueRepository.saveAndFlush(
                    Issue.create(project.getId(), "Payment gateway down", ProjectPriority.CRITICAL));

            issue.archive(owner);
            issueRepository.saveAndFlush(issue);
            entityManager.clear();

            Issue reloaded = issueRepository.findById(issue.getId()).orElseThrow();
            assertThat(reloaded.isArchived()).isTrue();
            assertThat(issueRepository.findByIdAndArchivedAtIsNull(issue.getId())).isEmpty();
        }

        @Test
        @DisplayName("cannot reference a project that does not exist")
        void enforcesProjectForeignKey() {
            Issue issue = Issue.create(UUID.randomUUID(), "Orphan", ProjectPriority.LOW);

            assertThatThrownBy(() -> issueRepository.saveAndFlush(issue))
                    .isInstanceOf(DataIntegrityViolationException.class);
        }

        @Test
        @DisplayName("the database rejects a priority the application does not define")
        void databaseRejectsUnknownPriority() {
            Project project = persistedProject("Apollo");
            Issue issue = issueRepository.saveAndFlush(
                    Issue.create(project.getId(), "Payment gateway down", ProjectPriority.CRITICAL));

            assertThatThrownBy(() -> {
                entityManager.getEntityManager()
                        .createNativeQuery("UPDATE issues SET priority = 'NOT_A_PRIORITY' WHERE id = :id")
                        .setParameter("id", issue.getId())
                        .executeUpdate();
                entityManager.flush();
            }).isInstanceOf(ConstraintViolationException.class);
        }

        @Test
        @DisplayName("lists a project's active issues, excluding archived ones")
        void listsActiveIssues() {
            Project project = persistedProject("Apollo");
            issueRepository.saveAndFlush(Issue.create(project.getId(), "Payment gateway down", ProjectPriority.CRITICAL));
            Issue archived = issueRepository.saveAndFlush(
                    Issue.create(project.getId(), "Legacy", ProjectPriority.LOW));
            archived.archive(owner);
            issueRepository.saveAndFlush(archived);
            entityManager.clear();

            assertThat(issueRepository.findByProjectIdAndArchivedAtIsNullOrderByCreatedAtAsc(project.getId()))
                    .extracting(Issue::getName)
                    .containsExactly("Payment gateway down");
        }
    }

    /**
     * Coverage for {@code IssueRepository.search}, per {@code docs/project/13-SEARCH-SPEC.md}.
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
            issueRepository.saveAndFlush(Issue.create(project.getId(), "Payment Gateway Down", ProjectPriority.CRITICAL));
            entityManager.clear();

            assertThat(issueRepository.search(project.getId(), "PAYMENT"))
                    .extracting(Issue::getName)
                    .containsExactly("Payment Gateway Down");
        }

        @Test
        @DisplayName("matches a partial substring, not only a whole-word match")
        void matchesPartialSubstring() {
            Project project = persistedProject("Apollo");
            issueRepository.saveAndFlush(Issue.create(project.getId(), "Payment gateway down", ProjectPriority.CRITICAL));
            entityManager.clear();

            assertThat(issueRepository.search(project.getId(), "ateway")).hasSize(1);
        }

        @Test
        @DisplayName("matches on name")
        void matchesOnName() {
            Project project = persistedProject("Apollo");
            Issue issue = Issue.create(project.getId(), "Payment gateway down", ProjectPriority.CRITICAL);
            issueRepository.saveAndFlush(issue);
            entityManager.clear();

            assertThat(issueRepository.search(project.getId(), "payment")).hasSize(1);
        }

        @Test
        @DisplayName("matches on description")
        void matchesOnDescription() {
            Project project = persistedProject("Apollo");
            Issue issue = Issue.create(project.getId(), "Unrelated name", ProjectPriority.CRITICAL);
            issue.describe("Checkout failing for all payment attempts");
            issueRepository.saveAndFlush(issue);
            entityManager.clear();

            assertThat(issueRepository.search(project.getId(), "payment")).hasSize(1);
        }

        @Test
        @DisplayName("excludes archived issues")
        void excludesArchivedIssues() {
            Project project = persistedProject("Apollo");
            Issue issue = issueRepository.saveAndFlush(
                    Issue.create(project.getId(), "Payment gateway down", ProjectPriority.CRITICAL));
            issue.archive(owner);
            issueRepository.saveAndFlush(issue);
            entityManager.clear();

            assertThat(issueRepository.search(project.getId(), "payment")).isEmpty();
        }

        @Test
        @DisplayName("excludes matches belonging to a different project")
        void excludesOtherProjects() {
            Project projectA = persistedProject("Apollo");
            Project projectB = persistedProject("Artemis");
            issueRepository.saveAndFlush(Issue.create(projectB.getId(), "Payment gateway down", ProjectPriority.CRITICAL));
            entityManager.clear();

            assertThat(issueRepository.search(projectA.getId(), "payment")).isEmpty();
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
            issueRepository.saveAndFlush(Issue.create(project.getId(), "Payment gateway down", ProjectPriority.CRITICAL));
            entityManager.clear();

            assertThat(issueRepository.findByFilters(project.getId(), null, false, DEFAULT_SORT))
                    .extracting(Issue::getName)
                    .containsExactlyElementsOf(issueRepository
                            .findByProjectIdAndArchivedAtIsNullOrderByCreatedAtAsc(project.getId())
                            .stream().map(Issue::getName).toList());
        }

        @Test
        @DisplayName("filters by priority")
        void filtersByPriority() {
            Project project = persistedProject("Apollo");
            issueRepository.saveAndFlush(Issue.create(project.getId(), "Critical issue", ProjectPriority.CRITICAL));
            issueRepository.saveAndFlush(Issue.create(project.getId(), "Low issue", ProjectPriority.LOW));
            entityManager.clear();

            assertThat(issueRepository.findByFilters(project.getId(), ProjectPriority.CRITICAL, false, DEFAULT_SORT))
                    .extracting(Issue::getName)
                    .containsExactly("Critical issue");
        }

        @Test
        @DisplayName("archived=true returns only archived issues")
        void archivedTrueReturnsOnlyArchived() {
            Project project = persistedProject("Apollo");
            issueRepository.saveAndFlush(Issue.create(project.getId(), "Active", ProjectPriority.CRITICAL));
            Issue archived = issueRepository.saveAndFlush(
                    Issue.create(project.getId(), "Archived", ProjectPriority.CRITICAL));
            archived.archive(owner);
            issueRepository.saveAndFlush(archived);
            entityManager.clear();

            assertThat(issueRepository.findByFilters(project.getId(), null, true, DEFAULT_SORT))
                    .extracting(Issue::getName)
                    .containsExactly("Archived");
        }

        @Test
        @DisplayName("sorts by name descending, per docs/project/15-LIST-SPEC.md")
        void sortsByRequestedFieldAndDirection() {
            Project project = persistedProject("Apollo");
            issueRepository.saveAndFlush(Issue.create(project.getId(), "Alpha", ProjectPriority.CRITICAL));
            issueRepository.saveAndFlush(Issue.create(project.getId(), "Beta", ProjectPriority.CRITICAL));
            entityManager.clear();

            assertThat(issueRepository.findByFilters(project.getId(), null, false,
                            Sort.by(Sort.Direction.DESC, "name")))
                    .extracting(Issue::getName)
                    .containsExactly("Beta", "Alpha");
        }

        @Test
        @DisplayName("countActiveByPriority groups active issues by priority, excluding archived")
        void countsActiveIssuesByPriority() {
            Project project = persistedProject("Apollo");
            issueRepository.saveAndFlush(Issue.create(project.getId(), "One", ProjectPriority.CRITICAL));
            issueRepository.saveAndFlush(Issue.create(project.getId(), "Two", ProjectPriority.CRITICAL));
            Issue archived = issueRepository.saveAndFlush(Issue.create(project.getId(), "Archived", ProjectPriority.LOW));
            archived.archive(owner);
            issueRepository.saveAndFlush(archived);
            entityManager.clear();

            var counts = issueRepository.countActiveByPriority(project.getId()).stream()
                    .collect(java.util.stream.Collectors.toMap(IssueRepository.PriorityCount::getPriority,
                            IssueRepository.PriorityCount::getTotal));

            assertThat(counts.get(ProjectPriority.CRITICAL)).isEqualTo(2L);
            assertThat(counts).doesNotContainKey(ProjectPriority.LOW);
        }

        @Test
        @DisplayName("countByProjectIdAndArchivedAtIsNull counts active issues, excluding archived")
        void countsActiveIssues() {
            Project project = persistedProject("Apollo");
            issueRepository.saveAndFlush(Issue.create(project.getId(), "One", ProjectPriority.CRITICAL));
            issueRepository.saveAndFlush(Issue.create(project.getId(), "Two", ProjectPriority.CRITICAL));
            Issue archived = issueRepository.saveAndFlush(Issue.create(project.getId(), "Archived", ProjectPriority.LOW));
            archived.archive(owner);
            issueRepository.saveAndFlush(archived);
            entityManager.clear();

            assertThat(issueRepository.countByProjectIdAndArchivedAtIsNull(project.getId())).isEqualTo(2L);
        }
    }
}
