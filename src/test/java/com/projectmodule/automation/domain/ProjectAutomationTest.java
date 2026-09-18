package com.projectmodule.automation.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.projectmodule.common.exception.BusinessRuleViolationException;
import com.projectmodule.common.exception.ValidationException;
import com.projectmodule.common.identity.ExternalUserId;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class ProjectAutomationTest {

    private static final UUID PROJECT_ID = UUID.randomUUID();

    private static ProjectAutomation notifyRule() {
        return ProjectAutomation.create(PROJECT_ID, "Notify on overdue", "task.overdue",
                AutomationActionType.NOTIFY, UUID.randomUUID(), null, "A task is overdue");
    }

    @Nested
    class Creation {

        @Test
        @DisplayName("creates an enabled rule with the supplied fields")
        void createsEnabledRule() {
            ProjectAutomation automation = notifyRule();

            assertThat(automation.getProjectId()).isEqualTo(PROJECT_ID);
            assertThat(automation.getName()).isEqualTo("Notify on overdue");
            assertThat(automation.getTriggerEvent()).isEqualTo("task.overdue");
            assertThat(automation.getActionType()).isEqualTo(AutomationActionType.NOTIFY);
            assertThat(automation.getActionRecipientId()).isPresent();
            assertThat(automation.isEnabled()).isTrue();
            assertThat(automation.isArchived()).isFalse();
        }

        @Test
        @DisplayName("NOTIFY requires an actionRecipientId")
        void notifyRequiresRecipient() {
            assertThatThrownBy(() -> ProjectAutomation.create(PROJECT_ID, "n", "task.overdue",
                    AutomationActionType.NOTIFY, null, null, "message"))
                    .isInstanceOf(ValidationException.class);
        }

        @Test
        @DisplayName("CHAT_MESSAGE requires an actionChannelReference")
        void chatMessageRequiresChannel() {
            assertThatThrownBy(() -> ProjectAutomation.create(PROJECT_ID, "n", "task.overdue",
                    AutomationActionType.CHAT_MESSAGE, null, null, "message"))
                    .isInstanceOf(ValidationException.class);
        }

        @Test
        @DisplayName("rejects a blank name")
        void rejectsBlankName() {
            assertThatThrownBy(() -> ProjectAutomation.create(PROJECT_ID, "   ", "task.overdue",
                    AutomationActionType.NOTIFY, UUID.randomUUID(), null, "message"))
                    .isInstanceOf(ValidationException.class);
        }

        @Test
        @DisplayName("rejects a triggerEvent outside the catalogue, per docs/project/28-AUTOMATION-SPEC.md §2")
        void rejectsUncatalogedTriggerEvent() {
            assertThatThrownBy(() -> ProjectAutomation.create(PROJECT_ID, "n", "task.deleted",
                    AutomationActionType.NOTIFY, UUID.randomUUID(), null, "message"))
                    .isInstanceOf(ValidationException.class);
        }

        @Test
        @DisplayName("rejects an AI/Automation catalogue event as a triggerEvent (deliberately excluded)")
        void rejectsAiOrAutomationEventAsTrigger() {
            assertThatThrownBy(() -> ProjectAutomation.create(PROJECT_ID, "n", "automation.executed",
                    AutomationActionType.NOTIFY, UUID.randomUUID(), null, "message"))
                    .isInstanceOf(ValidationException.class);
        }

        @Test
        @DisplayName("accepts every catalogued event type as a triggerEvent")
        void acceptsEveryCataloguedEventType() {
            for (AutomationTriggerEvent value : AutomationTriggerEvent.values()) {
                ProjectAutomation automation = ProjectAutomation.create(PROJECT_ID, "n", value.eventType(),
                        AutomationActionType.NOTIFY, UUID.randomUUID(), null, "message");
                assertThat(automation.getTriggerEvent()).isEqualTo(value.eventType());
            }
        }
    }

    @Nested
    class EnableDisable {

        @Test
        @DisplayName("disable() then enable() round-trips")
        void disableThenEnable() {
            ProjectAutomation automation = notifyRule();

            automation.disable();
            assertThat(automation.isEnabled()).isFalse();

            automation.enable();
            assertThat(automation.isEnabled()).isTrue();
        }
    }

    @Nested
    class Matching {

        @Test
        @DisplayName("matches() is true only when enabled, active and the trigger event equals")
        void matchesOnlyWhenEnabledActiveAndEqual() {
            ProjectAutomation automation = notifyRule();

            assertThat(automation.matches("task.overdue")).isTrue();
            assertThat(automation.matches("task.blocked")).isFalse();

            automation.disable();
            assertThat(automation.matches("task.overdue")).isFalse();
        }

        @Test
        @DisplayName("an archived automation never matches")
        void archivedNeverMatches() {
            ProjectAutomation automation = notifyRule();
            automation.archive(ExternalUserId.of(UUID.randomUUID()));

            assertThat(automation.matches("task.overdue")).isFalse();
        }
    }

    @Nested
    class Archiving {

        @Test
        @DisplayName("archive() sets archivedAt/archivedBy")
        void archiveSetsFields() {
            ProjectAutomation automation = notifyRule();
            ExternalUserId archivedBy = ExternalUserId.of(UUID.randomUUID());

            automation.archive(archivedBy);

            assertThat(automation.isArchived()).isTrue();
            assertThat(automation.getArchivedBy()).contains(archivedBy);
        }

        @Test
        @DisplayName("archive() rejects an already-archived automation")
        void archiveRejectsAlreadyArchived() {
            ProjectAutomation automation = notifyRule();
            automation.archive(ExternalUserId.of(UUID.randomUUID()));

            assertThatThrownBy(() -> automation.archive(ExternalUserId.of(UUID.randomUUID())))
                    .isInstanceOf(BusinessRuleViolationException.class);
        }

        @Test
        @DisplayName("cannot rename an archived automation")
        void cannotRenameArchived() {
            ProjectAutomation automation = notifyRule();
            automation.archive(ExternalUserId.of(UUID.randomUUID()));

            assertThatThrownBy(() -> automation.rename("New name"))
                    .isInstanceOf(BusinessRuleViolationException.class);
        }
    }
}
