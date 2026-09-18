package com.projectmodule.work.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.projectmodule.common.exception.BusinessRuleViolationException;
import com.projectmodule.common.exception.ValidationException;
import com.projectmodule.common.identity.ExternalUserId;
import com.projectmodule.work.domain.event.TaskAssigned;
import com.projectmodule.work.domain.event.TaskBlocked;
import com.projectmodule.work.domain.event.TaskCompleted;
import com.projectmodule.work.domain.event.TaskCreated;
import com.projectmodule.work.domain.event.TaskDomainEvent;
import com.projectmodule.work.domain.event.TaskOverdue;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class TaskTest {

    private static final UUID PROJECT_ID = UUID.randomUUID();
    private static final ExternalUserId ASSIGNEE = ExternalUserId.of(UUID.randomUUID());
    private static final ExternalUserId ACTOR = ExternalUserId.of(UUID.randomUUID());

    private static Task newTask() {
        return Task.create(PROJECT_ID, "Implement login", LocalDate.of(2026, 3, 1), null);
    }

    @Nested
    class Creation {

        @Test
        @DisplayName("starts TODO, unarchived, unassigned")
        void startsTodo() {
            Task task = newTask();

            assertThat(task.getStatus()).isEqualTo(TaskStatus.TODO);
            assertThat(task.isArchived()).isFalse();
            assertThat(task.getAssigneeId()).isEmpty();
            assertThat(task.getProjectId()).isEqualTo(PROJECT_ID);
        }

        @Test
        @DisplayName("accepts an initial assignee without raising task.assigned")
        void initialAssigneeRaisesNoSeparateEvent() {
            Task task = Task.create(PROJECT_ID, "Implement login", null, ASSIGNEE);

            assertThat(task.getAssigneeId()).contains(ASSIGNEE);
            List<TaskDomainEvent> events = task.pullDomainEvents();
            assertThat(events).hasSize(1);
            assertThat(events.get(0)).isInstanceOf(TaskCreated.class);
        }

        @Test
        @DisplayName("rejects a blank name")
        void rejectsBlankName() {
            assertThatThrownBy(() -> Task.create(PROJECT_ID, "   ", null, null))
                    .isInstanceOf(ValidationException.class);
        }

        @Test
        @DisplayName("raises exactly one TaskCreated event")
        void raisesCreatedEvent() {
            Task task = newTask();

            List<TaskDomainEvent> events = task.pullDomainEvents();

            assertThat(events).hasSize(1);
            assertThat(events.get(0)).isInstanceOf(TaskCreated.class);
            assertThat(events.get(0).taskId()).isEqualTo(task.getId());
        }
    }

    @Nested
    class PlainEdits {

        @Test
        @DisplayName("recordUpdated raises exactly one TaskUpdated event, regardless of how many fields changed")
        void recordUpdatedRaisesOneEvent() {
            Task task = newTask();
            task.pullDomainEvents();

            task.rename("Renamed");
            task.describe("New description");
            task.reschedule(LocalDate.of(2026, 4, 1));
            task.recordUpdated();

            List<TaskDomainEvent> events = task.pullDomainEvents();
            assertThat(events).hasSize(1);
            assertThat(events.get(0)).isInstanceOf(com.projectmodule.work.domain.event.TaskUpdated.class);
        }
    }

    @Nested
    class Assignment {

        @Test
        @DisplayName("assigning raises exactly one TaskAssigned event")
        void assignRaisesEvent() {
            Task task = newTask();
            task.pullDomainEvents();

            task.assign(ASSIGNEE);

            assertThat(task.getAssigneeId()).contains(ASSIGNEE);
            List<TaskDomainEvent> events = task.pullDomainEvents();
            assertThat(events).hasSize(1);
            assertThat(events.get(0)).isInstanceOf(TaskAssigned.class);
        }

        @Test
        @DisplayName("reassigning to the same person still raises the event - reassignment is repeatable")
        void reassigningSamePersonStillRaisesEvent() {
            Task task = Task.create(PROJECT_ID, "Task", null, ASSIGNEE);
            task.pullDomainEvents();

            task.assign(ASSIGNEE);

            assertThat(task.pullDomainEvents()).hasSize(1);
        }
    }

    @Nested
    class StatusTransitions {

        @Test
        @DisplayName("completing raises exactly one TaskCompleted event")
        void completeRaisesEvent() {
            Task task = newTask();
            task.pullDomainEvents();

            task.complete();

            assertThat(task.getStatus()).isEqualTo(TaskStatus.COMPLETED);
            List<TaskDomainEvent> events = task.pullDomainEvents();
            assertThat(events).hasSize(1);
            assertThat(events.get(0)).isInstanceOf(TaskCompleted.class);
        }

        @Test
        @DisplayName("marking blocked raises exactly one TaskBlocked event")
        void markBlockedRaisesEvent() {
            Task task = newTask();
            task.pullDomainEvents();

            task.markBlocked();

            assertThat(task.getStatus()).isEqualTo(TaskStatus.BLOCKED);
            List<TaskDomainEvent> events = task.pullDomainEvents();
            assertThat(events).hasSize(1);
            assertThat(events.get(0)).isInstanceOf(TaskBlocked.class);
        }

        @Test
        @DisplayName("marking overdue raises exactly one TaskOverdue event")
        void markOverdueRaisesEvent() {
            Task task = newTask();
            task.pullDomainEvents();

            task.markOverdue();

            assertThat(task.getStatus()).isEqualTo(TaskStatus.OVERDUE);
            List<TaskDomainEvent> events = task.pullDomainEvents();
            assertThat(events).hasSize(1);
            assertThat(events.get(0)).isInstanceOf(TaskOverdue.class);
        }

        @Test
        @DisplayName("refuses to complete twice")
        void refusesDoubleComplete() {
            Task task = newTask();
            task.complete();

            assertThatThrownBy(task::complete).isInstanceOf(BusinessRuleViolationException.class);
        }

        @Test
        @DisplayName("refuses to block a completed task")
        void refusesBlockAfterCompleted() {
            Task task = newTask();
            task.complete();

            assertThatThrownBy(task::markBlocked).isInstanceOf(BusinessRuleViolationException.class);
        }

        @Test
        @DisplayName("refuses to mark a completed task overdue")
        void refusesOverdueAfterCompleted() {
            Task task = newTask();
            task.complete();

            assertThatThrownBy(task::markOverdue).isInstanceOf(BusinessRuleViolationException.class);
        }

        @Test
        @DisplayName("a blocked task can still be completed")
        void blockedTaskCanBeCompleted() {
            Task task = newTask();
            task.markBlocked();
            task.pullDomainEvents();

            task.complete();

            assertThat(task.getStatus()).isEqualTo(TaskStatus.COMPLETED);
        }
    }

    @Nested
    class Archiving {

        @Test
        @DisplayName("archiving records who did it and raises no event")
        void archiveRecordsActorAndRaisesNoEvent() {
            Task task = newTask();
            task.pullDomainEvents();

            task.archive(ACTOR);

            assertThat(task.isArchived()).isTrue();
            assertThat(task.getArchivedBy()).contains(ACTOR);
            assertThat(task.pullDomainEvents()).isEmpty();
        }

        @Test
        @DisplayName("an archived task is read-only")
        void archivedTaskRejectsEdits() {
            Task task = newTask();
            task.archive(ACTOR);

            assertThatThrownBy(() -> task.rename("New name"))
                    .isInstanceOf(BusinessRuleViolationException.class);
            assertThatThrownBy(task::complete)
                    .isInstanceOf(BusinessRuleViolationException.class);
            assertThatThrownBy(() -> task.assign(ASSIGNEE))
                    .isInstanceOf(BusinessRuleViolationException.class);
        }
    }
}
