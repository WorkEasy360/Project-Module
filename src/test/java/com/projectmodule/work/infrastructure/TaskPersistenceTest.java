package com.projectmodule.work.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.projectmodule.common.identity.ExternalUserId;
import com.projectmodule.common.identity.OrganizationId;
import com.projectmodule.project.domain.Project;
import com.projectmodule.project.domain.ProjectPriority;
import com.projectmodule.project.infrastructure.ProjectRepository;
import com.projectmodule.work.domain.Checklist;
import com.projectmodule.work.domain.Subtask;
import com.projectmodule.work.domain.Task;
import com.projectmodule.work.domain.TaskStatus;
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
import org.springframework.data.domain.Sort;
import org.springframework.test.context.ActiveProfiles;

/**
 * Repository tests for Task, Subtask and Checklist, run against a real PostgreSQL 17 instance —
 * the same conventions {@code ProjectPersistenceTest} and {@code WorkPersistenceTest} already
 * establish. Starting this context proves the entity mappings match the {@code V3} migration.
 *
 * <p>Skipped when {@code PROJECTMODULE_TEST_DB_URL} is not set.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@EnabledIfEnvironmentVariable(named = "PROJECTMODULE_TEST_DB_URL", matches = ".+")
class TaskPersistenceTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private ProjectRepository projectRepository;

    @Autowired
    private TaskRepository taskRepository;

    @Autowired
    private SubtaskRepository subtaskRepository;

    @Autowired
    private ChecklistRepository checklistRepository;

    private final OrganizationId organization = OrganizationId.of(UUID.randomUUID());
    private final ExternalUserId owner = ExternalUserId.of(UUID.randomUUID());

    private Project persistedProject(String name) {
        Project project = Project.create(organization, name, owner, ProjectPriority.MEDIUM);
        return projectRepository.saveAndFlush(project);
    }

    @Nested
    class TaskPersistence {

        @Test
        @DisplayName("round-trips a task, including its status and assignee")
        void roundTripsTask() {
            Project project = persistedProject("Apollo");
            Task task = Task.create(project.getId(), "Implement login", LocalDate.of(2026, 3, 1), owner);
            UUID id = task.getId();

            taskRepository.saveAndFlush(task);
            entityManager.clear();

            Task loaded = taskRepository.findById(id).orElseThrow();
            assertThat(loaded.getName()).isEqualTo("Implement login");
            assertThat(loaded.getProjectId()).isEqualTo(project.getId());
            assertThat(loaded.getAssigneeId()).contains(owner);
            assertThat(loaded.getStatus()).isEqualTo(TaskStatus.TODO);
            assertThat(loaded.getId().version()).isEqualTo(7);
        }

        @Test
        @DisplayName("soft-archived tasks are still stored, with their archive detail")
        void softArchiveRetainsRow() {
            Project project = persistedProject("Apollo");
            Task task = taskRepository.saveAndFlush(Task.create(project.getId(), "Implement login", null, null));

            task.archive(owner);
            taskRepository.saveAndFlush(task);
            entityManager.clear();

            Task reloaded = taskRepository.findById(task.getId()).orElseThrow();
            assertThat(reloaded.isArchived()).isTrue();
            assertThat(taskRepository.findByIdAndArchivedAtIsNull(task.getId())).isEmpty();
        }

        @Test
        @DisplayName("cannot reference a project that does not exist")
        void enforcesProjectForeignKey() {
            Task task = Task.create(UUID.randomUUID(), "Orphan", null, null);

            assertThatThrownBy(() -> taskRepository.saveAndFlush(task))
                    .isInstanceOf(DataIntegrityViolationException.class);
        }

        @Test
        @DisplayName("the database rejects a status the application does not define")
        void databaseRejectsUnknownStatus() {
            Project project = persistedProject("Apollo");
            Task task = taskRepository.saveAndFlush(Task.create(project.getId(), "Implement login", null, null));

            assertThatThrownBy(() -> {
                entityManager.getEntityManager()
                        .createNativeQuery("UPDATE tasks SET status = 'NOT_A_STATUS' WHERE id = :id")
                        .setParameter("id", task.getId())
                        .executeUpdate();
                entityManager.flush();
            }).isInstanceOf(ConstraintViolationException.class);
        }

        @Test
        @DisplayName("lists a project's active tasks, excluding archived ones")
        void listsActiveTasks() {
            Project project = persistedProject("Apollo");
            taskRepository.saveAndFlush(Task.create(project.getId(), "Implement login", null, null));
            Task archived = taskRepository.saveAndFlush(Task.create(project.getId(), "Legacy", null, null));
            archived.archive(owner);
            taskRepository.saveAndFlush(archived);
            entityManager.clear();

            assertThat(taskRepository.findByProjectIdAndArchivedAtIsNullOrderByCreatedAtAsc(project.getId()))
                    .extracting(Task::getName)
                    .containsExactly("Implement login");
        }
    }

    @Nested
    class SubtaskPersistence {

        @Test
        @DisplayName("round-trips a subtask, including its completed flag")
        void roundTripsSubtask() {
            Project project = persistedProject("Apollo");
            Task task = taskRepository.saveAndFlush(Task.create(project.getId(), "Implement login", null, null));
            Subtask subtask = Subtask.create(task.getId(), "Write unit tests");
            subtask.complete();
            UUID id = subtask.getId();

            subtaskRepository.saveAndFlush(subtask);
            entityManager.clear();

            Subtask loaded = subtaskRepository.findById(id).orElseThrow();
            assertThat(loaded.getName()).isEqualTo("Write unit tests");
            assertThat(loaded.getTaskId()).isEqualTo(task.getId());
            assertThat(loaded.isCompleted()).isTrue();
        }

        @Test
        @DisplayName("cannot reference a task that does not exist")
        void enforcesTaskForeignKey() {
            Subtask subtask = Subtask.create(UUID.randomUUID(), "Orphan");

            assertThatThrownBy(() -> subtaskRepository.saveAndFlush(subtask))
                    .isInstanceOf(DataIntegrityViolationException.class);
        }

        @Test
        @DisplayName("lists a task's active subtasks, excluding archived ones")
        void listsActiveSubtasks() {
            Project project = persistedProject("Apollo");
            Task task = taskRepository.saveAndFlush(Task.create(project.getId(), "Implement login", null, null));
            subtaskRepository.saveAndFlush(Subtask.create(task.getId(), "Write unit tests"));
            Subtask archived = subtaskRepository.saveAndFlush(Subtask.create(task.getId(), "Legacy"));
            archived.archive(owner);
            subtaskRepository.saveAndFlush(archived);
            entityManager.clear();

            assertThat(subtaskRepository.findByTaskIdAndArchivedAtIsNullOrderByCreatedAtAsc(task.getId()))
                    .extracting(Subtask::getName)
                    .containsExactly("Write unit tests");
        }
    }

    @Nested
    class ChecklistPersistence {

        @Test
        @DisplayName("round-trips a checklist line, optionally scoped to a subtask")
        void roundTripsChecklist() {
            Project project = persistedProject("Apollo");
            Task task = taskRepository.saveAndFlush(Task.create(project.getId(), "Implement login", null, null));
            Subtask subtask = subtaskRepository.saveAndFlush(Subtask.create(task.getId(), "Write unit tests"));
            Checklist checklist = Checklist.create(task.getId(), subtask.getId(), "Add password validation");
            UUID id = checklist.getId();

            checklistRepository.saveAndFlush(checklist);
            entityManager.clear();

            Checklist loaded = checklistRepository.findById(id).orElseThrow();
            assertThat(loaded.getText()).isEqualTo("Add password validation");
            assertThat(loaded.getSubtaskId()).contains(subtask.getId());
        }

        @Test
        @DisplayName("a checklist line may be persisted with no subtask at all")
        void persistsWithoutSubtask() {
            Project project = persistedProject("Apollo");
            Task task = taskRepository.saveAndFlush(Task.create(project.getId(), "Implement login", null, null));
            Checklist checklist = Checklist.create(task.getId(), null, "No subtask");

            checklistRepository.saveAndFlush(checklist);
            entityManager.clear();

            assertThat(checklistRepository.findById(checklist.getId()).orElseThrow().getSubtaskId())
                    .isEmpty();
        }

        @Test
        @DisplayName("removing a subtask clears subtask_id on its checklist lines rather than blocking or cascading")
        void subtaskRemovalSetsNullOnChecklists() {
            Project project = persistedProject("Apollo");
            Task task = taskRepository.saveAndFlush(Task.create(project.getId(), "Implement login", null, null));
            Subtask subtask = subtaskRepository.saveAndFlush(Subtask.create(task.getId(), "Write unit tests"));
            Checklist checklist = checklistRepository.saveAndFlush(
                    Checklist.create(task.getId(), subtask.getId(), "Add password validation"));

            entityManager.getEntityManager()
                    .createNativeQuery("DELETE FROM subtasks WHERE id = :id")
                    .setParameter("id", subtask.getId())
                    .executeUpdate();
            entityManager.flush();
            entityManager.clear();

            assertThat(checklistRepository.findById(checklist.getId()).orElseThrow().getSubtaskId())
                    .isEmpty();
        }

        @Test
        @DisplayName("cannot reference a task that does not exist")
        void enforcesTaskForeignKey() {
            Checklist checklist = Checklist.create(UUID.randomUUID(), null, "Orphan");

            assertThatThrownBy(() -> checklistRepository.saveAndFlush(checklist))
                    .isInstanceOf(DataIntegrityViolationException.class);
        }
    }

    /**
     * Coverage for {@code TaskRepository.search}, per {@code docs/project/13-SEARCH-SPEC.md}.
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
            taskRepository.saveAndFlush(Task.create(project.getId(), "Implement Login", null, null));
            entityManager.clear();

            assertThat(taskRepository.search(project.getId(), "LOGIN"))
                    .extracting(Task::getName)
                    .containsExactly("Implement Login");
        }

        @Test
        @DisplayName("matches a partial substring, not only a whole-word match")
        void matchesPartialSubstring() {
            Project project = persistedProject("Apollo");
            taskRepository.saveAndFlush(Task.create(project.getId(), "Implement login", null, null));
            entityManager.clear();

            assertThat(taskRepository.search(project.getId(), "ogi")).hasSize(1);
        }

        @Test
        @DisplayName("matches on name")
        void matchesOnName() {
            Project project = persistedProject("Apollo");
            taskRepository.saveAndFlush(Task.create(project.getId(), "Implement login", null, null));
            entityManager.clear();

            assertThat(taskRepository.search(project.getId(), "login")).hasSize(1);
        }

        @Test
        @DisplayName("matches on description")
        void matchesOnDescription() {
            Project project = persistedProject("Apollo");
            Task task = Task.create(project.getId(), "Unrelated name", null, null);
            task.describe("Add support for the OAuth login flow");
            taskRepository.saveAndFlush(task);
            entityManager.clear();

            assertThat(taskRepository.search(project.getId(), "login")).hasSize(1);
        }

        @Test
        @DisplayName("excludes archived tasks")
        void excludesArchivedTasks() {
            Project project = persistedProject("Apollo");
            Task task = taskRepository.saveAndFlush(Task.create(project.getId(), "Implement login", null, null));
            task.archive(owner);
            taskRepository.saveAndFlush(task);
            entityManager.clear();

            assertThat(taskRepository.search(project.getId(), "login")).isEmpty();
        }

        @Test
        @DisplayName("excludes matches belonging to a different project")
        void excludesOtherProjects() {
            Project projectA = persistedProject("Apollo");
            Project projectB = persistedProject("Artemis");
            taskRepository.saveAndFlush(Task.create(projectB.getId(), "Implement login", null, null));
            entityManager.clear();

            assertThat(taskRepository.search(projectA.getId(), "login")).isEmpty();
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
            taskRepository.saveAndFlush(Task.create(project.getId(), "Implement login", null, null));
            entityManager.clear();

            assertThat(taskRepository.findByFilters(project.getId(), null, null, null, null, false, DEFAULT_SORT))
                    .extracting(Task::getName)
                    .containsExactlyElementsOf(taskRepository
                            .findByProjectIdAndArchivedAtIsNullOrderByCreatedAtAsc(project.getId())
                            .stream().map(Task::getName).toList());
        }

        @Test
        @DisplayName("filters by status")
        void filtersByStatus() {
            Project project = persistedProject("Apollo");
            Task todo = taskRepository.saveAndFlush(Task.create(project.getId(), "Todo task", null, null));
            Task blocked = Task.create(project.getId(), "Blocked task", null, null);
            blocked.markBlocked();
            taskRepository.saveAndFlush(blocked);
            entityManager.clear();

            assertThat(taskRepository.findByFilters(
                            project.getId(), TaskStatus.BLOCKED, null, null, null, false, DEFAULT_SORT))
                    .extracting(Task::getName)
                    .containsExactly("Blocked task");
        }

        @Test
        @DisplayName("combines assigneeId and due-date range filters with AND")
        void combinesMultipleFilters() {
            Project project = persistedProject("Apollo");
            Task matching = Task.create(project.getId(), "Matches", LocalDate.of(2026, 3, 15), owner);
            Task wrongAssignee = Task.create(project.getId(), "Wrong assignee", LocalDate.of(2026, 3, 15), null);
            Task outOfRange = Task.create(project.getId(), "Out of range", LocalDate.of(2026, 12, 1), owner);
            taskRepository.saveAndFlush(matching);
            taskRepository.saveAndFlush(wrongAssignee);
            taskRepository.saveAndFlush(outOfRange);
            entityManager.clear();

            assertThat(taskRepository.findByFilters(project.getId(), null, owner.value(),
                            LocalDate.of(2026, 6, 1), LocalDate.of(2026, 1, 1), false, DEFAULT_SORT))
                    .extracting(Task::getName)
                    .containsExactly("Matches");
        }

        @Test
        @DisplayName("archived=true returns only archived tasks")
        void archivedTrueReturnsOnlyArchived() {
            Project project = persistedProject("Apollo");
            Task active = taskRepository.saveAndFlush(Task.create(project.getId(), "Active", null, null));
            Task archived = taskRepository.saveAndFlush(Task.create(project.getId(), "Archived", null, null));
            archived.archive(owner);
            taskRepository.saveAndFlush(archived);
            entityManager.clear();

            assertThat(taskRepository.findByFilters(project.getId(), null, null, null, null, true, DEFAULT_SORT))
                    .extracting(Task::getName)
                    .containsExactly("Archived");
        }

        @Test
        @DisplayName("sorts by name descending, per docs/project/15-LIST-SPEC.md")
        void sortsByRequestedFieldAndDirection() {
            Project project = persistedProject("Apollo");
            taskRepository.saveAndFlush(Task.create(project.getId(), "Alpha", null, null));
            taskRepository.saveAndFlush(Task.create(project.getId(), "Beta", null, null));
            entityManager.clear();

            assertThat(taskRepository.findByFilters(project.getId(), null, null, null, null, false,
                            Sort.by(Sort.Direction.DESC, "name")))
                    .extracting(Task::getName)
                    .containsExactly("Beta", "Alpha");
        }
    }

    /**
     * Repository-level coverage for the new query methods
     * {@code docs/project/17-CALENDAR-SPEC.md} and {@code docs/project/22-DELAY-DETECTION-SPEC.md}
     * add, against real PostgreSQL.
     */
    @Nested
    class CalendarAndDelayQueries {

        @Test
        @DisplayName("calendar query excludes tasks with no dueDate, orders by dueDate")
        void calendarQueryExcludesUndatedTasks() {
            Project project = persistedProject("Apollo");
            taskRepository.saveAndFlush(Task.create(project.getId(), "Dated", LocalDate.of(2026, 3, 1), null));
            taskRepository.saveAndFlush(Task.create(project.getId(), "Undated", null, null));
            entityManager.clear();

            assertThat(taskRepository.findByProjectIdAndDueDateIsNotNullAndArchivedAtIsNullOrderByDueDateAsc(
                            project.getId()))
                    .extracting(Task::getName)
                    .containsExactly("Dated");
        }

        @Test
        @DisplayName("delay query excludes completed tasks")
        void delayQueryExcludesCompleted() {
            Project project = persistedProject("Apollo");
            Task overdue = taskRepository.saveAndFlush(
                    Task.create(project.getId(), "Overdue", LocalDate.of(2020, 1, 1), null));
            Task completed = Task.create(project.getId(), "Completed but overdue", LocalDate.of(2020, 1, 1), null);
            completed.complete();
            taskRepository.saveAndFlush(completed);
            entityManager.clear();

            assertThat(taskRepository.findByProjectIdAndDueDateBeforeAndStatusNotAndArchivedAtIsNullOrderByDueDateAsc(
                            project.getId(), LocalDate.now(), TaskStatus.COMPLETED))
                    .extracting(Task::getName)
                    .containsExactly("Overdue");
        }

        @Test
        @DisplayName("countActiveByStatus groups active tasks by status, excluding archived")
        void countsActiveTasksByStatus() {
            Project project = persistedProject("Apollo");
            taskRepository.saveAndFlush(Task.create(project.getId(), "Todo one", null, null));
            taskRepository.saveAndFlush(Task.create(project.getId(), "Todo two", null, null));
            Task blocked = Task.create(project.getId(), "Blocked", null, null);
            blocked.markBlocked();
            taskRepository.saveAndFlush(blocked);
            Task archived = taskRepository.saveAndFlush(Task.create(project.getId(), "Archived", null, null));
            archived.archive(owner);
            taskRepository.saveAndFlush(archived);
            entityManager.clear();

            var counts = taskRepository.countActiveByStatus(project.getId()).stream()
                    .collect(java.util.stream.Collectors.toMap(TaskRepository.StatusCount::getStatus,
                            TaskRepository.StatusCount::getTotal));

            assertThat(counts.get(TaskStatus.TODO)).isEqualTo(2L);
            assertThat(counts.get(TaskStatus.BLOCKED)).isEqualTo(1L);
            assertThat(counts).doesNotContainKey(TaskStatus.COMPLETED);
        }
    }
}
