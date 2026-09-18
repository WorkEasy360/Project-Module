package com.projectmodule.work.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.projectmodule.common.identity.ExternalUserId;
import com.projectmodule.common.identity.OrganizationId;
import com.projectmodule.project.domain.Project;
import com.projectmodule.project.domain.ProjectPriority;
import com.projectmodule.project.infrastructure.ProjectRepository;
import com.projectmodule.work.domain.Dependency;
import com.projectmodule.work.domain.Task;
import java.util.Set;
import java.util.UUID;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.BeforeEach;
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
 * Repository tests for Dependency, run against a real PostgreSQL 17 instance — the same
 * conventions {@code TaskPersistenceTest} and {@code WorkPersistenceTest} already establish.
 * Starting this context proves the entity mapping matches the {@code V4} migration, including
 * its database-level {@code CHECK}/{@code UNIQUE} constraints.
 *
 * <p>Skipped when {@code PROJECTMODULE_TEST_DB_URL} is not set.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@EnabledIfEnvironmentVariable(named = "PROJECTMODULE_TEST_DB_URL", matches = ".+")
class DependencyPersistenceTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private ProjectRepository projectRepository;

    @Autowired
    private TaskRepository taskRepository;

    @Autowired
    private DependencyRepository dependencyRepository;

    private final OrganizationId organization = OrganizationId.of(UUID.randomUUID());
    private final ExternalUserId owner = ExternalUserId.of(UUID.randomUUID());

    private Task dependentTask;
    private Task prerequisiteTask;

    @BeforeEach
    void setUp() {
        Project project = projectRepository.saveAndFlush(
                Project.create(organization, "Apollo", owner, ProjectPriority.MEDIUM));
        dependentTask = taskRepository.saveAndFlush(Task.create(project.getId(), "Ship feature", null, null));
        prerequisiteTask = taskRepository.saveAndFlush(Task.create(project.getId(), "Design schema", null, null));
    }

    @Test
    @DisplayName("round-trips a dependency, including its broken flag")
    void roundTripsDependency() {
        Dependency dependency = Dependency.create(dependentTask.getId(), prerequisiteTask.getId());
        UUID id = dependency.getId();

        dependencyRepository.saveAndFlush(dependency);
        entityManager.clear();

        Dependency loaded = dependencyRepository.findById(id).orElseThrow();
        assertThat(loaded.getDependentTaskId()).isEqualTo(dependentTask.getId());
        assertThat(loaded.getPrerequisiteTaskId()).isEqualTo(prerequisiteTask.getId());
        assertThat(loaded.isBroken()).isFalse();
    }

    @Test
    @DisplayName("soft-archived dependencies are still stored, with their archive detail")
    void softArchiveRetainsRow() {
        Dependency dependency = dependencyRepository.saveAndFlush(
                Dependency.create(dependentTask.getId(), prerequisiteTask.getId()));

        dependency.archive(owner);
        dependencyRepository.saveAndFlush(dependency);
        entityManager.clear();

        Dependency reloaded = dependencyRepository.findById(dependency.getId()).orElseThrow();
        assertThat(reloaded.isArchived()).isTrue();
        assertThat(dependencyRepository.findByIdAndArchivedAtIsNull(dependency.getId())).isEmpty();
    }

    @Test
    @DisplayName("cannot reference a dependent task that does not exist")
    void enforcesDependentTaskForeignKey() {
        Dependency dependency = Dependency.create(UUID.randomUUID(), prerequisiteTask.getId());

        assertThatThrownBy(() -> dependencyRepository.saveAndFlush(dependency))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("cannot reference a prerequisite task that does not exist")
    void enforcesPrerequisiteTaskForeignKey() {
        Dependency dependency = Dependency.create(dependentTask.getId(), UUID.randomUUID());

        assertThatThrownBy(() -> dependencyRepository.saveAndFlush(dependency))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("the database rejects a self-dependency even if application checks are bypassed")
    void databaseRejectsSelfDependency() {
        Dependency dependency = dependencyRepository.saveAndFlush(
                Dependency.create(dependentTask.getId(), prerequisiteTask.getId()));

        assertThatThrownBy(() -> {
            entityManager.getEntityManager()
                    .createNativeQuery("UPDATE dependencies SET prerequisite_task_id = dependent_task_id WHERE id = :id")
                    .setParameter("id", dependency.getId())
                    .executeUpdate();
            entityManager.flush();
        }).isInstanceOf(ConstraintViolationException.class);
    }

    @Test
    @DisplayName("the database rejects a duplicate ordered pair")
    void databaseRejectsDuplicatePair() {
        dependencyRepository.saveAndFlush(Dependency.create(dependentTask.getId(), prerequisiteTask.getId()));
        Dependency duplicate = Dependency.create(dependentTask.getId(), prerequisiteTask.getId());

        assertThatThrownBy(() -> dependencyRepository.saveAndFlush(duplicate))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("lists a task's active dependencies, excluding archived ones")
    void listsActiveDependencies() {
        Task otherPrerequisite = taskRepository.saveAndFlush(
                Task.create(dependentTask.getProjectId(), "Other prerequisite", null, null));
        dependencyRepository.saveAndFlush(Dependency.create(dependentTask.getId(), prerequisiteTask.getId()));
        Dependency archived = dependencyRepository.saveAndFlush(
                Dependency.create(dependentTask.getId(), otherPrerequisite.getId()));
        archived.archive(owner);
        dependencyRepository.saveAndFlush(archived);
        entityManager.clear();

        assertThat(dependencyRepository.findByDependentTaskIdAndArchivedAtIsNullOrderByCreatedAtAsc(dependentTask.getId()))
                .extracting(Dependency::getPrerequisiteTaskId)
                .containsExactly(prerequisiteTask.getId());
    }

    /** Coverage for {@code docs/project/23-DEPENDENCY-ANALYSIS-SPEC.md} §4, against real PostgreSQL. */
    @Test
    @DisplayName("finds every active dependency touching a set of task ids, excluding archived ones")
    void findsDependenciesTouchingTaskIdSet() {
        Task unrelatedTask = taskRepository.saveAndFlush(
                Task.create(dependentTask.getProjectId(), "Unrelated", null, null));
        Task unrelatedPrerequisite = taskRepository.saveAndFlush(
                Task.create(dependentTask.getProjectId(), "Unrelated prerequisite", null, null));
        dependencyRepository.saveAndFlush(Dependency.create(dependentTask.getId(), prerequisiteTask.getId()));
        Dependency archived = dependencyRepository.saveAndFlush(
                Dependency.create(dependentTask.getId(), unrelatedPrerequisite.getId()));
        archived.archive(owner);
        dependencyRepository.saveAndFlush(archived);
        dependencyRepository.saveAndFlush(Dependency.create(unrelatedTask.getId(), unrelatedPrerequisite.getId()));
        entityManager.clear();

        assertThat(dependencyRepository.findByDependentTaskIdInAndArchivedAtIsNull(Set.of(dependentTask.getId())))
                .extracting(Dependency::getPrerequisiteTaskId)
                .containsExactly(prerequisiteTask.getId());
    }
}
