package com.projectmodule.dashboard.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import com.projectmodule.common.identity.ExternalUserId;
import com.projectmodule.common.identity.OrganizationId;
import com.projectmodule.project.domain.Project;
import com.projectmodule.project.domain.ProjectPriority;
import com.projectmodule.project.domain.ProjectStatus;
import com.projectmodule.project.infrastructure.ProjectRepository;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.test.context.ActiveProfiles;

/**
 * Proves the dashboard's {@code GROUP BY} queries against a real PostgreSQL 17 instance — the
 * one query in the module written as JPQL rather than a derived method name, so it is the one
 * most worth checking against a real driver rather than mocks.
 *
 * <p>Every test uses a fresh random {@code organizationId}, so it can never see data committed
 * by another test's organization, in this class or any other — the same isolation approach
 * {@code ProjectPersistenceTest} uses.
 *
 * <p>Skipped when {@code PROJECTMODULE_TEST_DB_URL} is not set, matching every other real-database
 * test in this module.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@EnabledIfEnvironmentVariable(named = "PROJECTMODULE_TEST_DB_URL", matches = ".+")
class DashboardQueryTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private ProjectRepository projectRepository;

    private final OrganizationId organization = OrganizationId.of(UUID.randomUUID());
    private final ExternalUserId owner = ExternalUserId.of(UUID.randomUUID());

    private Project persistedProject(String name, ProjectStatus status, ProjectPriority priority) {
        Project project = Project.create(organization, name, owner, priority);
        project.changeStatus(status);
        return projectRepository.saveAndFlush(project);
    }

    @Test
    @DisplayName("counts active projects by status, scoped to one organization")
    void countsActiveByStatus() {
        persistedProject("Planning A", ProjectStatus.PLANNING, ProjectPriority.LOW);
        persistedProject("Active A", ProjectStatus.ACTIVE, ProjectPriority.LOW);
        persistedProject("Active B", ProjectStatus.ACTIVE, ProjectPriority.LOW);
        Project archived = persistedProject("Archived A", ProjectStatus.ACTIVE, ProjectPriority.LOW);
        archived.archive(owner);
        projectRepository.saveAndFlush(archived);

        OrganizationId otherOrg = OrganizationId.of(UUID.randomUUID());
        projectRepository.saveAndFlush(
                Project.create(otherOrg, "Elsewhere", owner, ProjectPriority.LOW));
        entityManager.clear();

        var counts = projectRepository.countActiveByStatus(organization.value());

        assertThat(counts)
                .extracting(ProjectRepository.StatusCount::getStatus,
                        ProjectRepository.StatusCount::getTotal)
                .containsExactlyInAnyOrder(
                        org.assertj.core.groups.Tuple.tuple(ProjectStatus.PLANNING, 1L),
                        org.assertj.core.groups.Tuple.tuple(ProjectStatus.ACTIVE, 2L));
    }

    @Test
    @DisplayName("counts active projects by priority, scoped to one organization")
    void countsActiveByPriority() {
        persistedProject("Low A", ProjectStatus.PLANNING, ProjectPriority.LOW);
        persistedProject("Critical A", ProjectStatus.PLANNING, ProjectPriority.CRITICAL);
        persistedProject("Critical B", ProjectStatus.PLANNING, ProjectPriority.CRITICAL);
        entityManager.clear();

        var counts = projectRepository.countActiveByPriority(organization.value());

        assertThat(counts)
                .extracting(ProjectRepository.PriorityCount::getPriority,
                        ProjectRepository.PriorityCount::getTotal)
                .containsExactlyInAnyOrder(
                        org.assertj.core.groups.Tuple.tuple(ProjectPriority.LOW, 1L),
                        org.assertj.core.groups.Tuple.tuple(ProjectPriority.CRITICAL, 2L));
    }

    @Test
    @DisplayName("counts archived projects separately from active ones")
    void countsArchivedSeparately() {
        persistedProject("Stays active", ProjectStatus.ACTIVE, ProjectPriority.MEDIUM);
        Project archived = persistedProject("Gets archived", ProjectStatus.ACTIVE, ProjectPriority.MEDIUM);
        archived.archive(owner);
        projectRepository.saveAndFlush(archived);
        entityManager.clear();

        assertThat(projectRepository.countByOrganizationIdAndArchivedAtIsNull(organization.value()))
                .isEqualTo(1L);
        assertThat(projectRepository.countByOrganizationIdAndArchivedAtIsNotNull(organization.value()))
                .isEqualTo(1L);
    }

    @Test
    @DisplayName("an organization with no projects reports empty breakdowns")
    void emptyOrganizationReportsNoRows() {
        OrganizationId emptyOrg = OrganizationId.of(UUID.randomUUID());

        assertThat(projectRepository.countActiveByStatus(emptyOrg.value())).isEmpty();
        assertThat(projectRepository.countActiveByPriority(emptyOrg.value())).isEmpty();
        assertThat(projectRepository.countByOrganizationIdAndArchivedAtIsNull(emptyOrg.value()))
                .isZero();
    }
}
