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

class DecisionTest {

    private static final UUID PROJECT_ID = UUID.randomUUID();
    private static final ExternalUserId ACTOR = ExternalUserId.of(UUID.randomUUID());
    private static final ExternalUserId DECIDER = ExternalUserId.of(UUID.randomUUID());

    private static Decision newDecision() {
        return Decision.create(PROJECT_ID, "Use PostgreSQL for persistence");
    }

    @Nested
    class Creation {

        @Test
        @DisplayName("starts unarchived, belonging to the given project, with no decider")
        void startsUnarchived() {
            Decision decision = newDecision();

            assertThat(decision.isArchived()).isFalse();
            assertThat(decision.getProjectId()).isEqualTo(PROJECT_ID);
            assertThat(decision.getDecidedBy()).isEmpty();
        }

        @Test
        @DisplayName("rejects a blank name")
        void rejectsBlankName() {
            assertThatThrownBy(() -> Decision.create(PROJECT_ID, "  "))
                    .isInstanceOf(ValidationException.class);
        }
    }

    @Nested
    class PlainEdits {

        @Test
        @DisplayName("plain field mutators apply their changes")
        void appliesEdits() {
            Decision decision = newDecision();

            decision.rename("Use PostgreSQL 17 for persistence");
            decision.describe("Chosen for jsonb, partial indexes and timestamptz support");
            decision.attributeTo(DECIDER);

            assertThat(decision.getName()).isEqualTo("Use PostgreSQL 17 for persistence");
            assertThat(decision.getDescription()).contains("Chosen for jsonb, partial indexes and timestamptz support");
            assertThat(decision.getDecidedBy()).contains(DECIDER);
        }
    }

    @Nested
    class Archiving {

        @Test
        @DisplayName("archiving records who did it")
        void archiveRecordsActor() {
            Decision decision = newDecision();

            decision.archive(ACTOR);

            assertThat(decision.isArchived()).isTrue();
            assertThat(decision.getArchivedBy()).contains(ACTOR);
        }

        @Test
        @DisplayName("refuses to archive twice")
        void refusesDoubleArchive() {
            Decision decision = newDecision();
            decision.archive(ACTOR);

            assertThatThrownBy(() -> decision.archive(ACTOR))
                    .isInstanceOf(BusinessRuleViolationException.class);
        }

        @Test
        @DisplayName("an archived decision is read-only")
        void archivedDecisionRejectsEdits() {
            Decision decision = newDecision();
            decision.archive(ACTOR);

            assertThatThrownBy(() -> decision.rename("New name"))
                    .isInstanceOf(BusinessRuleViolationException.class);
            assertThatThrownBy(() -> decision.attributeTo(DECIDER))
                    .isInstanceOf(BusinessRuleViolationException.class);
        }
    }
}
