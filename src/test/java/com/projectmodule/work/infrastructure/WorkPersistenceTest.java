package com.projectmodule.work.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.projectmodule.common.identity.ExternalUserId;
import com.projectmodule.common.identity.OrganizationId;
import com.projectmodule.project.domain.Project;
import com.projectmodule.project.domain.ProjectPriority;
import com.projectmodule.project.infrastructure.ProjectRepository;
import com.projectmodule.work.domain.Milestone;
import com.projectmodule.work.domain.MilestoneStatus;
import com.projectmodule.work.domain.Phase;
import com.projectmodule.work.domain.TaskList;
import java.time.LocalDate;
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
import org.springframework.test.context.ActiveProfiles;

/**
 * Repository tests for Phase, Milestone and Task List, run against a real PostgreSQL 17
 * instance — the same conventions {@code ProjectPersistenceTest} already establishes. Starting
 * this context proves the entity mappings match the {@code V2} migration; Flyway validates
 * rather than generates.
 *
 * <p>Skipped when {@code PROJECTMODULE_TEST_DB_URL} is not set.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@EnabledIfEnvironmentVariable(named = "PROJECTMODULE_TEST_DB_URL", matches = ".+")
class WorkPersistenceTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private ProjectRepository projectRepository;

    @Autowired
    private PhaseRepository phaseRepository;

    @Autowired
    private MilestoneRepository milestoneRepository;

    @Autowired
    private TaskListRepository taskListRepository;

    private final OrganizationId organization = OrganizationId.of(UUID.randomUUID());
    private final ExternalUserId owner = ExternalUserId.of(UUID.randomUUID());

    private Project persistedProject(String name) {
        Project project = Project.create(organization, name, owner, ProjectPriority.MEDIUM);
        return projectRepository.saveAndFlush(project);
    }

    @Nested
    class PhasePersistence {

        @Test
        @DisplayName("round-trips a phase, including the application-generated identifier")
        void roundTripsPhase() {
            Project project = persistedProject("Apollo");
            Phase phase = Phase.create(project.getId(), "Discovery");
            phase.describe("Explore the problem space");
            phase.schedule(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 3, 1));
            UUID id = phase.getId();

            phaseRepository.saveAndFlush(phase);
            entityManager.clear();

            Phase loaded = phaseRepository.findById(id).orElseThrow();
            assertThat(loaded.getName()).isEqualTo("Discovery");
            assertThat(loaded.getProjectId()).isEqualTo(project.getId());
            assertThat(loaded.getDescription()).contains("Explore the problem space");
            assertThat(loaded.getStartDate()).contains(LocalDate.of(2026, 1, 1));
            assertThat(loaded.getId().version()).isEqualTo(7);
        }

        @Test
        @DisplayName("soft-archived phases are still stored, with their archive detail")
        void softArchiveRetainsRow() {
            Project project = persistedProject("Apollo");
            Phase phase = Phase.create(project.getId(), "Discovery");
            phaseRepository.saveAndFlush(phase);

            phase.archive(owner);
            phaseRepository.saveAndFlush(phase);
            entityManager.clear();

            Phase reloaded = phaseRepository.findById(phase.getId()).orElseThrow();
            assertThat(reloaded.isArchived()).isTrue();
            assertThat(reloaded.getArchivedBy()).contains(owner);
            assertThat(phaseRepository.findByIdAndArchivedAtIsNull(phase.getId())).isEmpty();
        }

        @Test
        @DisplayName("cannot reference a project that does not exist")
        void enforcesProjectForeignKey() {
            Phase phase = Phase.create(UUID.randomUUID(), "Orphan");

            assertThatThrownBy(() -> phaseRepository.saveAndFlush(phase))
                    .isInstanceOf(DataIntegrityViolationException.class);
        }

        @Test
        @DisplayName("the database rejects a blank name")
        void databaseRejectsBlankName() {
            Project project = persistedProject("Apollo");
            Phase phase = phaseRepository.saveAndFlush(Phase.create(project.getId(), "Discovery"));

            assertThatThrownBy(() -> {
                entityManager.getEntityManager()
                        .createNativeQuery("UPDATE phases SET name = '   ' WHERE id = :id")
                        .setParameter("id", phase.getId())
                        .executeUpdate();
                entityManager.flush();
            }).isInstanceOf(ConstraintViolationException.class);
        }

        @Test
        @DisplayName("lists a project's active phases, excluding archived ones")
        void listsActivePhases() {
            Project project = persistedProject("Apollo");
            phaseRepository.saveAndFlush(Phase.create(project.getId(), "Discovery"));
            Phase archived = phaseRepository.saveAndFlush(Phase.create(project.getId(), "Legacy"));
            archived.archive(owner);
            phaseRepository.saveAndFlush(archived);
            entityManager.clear();

            assertThat(phaseRepository.findByProjectIdAndArchivedAtIsNullOrderByCreatedAtAsc(project.getId()))
                    .extracting(Phase::getName)
                    .containsExactly("Discovery");
        }
    }

    @Nested
    class MilestonePersistence {

        @Test
        @DisplayName("round-trips a milestone, including its status")
        void roundTripsMilestone() {
            Project project = persistedProject("Apollo");
            Phase phase = phaseRepository.saveAndFlush(Phase.create(project.getId(), "Discovery"));
            Milestone milestone = Milestone.create(
                    project.getId(), phase.getId(), "Beta launch", LocalDate.of(2026, 3, 1));
            UUID id = milestone.getId();

            milestoneRepository.saveAndFlush(milestone);
            entityManager.clear();

            Milestone loaded = milestoneRepository.findById(id).orElseThrow();
            assertThat(loaded.getName()).isEqualTo("Beta launch");
            assertThat(loaded.getPhaseId()).contains(phase.getId());
            assertThat(loaded.getStatus()).isEqualTo(MilestoneStatus.PENDING);
        }

        @Test
        @DisplayName("a milestone may be persisted with no phase at all")
        void persistsWithoutPhase() {
            Project project = persistedProject("Apollo");
            Milestone milestone = Milestone.create(project.getId(), null, "No phase", null);

            milestoneRepository.saveAndFlush(milestone);
            entityManager.clear();

            assertThat(milestoneRepository.findById(milestone.getId()).orElseThrow().getPhaseId())
                    .isEmpty();
        }

        @Test
        @DisplayName("status transitions persist correctly")
        void persistsStatusTransition() {
            Project project = persistedProject("Apollo");
            Milestone milestone = milestoneRepository.saveAndFlush(
                    Milestone.create(project.getId(), null, "Beta", null));

            milestone.complete();
            milestoneRepository.saveAndFlush(milestone);
            entityManager.clear();

            assertThat(milestoneRepository.findById(milestone.getId()).orElseThrow().getStatus())
                    .isEqualTo(MilestoneStatus.COMPLETED);
        }

        @Test
        @DisplayName("removing a phase clears phase_id on its milestones rather than blocking or cascading")
        void phaseRemovalSetsNullOnMilestones() {
            Project project = persistedProject("Apollo");
            Phase phase = phaseRepository.saveAndFlush(Phase.create(project.getId(), "Discovery"));
            Milestone milestone = milestoneRepository.saveAndFlush(
                    Milestone.create(project.getId(), phase.getId(), "Beta", null));

            entityManager.getEntityManager()
                    .createNativeQuery("DELETE FROM phases WHERE id = :id")
                    .setParameter("id", phase.getId())
                    .executeUpdate();
            entityManager.flush();
            entityManager.clear();

            assertThat(milestoneRepository.findById(milestone.getId()).orElseThrow().getPhaseId())
                    .isEmpty();
        }

        @Test
        @DisplayName("the database rejects a status the application does not define")
        void databaseRejectsUnknownStatus() {
            Project project = persistedProject("Apollo");
            Milestone milestone = milestoneRepository.saveAndFlush(
                    Milestone.create(project.getId(), null, "Beta", null));

            assertThatThrownBy(() -> {
                entityManager.getEntityManager()
                        .createNativeQuery("UPDATE milestones SET status = 'NOT_A_STATUS' WHERE id = :id")
                        .setParameter("id", milestone.getId())
                        .executeUpdate();
                entityManager.flush();
            }).isInstanceOf(ConstraintViolationException.class);
        }
    }

    @Nested
    class TaskListPersistence {

        @Test
        @DisplayName("round-trips a task list")
        void roundTripsTaskList() {
            Project project = persistedProject("Apollo");
            Phase phase = phaseRepository.saveAndFlush(Phase.create(project.getId(), "Discovery"));
            TaskList taskList = TaskList.create(project.getId(), phase.getId(), "Backend work");
            UUID id = taskList.getId();

            taskListRepository.saveAndFlush(taskList);
            entityManager.clear();

            TaskList loaded = taskListRepository.findById(id).orElseThrow();
            assertThat(loaded.getName()).isEqualTo("Backend work");
            assertThat(loaded.getPhaseId()).contains(phase.getId());
        }

        @Test
        @DisplayName("a task list may be persisted with no phase at all")
        void persistsWithoutPhase() {
            Project project = persistedProject("Apollo");
            TaskList taskList = TaskList.create(project.getId(), null, "No phase");

            taskListRepository.saveAndFlush(taskList);
            entityManager.clear();

            assertThat(taskListRepository.findById(taskList.getId()).orElseThrow().getPhaseId())
                    .isEmpty();
        }

        @Test
        @DisplayName("lists a project's active task lists, excluding archived ones")
        void listsActiveTaskLists() {
            Project project = persistedProject("Apollo");
            taskListRepository.saveAndFlush(TaskList.create(project.getId(), null, "Backend"));
            TaskList archived = taskListRepository.saveAndFlush(
                    TaskList.create(project.getId(), null, "Legacy"));
            archived.archive(owner);
            taskListRepository.saveAndFlush(archived);
            entityManager.clear();

            assertThat(taskListRepository.findByProjectIdAndArchivedAtIsNullOrderByCreatedAtAsc(project.getId()))
                    .extracting(TaskList::getName)
                    .containsExactly("Backend");
        }
    }

    /**
     * Repository-level coverage for the new query methods
     * {@code docs/project/17-CALENDAR-SPEC.md} and {@code docs/project/22-DELAY-DETECTION-SPEC.md}
     * add to {@code PhaseRepository}/{@code MilestoneRepository}, against real PostgreSQL.
     */
    @Nested
    class CalendarAndDelayQueries {

        @Test
        @DisplayName("PhaseRepository: only phases with both dates set are returned, ordered by startDate")
        void phaseCalendarQueryExcludesUnscheduledPhases() {
            Project project = persistedProject("Apollo");
            Phase scheduled = Phase.create(project.getId(), "Scheduled");
            scheduled.schedule(LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 10));
            phaseRepository.saveAndFlush(scheduled);
            phaseRepository.saveAndFlush(Phase.create(project.getId(), "Unscheduled"));
            entityManager.clear();

            assertThat(phaseRepository
                            .findByProjectIdAndStartDateIsNotNullAndEndDateIsNotNullAndArchivedAtIsNullOrderByStartDateAsc(
                                    project.getId()))
                    .extracting(Phase::getName)
                    .containsExactly("Scheduled");
        }

        @Test
        @DisplayName("PhaseRepository: returns phases whose endDate has already passed")
        void phaseDelayQueryFindsPastEndDate() {
            Project project = persistedProject("Apollo");
            Phase overdue = Phase.create(project.getId(), "Overdue phase");
            overdue.schedule(LocalDate.of(2020, 1, 1), LocalDate.of(2020, 1, 10));
            phaseRepository.saveAndFlush(overdue);
            Phase future = Phase.create(project.getId(), "Future phase");
            future.schedule(LocalDate.of(2099, 1, 1), LocalDate.of(2099, 1, 10));
            phaseRepository.saveAndFlush(future);
            entityManager.clear();

            assertThat(phaseRepository.findByProjectIdAndEndDateBeforeAndArchivedAtIsNullOrderByEndDateAsc(
                            project.getId(), LocalDate.now()))
                    .extracting(Phase::getName)
                    .containsExactly("Overdue phase");
        }

        @Test
        @DisplayName("MilestoneRepository: only milestones with a dueDate are returned, ordered by dueDate")
        void milestoneCalendarQueryExcludesUndatedMilestones() {
            Project project = persistedProject("Apollo");
            milestoneRepository.saveAndFlush(
                    Milestone.create(project.getId(), null, "Dated", LocalDate.of(2026, 3, 1)));
            milestoneRepository.saveAndFlush(Milestone.create(project.getId(), null, "Undated", null));
            entityManager.clear();

            assertThat(milestoneRepository.findByProjectIdAndDueDateIsNotNullAndArchivedAtIsNullOrderByDueDateAsc(
                            project.getId()))
                    .extracting(Milestone::getName)
                    .containsExactly("Dated");
        }

        @Test
        @DisplayName("MilestoneRepository: excludes completed milestones from the delay query")
        void milestoneDelayQueryExcludesCompleted() {
            Project project = persistedProject("Apollo");
            Milestone overdue = Milestone.create(project.getId(), null, "Overdue", LocalDate.of(2020, 1, 1));
            milestoneRepository.saveAndFlush(overdue);
            Milestone completed = Milestone.create(project.getId(), null, "Completed but overdue", LocalDate.of(2020, 1, 1));
            completed.complete();
            milestoneRepository.saveAndFlush(completed);
            entityManager.clear();

            assertThat(milestoneRepository
                            .findByProjectIdAndDueDateBeforeAndStatusNotAndArchivedAtIsNullOrderByDueDateAsc(
                                    project.getId(), LocalDate.now(), MilestoneStatus.COMPLETED))
                    .extracting(Milestone::getName)
                    .containsExactly("Overdue");
        }
    }
}
