package com.projectmodule.work.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.projectmodule.common.exception.BusinessRuleViolationException;
import com.projectmodule.common.exception.ValidationException;
import com.projectmodule.common.identity.ExternalUserId;
import com.projectmodule.work.domain.event.MilestoneAtRisk;
import com.projectmodule.work.domain.event.MilestoneCompleted;
import com.projectmodule.work.domain.event.MilestoneCreated;
import com.projectmodule.work.domain.event.MilestoneDomainEvent;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class MilestoneTest {

    private static final UUID PROJECT_ID = UUID.randomUUID();
    private static final UUID PHASE_ID = UUID.randomUUID();
    private static final ExternalUserId ACTOR = ExternalUserId.of(UUID.randomUUID());

    private static Milestone newMilestone() {
        return Milestone.create(PROJECT_ID, PHASE_ID, "Beta launch", LocalDate.of(2026, 3, 1));
    }

    @Nested
    class Creation {

        @Test
        @DisplayName("starts PENDING, unarchived, with the given phase")
        void startsPending() {
            Milestone milestone = newMilestone();

            assertThat(milestone.getStatus()).isEqualTo(MilestoneStatus.PENDING);
            assertThat(milestone.isArchived()).isFalse();
            assertThat(milestone.getProjectId()).isEqualTo(PROJECT_ID);
            assertThat(milestone.getPhaseId()).contains(PHASE_ID);
        }

        @Test
        @DisplayName("accepts a null phase - a milestone may belong directly to the project")
        void acceptsNullPhase() {
            Milestone milestone = Milestone.create(PROJECT_ID, null, "No phase", null);

            assertThat(milestone.getPhaseId()).isEmpty();
        }

        @Test
        @DisplayName("rejects a blank name")
        void rejectsBlankName() {
            assertThatThrownBy(() -> Milestone.create(PROJECT_ID, PHASE_ID, "  ", null))
                    .isInstanceOf(ValidationException.class);
        }

        @Test
        @DisplayName("raises exactly one MilestoneCreated event")
        void raisesCreatedEvent() {
            Milestone milestone = newMilestone();

            List<MilestoneDomainEvent> events = milestone.pullDomainEvents();

            assertThat(events).hasSize(1);
            assertThat(events.get(0)).isInstanceOf(MilestoneCreated.class);
        }
    }

    @Nested
    class PlainEdits {

        @Test
        @DisplayName("renaming, describing and rescheduling raise no event")
        void plainEditsRaiseNoEvent() {
            Milestone milestone = newMilestone();
            milestone.pullDomainEvents();

            milestone.rename("Renamed");
            milestone.describe("New description");
            milestone.reschedule(LocalDate.of(2026, 4, 1));
            milestone.reassignPhase(UUID.randomUUID());

            assertThat(milestone.pullDomainEvents()).isEmpty();
        }
    }

    @Nested
    class StatusTransitions {

        @Test
        @DisplayName("completing raises exactly one MilestoneCompleted event")
        void completeRaisesEvent() {
            Milestone milestone = newMilestone();
            milestone.pullDomainEvents();

            milestone.complete();

            assertThat(milestone.getStatus()).isEqualTo(MilestoneStatus.COMPLETED);
            List<MilestoneDomainEvent> events = milestone.pullDomainEvents();
            assertThat(events).hasSize(1);
            assertThat(events.get(0)).isInstanceOf(MilestoneCompleted.class);
        }

        @Test
        @DisplayName("marking at risk raises exactly one MilestoneAtRisk event")
        void markAtRiskRaisesEvent() {
            Milestone milestone = newMilestone();
            milestone.pullDomainEvents();

            milestone.markAtRisk();

            assertThat(milestone.getStatus()).isEqualTo(MilestoneStatus.AT_RISK);
            List<MilestoneDomainEvent> events = milestone.pullDomainEvents();
            assertThat(events).hasSize(1);
            assertThat(events.get(0)).isInstanceOf(MilestoneAtRisk.class);
        }

        @Test
        @DisplayName("refuses to complete twice")
        void refusesDoubleComplete() {
            Milestone milestone = newMilestone();
            milestone.complete();

            assertThatThrownBy(milestone::complete).isInstanceOf(BusinessRuleViolationException.class);
        }

        @Test
        @DisplayName("refuses to mark at risk twice")
        void refusesDoubleAtRisk() {
            Milestone milestone = newMilestone();
            milestone.markAtRisk();

            assertThatThrownBy(milestone::markAtRisk).isInstanceOf(BusinessRuleViolationException.class);
        }

        @Test
        @DisplayName("refuses to mark a completed milestone at risk")
        void refusesAtRiskAfterCompleted() {
            Milestone milestone = newMilestone();
            milestone.complete();

            assertThatThrownBy(milestone::markAtRisk).isInstanceOf(BusinessRuleViolationException.class);
        }

        @Test
        @DisplayName("an at-risk milestone can still be completed")
        void atRiskMilestoneCanBeCompleted() {
            Milestone milestone = newMilestone();
            milestone.markAtRisk();
            milestone.pullDomainEvents();

            milestone.complete();

            assertThat(milestone.getStatus()).isEqualTo(MilestoneStatus.COMPLETED);
        }
    }

    @Nested
    class Archiving {

        @Test
        @DisplayName("archiving records who did it and raises no event")
        void archiveRecordsActorAndRaisesNoEvent() {
            Milestone milestone = newMilestone();
            milestone.pullDomainEvents();

            milestone.archive(ACTOR);

            assertThat(milestone.isArchived()).isTrue();
            assertThat(milestone.getArchivedBy()).contains(ACTOR);
            assertThat(milestone.pullDomainEvents()).isEmpty();
        }

        @Test
        @DisplayName("an archived milestone is read-only")
        void archivedMilestoneRejectsEdits() {
            Milestone milestone = newMilestone();
            milestone.archive(ACTOR);

            assertThatThrownBy(() -> milestone.rename("New name"))
                    .isInstanceOf(BusinessRuleViolationException.class);
            assertThatThrownBy(milestone::complete)
                    .isInstanceOf(BusinessRuleViolationException.class);
        }
    }
}
