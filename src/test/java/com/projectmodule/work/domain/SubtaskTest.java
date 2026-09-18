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

class SubtaskTest {

    private static final UUID TASK_ID = UUID.randomUUID();
    private static final ExternalUserId ACTOR = ExternalUserId.of(UUID.randomUUID());

    private static Subtask newSubtask() {
        return Subtask.create(TASK_ID, "Write unit tests");
    }

    @Nested
    class Creation {

        @Test
        @DisplayName("starts not completed, unarchived, belonging to the given task")
        void startsNotCompleted() {
            Subtask subtask = newSubtask();

            assertThat(subtask.isCompleted()).isFalse();
            assertThat(subtask.isArchived()).isFalse();
            assertThat(subtask.getTaskId()).isEqualTo(TASK_ID);
        }

        @Test
        @DisplayName("rejects a blank name")
        void rejectsBlankName() {
            assertThatThrownBy(() -> Subtask.create(TASK_ID, "  "))
                    .isInstanceOf(ValidationException.class);
        }
    }

    @Nested
    class Completion {

        @Test
        @DisplayName("completing and reopening are freely reversible")
        void completionIsReversible() {
            Subtask subtask = newSubtask();

            subtask.complete();
            assertThat(subtask.isCompleted()).isTrue();

            subtask.reopen();
            assertThat(subtask.isCompleted()).isFalse();
        }
    }

    @Nested
    class Archiving {

        @Test
        @DisplayName("archiving records who did it")
        void archiveRecordsActor() {
            Subtask subtask = newSubtask();

            subtask.archive(ACTOR);

            assertThat(subtask.isArchived()).isTrue();
            assertThat(subtask.getArchivedBy()).contains(ACTOR);
        }

        @Test
        @DisplayName("refuses to archive twice")
        void refusesDoubleArchive() {
            Subtask subtask = newSubtask();
            subtask.archive(ACTOR);

            assertThatThrownBy(() -> subtask.archive(ACTOR))
                    .isInstanceOf(BusinessRuleViolationException.class);
        }

        @Test
        @DisplayName("an archived subtask is read-only")
        void archivedSubtaskRejectsEdits() {
            Subtask subtask = newSubtask();
            subtask.archive(ACTOR);

            assertThatThrownBy(() -> subtask.rename("New name"))
                    .isInstanceOf(BusinessRuleViolationException.class);
            assertThatThrownBy(subtask::complete)
                    .isInstanceOf(BusinessRuleViolationException.class);
        }
    }
}
