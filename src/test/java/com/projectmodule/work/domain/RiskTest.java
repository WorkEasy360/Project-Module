package com.projectmodule.work.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.projectmodule.common.exception.BusinessRuleViolationException;
import com.projectmodule.common.exception.ValidationException;
import com.projectmodule.common.identity.ExternalUserId;
import com.projectmodule.project.domain.ProjectPriority;
import com.projectmodule.work.domain.event.RiskCreated;
import com.projectmodule.work.domain.event.RiskResolved;
import com.projectmodule.work.domain.event.RiskUpdated;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class RiskTest {

    private static final UUID PROJECT_ID = UUID.randomUUID();
    private static final ExternalUserId ACTOR = ExternalUserId.of(UUID.randomUUID());

    private static Risk newRisk() {
        return Risk.create(PROJECT_ID, "Vendor delay", ProjectPriority.HIGH);
    }

    @Nested
    class Creation {

        @Test
        @DisplayName("starts open, unarchived, belonging to the given project")
        void startsOpen() {
            Risk risk = newRisk();

            assertThat(risk.getStatus()).isEqualTo(RiskStatus.OPEN);
            assertThat(risk.isArchived()).isFalse();
            assertThat(risk.getProjectId()).isEqualTo(PROJECT_ID);
            assertThat(risk.getPriority()).isEqualTo(ProjectPriority.HIGH);
        }

        @Test
        @DisplayName("raises RiskCreated")
        void raisesCreatedEvent() {
            Risk risk = newRisk();

            assertThat(risk.pullDomainEvents()).containsExactly(new RiskCreated(risk.getId()));
        }

        @Test
        @DisplayName("rejects a blank name")
        void rejectsBlankName() {
            assertThatThrownBy(() -> Risk.create(PROJECT_ID, "  ", ProjectPriority.HIGH))
                    .isInstanceOf(ValidationException.class);
        }
    }

    @Nested
    class PlainEdits {

        @Test
        @DisplayName("recordUpdated raises exactly one RiskUpdated")
        void recordUpdatedRaisesOneEvent() {
            Risk risk = newRisk();
            risk.pullDomainEvents();

            risk.rename("Supplier delay");
            risk.describe("New description");
            risk.reprioritize(ProjectPriority.CRITICAL);
            risk.recordUpdated();

            assertThat(risk.getName()).isEqualTo("Supplier delay");
            assertThat(risk.getDescription()).contains("New description");
            assertThat(risk.getPriority()).isEqualTo(ProjectPriority.CRITICAL);
            assertThat(risk.pullDomainEvents()).containsExactly(new RiskUpdated(risk.getId()));
        }
    }

    @Nested
    class Resolution {

        @Test
        @DisplayName("resolves and raises RiskResolved")
        void resolves() {
            Risk risk = newRisk();
            risk.pullDomainEvents();

            risk.resolve();

            assertThat(risk.getStatus()).isEqualTo(RiskStatus.RESOLVED);
            assertThat(risk.pullDomainEvents()).containsExactly(new RiskResolved(risk.getId()));
        }

        @Test
        @DisplayName("refuses to resolve twice")
        void refusesDoubleResolve() {
            Risk risk = newRisk();
            risk.resolve();

            assertThatThrownBy(risk::resolve)
                    .isInstanceOf(BusinessRuleViolationException.class);
        }
    }

    @Nested
    class Archiving {

        @Test
        @DisplayName("archiving records who did it")
        void archiveRecordsActor() {
            Risk risk = newRisk();

            risk.archive(ACTOR);

            assertThat(risk.isArchived()).isTrue();
            assertThat(risk.getArchivedBy()).contains(ACTOR);
        }

        @Test
        @DisplayName("refuses to archive twice")
        void refusesDoubleArchive() {
            Risk risk = newRisk();
            risk.archive(ACTOR);

            assertThatThrownBy(() -> risk.archive(ACTOR))
                    .isInstanceOf(BusinessRuleViolationException.class);
        }

        @Test
        @DisplayName("an archived risk is read-only")
        void archivedRiskRejectsEdits() {
            Risk risk = newRisk();
            risk.archive(ACTOR);

            assertThatThrownBy(() -> risk.rename("New name"))
                    .isInstanceOf(BusinessRuleViolationException.class);
            assertThatThrownBy(risk::resolve)
                    .isInstanceOf(BusinessRuleViolationException.class);
        }
    }
}
