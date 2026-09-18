package com.projectmodule.work.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.projectmodule.common.exception.BusinessRuleViolationException;
import com.projectmodule.common.exception.ValidationException;
import com.projectmodule.common.identity.ExternalUserId;
import com.projectmodule.work.domain.event.PhaseCreated;
import com.projectmodule.work.domain.event.PhaseDomainEvent;
import com.projectmodule.work.domain.event.PhaseUpdated;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class PhaseTest {

    private static final UUID PROJECT_ID = UUID.randomUUID();
    private static final ExternalUserId ACTOR = ExternalUserId.of(UUID.randomUUID());

    private static Phase newPhase() {
        return Phase.create(PROJECT_ID, "Discovery");
    }

    @Nested
    class Creation {

        @Test
        @DisplayName("starts unarchived, belonging to the given project, with an identity already assigned")
        void startsUnarchived() {
            Phase phase = newPhase();

            assertThat(phase.isArchived()).isFalse();
            assertThat(phase.getProjectId()).isEqualTo(PROJECT_ID);
            assertThat(phase.getId()).isNotNull();
            assertThat(phase.getName()).isEqualTo("Discovery");
        }

        @Test
        @DisplayName("rejects a blank name")
        void rejectsBlankName() {
            assertThatThrownBy(() -> Phase.create(PROJECT_ID, "   "))
                    .isInstanceOf(ValidationException.class);
        }

        @Test
        @DisplayName("raises exactly one PhaseCreated event")
        void raisesCreatedEvent() {
            Phase phase = newPhase();

            List<PhaseDomainEvent> events = phase.pullDomainEvents();

            assertThat(events).hasSize(1);
            assertThat(events.get(0)).isInstanceOf(PhaseCreated.class);
            assertThat(events.get(0).phaseId()).isEqualTo(phase.getId());
        }
    }

    @Nested
    class Scheduling {

        @Test
        @DisplayName("accepts an end date on or after the start date")
        void acceptsOrderedDates() {
            Phase phase = newPhase();
            LocalDate start = LocalDate.of(2026, 1, 1);

            phase.schedule(start, start.plusDays(30));

            assertThat(phase.getStartDate()).contains(start);
            assertThat(phase.getEndDate()).contains(start.plusDays(30));
        }

        @Test
        @DisplayName("rejects an end date before the start date")
        void rejectsReversedDates() {
            Phase phase = newPhase();

            assertThatThrownBy(() -> phase.schedule(
                    LocalDate.of(2026, 5, 1), LocalDate.of(2026, 4, 1)))
                    .isInstanceOf(ValidationException.class);
        }
    }

    @Nested
    class Updates {

        @Test
        @DisplayName("recordUpdated raises exactly one PhaseUpdated event, regardless of how many fields changed")
        void recordUpdatedRaisesOneEvent() {
            Phase phase = newPhase();
            phase.pullDomainEvents();

            phase.rename("Renamed");
            phase.describe("New description");
            phase.recordUpdated();

            List<PhaseDomainEvent> events = phase.pullDomainEvents();

            assertThat(events).hasSize(1);
            assertThat(events.get(0)).isInstanceOf(PhaseUpdated.class);
        }
    }

    @Nested
    class Archiving {

        @Test
        @DisplayName("archiving records who did it and raises no event")
        void archiveRecordsActorAndRaisesNoEvent() {
            Phase phase = newPhase();
            phase.pullDomainEvents();

            phase.archive(ACTOR);

            assertThat(phase.isArchived()).isTrue();
            assertThat(phase.getArchivedBy()).contains(ACTOR);
            assertThat(phase.pullDomainEvents()).isEmpty();
        }

        @Test
        @DisplayName("refuses to archive twice")
        void refusesDoubleArchive() {
            Phase phase = newPhase();
            phase.archive(ACTOR);

            assertThatThrownBy(() -> phase.archive(ACTOR))
                    .isInstanceOf(BusinessRuleViolationException.class);
        }

        @Test
        @DisplayName("an archived phase is read-only")
        void archivedPhaseRejectsEdits() {
            Phase phase = newPhase();
            phase.archive(ACTOR);

            assertThatThrownBy(() -> phase.rename("New name"))
                    .isInstanceOf(BusinessRuleViolationException.class);
            assertThatThrownBy(() -> phase.recordUpdated())
                    .isInstanceOf(BusinessRuleViolationException.class);
        }
    }
}
