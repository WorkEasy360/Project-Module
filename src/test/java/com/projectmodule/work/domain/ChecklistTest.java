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

class ChecklistTest {

    private static final UUID TASK_ID = UUID.randomUUID();
    private static final UUID SUBTASK_ID = UUID.randomUUID();
    private static final ExternalUserId ACTOR = ExternalUserId.of(UUID.randomUUID());

    private static Checklist newChecklist() {
        return Checklist.create(TASK_ID, SUBTASK_ID, "Add password validation");
    }

    @Nested
    class Creation {

        @Test
        @DisplayName("starts unchecked, unarchived, with the given subtask")
        void startsUnchecked() {
            Checklist checklist = newChecklist();

            assertThat(checklist.isChecked()).isFalse();
            assertThat(checklist.isArchived()).isFalse();
            assertThat(checklist.getTaskId()).isEqualTo(TASK_ID);
            assertThat(checklist.getSubtaskId()).contains(SUBTASK_ID);
        }

        @Test
        @DisplayName("accepts a null subtask - a checklist line may belong directly to the task")
        void acceptsNullSubtask() {
            Checklist checklist = Checklist.create(TASK_ID, null, "No subtask");

            assertThat(checklist.getSubtaskId()).isEmpty();
        }

        @Test
        @DisplayName("rejects blank text")
        void rejectsBlankText() {
            assertThatThrownBy(() -> Checklist.create(TASK_ID, null, "   "))
                    .isInstanceOf(ValidationException.class);
        }
    }

    @Nested
    class Toggling {

        @Test
        @DisplayName("checking and unchecking are freely reversible")
        void togglingIsReversible() {
            Checklist checklist = newChecklist();

            checklist.check();
            assertThat(checklist.isChecked()).isTrue();

            checklist.uncheck();
            assertThat(checklist.isChecked()).isFalse();
        }

        @Test
        @DisplayName("reassigns to a different subtask, or clears it")
        void reassignsSubtask() {
            Checklist checklist = newChecklist();
            UUID newSubtask = UUID.randomUUID();

            checklist.reassignSubtask(newSubtask);
            assertThat(checklist.getSubtaskId()).contains(newSubtask);

            checklist.reassignSubtask(null);
            assertThat(checklist.getSubtaskId()).isEmpty();
        }
    }

    @Nested
    class Archiving {

        @Test
        @DisplayName("archiving records who did it")
        void archiveRecordsActor() {
            Checklist checklist = newChecklist();

            checklist.archive(ACTOR);

            assertThat(checklist.isArchived()).isTrue();
            assertThat(checklist.getArchivedBy()).contains(ACTOR);
        }

        @Test
        @DisplayName("an archived checklist line is read-only")
        void archivedChecklistRejectsEdits() {
            Checklist checklist = newChecklist();
            checklist.archive(ACTOR);

            assertThatThrownBy(() -> checklist.relabel("New text"))
                    .isInstanceOf(BusinessRuleViolationException.class);
            assertThatThrownBy(checklist::check)
                    .isInstanceOf(BusinessRuleViolationException.class);
        }
    }
}
