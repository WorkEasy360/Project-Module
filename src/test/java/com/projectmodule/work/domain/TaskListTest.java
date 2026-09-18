package com.projectmodule.work.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.projectmodule.common.exception.BusinessRuleViolationException;
import com.projectmodule.common.exception.ValidationException;
import com.projectmodule.common.identity.ExternalUserId;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class TaskListTest {

    private static final UUID PROJECT_ID = UUID.randomUUID();
    private static final UUID PHASE_ID = UUID.randomUUID();
    private static final ExternalUserId ACTOR = ExternalUserId.of(UUID.randomUUID());

    private static TaskList newTaskList() {
        return TaskList.create(PROJECT_ID, PHASE_ID, "Backend work");
    }

    @Nested
    class Creation {

        @Test
        @DisplayName("starts unarchived, belonging to the given project and phase")
        void startsUnarchived() {
            TaskList taskList = newTaskList();

            assertThat(taskList.isArchived()).isFalse();
            assertThat(taskList.getProjectId()).isEqualTo(PROJECT_ID);
            assertThat(taskList.getPhaseId()).contains(PHASE_ID);
        }

        @Test
        @DisplayName("accepts a null phase - a task list may belong directly to the project")
        void acceptsNullPhase() {
            TaskList taskList = TaskList.create(PROJECT_ID, null, "No phase");

            assertThat(taskList.getPhaseId()).isEmpty();
        }

        @Test
        @DisplayName("rejects a blank name")
        void rejectsBlankName() {
            assertThatThrownBy(() -> TaskList.create(PROJECT_ID, PHASE_ID, "   "))
                    .isInstanceOf(ValidationException.class);
        }
    }

    @Nested
    class Edits {

        @Test
        @DisplayName("renames, describes and reassigns phase")
        void appliesEdits() {
            TaskList taskList = newTaskList();
            UUID newPhase = UUID.randomUUID();

            taskList.rename("Renamed");
            taskList.describe("New description");
            taskList.reassignPhase(newPhase);

            assertThat(taskList.getName()).isEqualTo("Renamed");
            assertThat(taskList.getDescription()).contains("New description");
            assertThat(taskList.getPhaseId()).contains(newPhase);
        }
    }

    @Nested
    class Archiving {

        @Test
        @DisplayName("archiving records who did it")
        void archiveRecordsActor() {
            TaskList taskList = newTaskList();

            taskList.archive(ACTOR);

            assertThat(taskList.isArchived()).isTrue();
            assertThat(taskList.getArchivedBy()).contains(ACTOR);
        }

        @Test
        @DisplayName("refuses to archive twice")
        void refusesDoubleArchive() {
            TaskList taskList = newTaskList();
            taskList.archive(ACTOR);

            assertThatThrownBy(() -> taskList.archive(ACTOR))
                    .isInstanceOf(BusinessRuleViolationException.class);
        }

        @Test
        @DisplayName("an archived task list is read-only")
        void archivedTaskListRejectsEdits() {
            TaskList taskList = newTaskList();
            taskList.archive(ACTOR);

            assertThatThrownBy(() -> taskList.rename("New name"))
                    .isInstanceOf(BusinessRuleViolationException.class);
        }
    }
}
