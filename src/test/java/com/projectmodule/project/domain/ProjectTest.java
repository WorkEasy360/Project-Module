package com.projectmodule.project.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThatCode;

import com.projectmodule.common.exception.BusinessRuleViolationException;
import com.projectmodule.common.exception.ValidationException;
import com.projectmodule.common.identity.ExternalUserId;
import com.projectmodule.common.identity.OrganizationId;
import com.projectmodule.project.domain.event.ProjectArchived;
import com.projectmodule.project.domain.event.ProjectCreated;
import com.projectmodule.project.domain.event.ProjectDomainEvent;
import com.projectmodule.project.domain.event.ProjectUpdated;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class ProjectTest {

    private static final OrganizationId ORG = OrganizationId.of(UUID.randomUUID());
    private static final ExternalUserId OWNER = ExternalUserId.of(UUID.randomUUID());
    private static final ExternalUserId ACTOR = ExternalUserId.of(UUID.randomUUID());

    private static Project newProject() {
        return Project.create(ORG, "Apollo", OWNER, ProjectPriority.MEDIUM);
    }

    @Nested
    class Creation {

        @Test
        @DisplayName("starts in planning, unarchived, with an identity already assigned")
        void startsInPlanning() {
            Project project = newProject();

            assertThat(project.getStatus()).isEqualTo(ProjectStatus.PLANNING);
            assertThat(project.isArchived()).isFalse();
            assertThat(project.getId()).isNotNull();
            assertThat(project.getOwnerId()).isEqualTo(OWNER);
            assertThat(project.getOrganizationId()).isEqualTo(ORG);
        }

        @Test
        @DisplayName("trims surrounding whitespace from the name")
        void trimsName() {
            assertThat(Project.create(ORG, "  Apollo  ", OWNER, ProjectPriority.LOW).getName())
                    .isEqualTo("Apollo");
        }

        @Test
        @DisplayName("rejects a blank name")
        void rejectsBlankName() {
            assertThatThrownBy(() -> Project.create(ORG, "   ", OWNER, ProjectPriority.LOW))
                    .isInstanceOf(ValidationException.class)
                    .hasMessageContaining("must not be blank");
        }

        @Test
        @DisplayName("rejects a name longer than the column allows, before it reaches the database")
        void rejectsOverlongName() {
            String tooLong = "x".repeat(201);

            assertThatThrownBy(() -> Project.create(ORG, tooLong, OWNER, ProjectPriority.LOW))
                    .isInstanceOf(ValidationException.class)
                    .hasMessageContaining("at most 200");
        }

        @Test
        @DisplayName("accepts a name of exactly the maximum length")
        void acceptsBoundaryLengthName() {
            assertThatCode(() -> Project.create(ORG, "x".repeat(200), OWNER, ProjectPriority.LOW))
                    .doesNotThrowAnyException();
        }
    }

    @Nested
    class Scheduling {

        @Test
        @DisplayName("accepts an end date on or after the start date")
        void acceptsOrderedDates() {
            Project project = newProject();
            LocalDate start = LocalDate.of(2026, 1, 1);

            project.schedule(start, start.plusDays(30));

            assertThat(project.getStartDate()).contains(start);
            assertThat(project.getTargetEndDate()).contains(start.plusDays(30));
        }

        @Test
        @DisplayName("rejects an end date before the start date")
        void rejectsReversedDates() {
            Project project = newProject();

            assertThatThrownBy(() -> project.schedule(
                    LocalDate.of(2026, 5, 1), LocalDate.of(2026, 4, 1)))
                    .isInstanceOf(ValidationException.class);
        }

        @Test
        @DisplayName("allows either date to be left unset")
        void allowsOpenEndedSchedule() {
            Project project = newProject();

            project.schedule(LocalDate.of(2026, 1, 1), null);

            assertThat(project.getStartDate()).isPresent();
            assertThat(project.getTargetEndDate()).isEmpty();
        }
    }

    @Nested
    class Archiving {

        @Test
        @DisplayName("archiving records who did it and when")
        void archiveRecordsActor() {
            Project project = newProject();

            project.archive(ACTOR);

            assertThat(project.isArchived()).isTrue();
            assertThat(project.getArchivedBy()).contains(ACTOR);
            assertThat(project.getArchivedAt()).isPresent();
        }

        @Test
        @DisplayName("archiving leaves the status untouched, so how it ended is not overwritten")
        void archivePreservesStatus() {
            Project project = newProject();
            project.changeStatus(ProjectStatus.COMPLETED);

            project.archive(ACTOR);

            assertThat(project.getStatus()).isEqualTo(ProjectStatus.COMPLETED);
        }

        @Test
        @DisplayName("refuses to archive twice")
        void refusesDoubleArchive() {
            Project project = newProject();
            project.archive(ACTOR);

            assertThatThrownBy(() -> project.archive(ACTOR))
                    .isInstanceOf(BusinessRuleViolationException.class);
        }

        @Test
        @DisplayName("an archived project is read-only")
        void archivedProjectRejectsEdits() {
            Project project = newProject();
            project.archive(ACTOR);

            assertThatThrownBy(() -> project.rename("New name"))
                    .isInstanceOf(BusinessRuleViolationException.class);
            assertThatThrownBy(() -> project.changeStatus(ProjectStatus.ACTIVE))
                    .isInstanceOf(BusinessRuleViolationException.class);
            assertThatThrownBy(() -> project.changePriority(ProjectPriority.HIGH))
                    .isInstanceOf(BusinessRuleViolationException.class);
            assertThatThrownBy(() -> project.schedule(null, null))
                    .isInstanceOf(BusinessRuleViolationException.class);
        }

        @Test
        @DisplayName("restoring clears both archive columns together")
        void restoreClearsArchiveState() {
            Project project = newProject();
            project.archive(ACTOR);

            project.restore();

            assertThat(project.isArchived()).isFalse();
            assertThat(project.getArchivedAt()).isEmpty();
            assertThat(project.getArchivedBy()).isEmpty();
            assertThatCode(() -> project.rename("Allowed again")).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("refuses to restore a project that is not archived")
        void refusesRestoreOfLiveProject() {
            assertThatThrownBy(() -> newProject().restore())
                    .isInstanceOf(BusinessRuleViolationException.class);
        }
    }

    @Nested
    class StatusSemantics {

        @Test
        @DisplayName("open states are the ones where work can still change")
        void openStates() {
            assertThat(ProjectStatus.PLANNING.isOpen()).isTrue();
            assertThat(ProjectStatus.ACTIVE.isOpen()).isTrue();
            assertThat(ProjectStatus.ON_HOLD.isOpen()).isTrue();
            assertThat(ProjectStatus.COMPLETED.isOpen()).isFalse();
            assertThat(ProjectStatus.CANCELLED.isOpen()).isFalse();
        }
    }

    @Nested
    class DomainEvents {

        @Test
        @DisplayName("creation raises exactly one ProjectCreated event")
        void createRaisesProjectCreated() {
            Project project = newProject();

            List<ProjectDomainEvent> events = project.pullDomainEvents();

            assertThat(events).hasSize(1);
            assertThat(events.get(0)).isInstanceOf(ProjectCreated.class);
            assertThat(events.get(0).projectId()).isEqualTo(project.getId());
        }

        @Test
        @DisplayName("pulling events clears them, so the same event is never recorded twice")
        void pullingClearsEvents() {
            Project project = newProject();

            project.pullDomainEvents();

            assertThat(project.pullDomainEvents()).isEmpty();
        }

        @Test
        @DisplayName("recordUpdated raises exactly one ProjectUpdated event, regardless of how many fields changed")
        void recordUpdatedRaisesOneEvent() {
            Project project = newProject();
            project.pullDomainEvents(); // discard the creation event

            project.rename("Renamed");
            project.changePriority(ProjectPriority.CRITICAL);
            project.changeStatus(ProjectStatus.ACTIVE);
            project.recordUpdated();

            List<ProjectDomainEvent> events = project.pullDomainEvents();

            assertThat(events).hasSize(1);
            assertThat(events.get(0)).isInstanceOf(ProjectUpdated.class);
        }

        @Test
        @DisplayName("archiving raises exactly one ProjectArchived event")
        void archiveRaisesProjectArchived() {
            Project project = newProject();
            project.pullDomainEvents(); // discard the creation event

            project.archive(OWNER);

            List<ProjectDomainEvent> events = project.pullDomainEvents();

            assertThat(events).hasSize(1);
            assertThat(events.get(0)).isInstanceOf(ProjectArchived.class);
        }

        @Test
        @DisplayName("recordUpdated refuses to run on an archived project, like every other mutator")
        void recordUpdatedRefusesOnArchivedProject() {
            Project project = newProject();
            project.archive(OWNER);
            project.pullDomainEvents();

            assertThatThrownBy(project::recordUpdated)
                    .isInstanceOf(BusinessRuleViolationException.class);
            assertThat(project.pullDomainEvents()).isEmpty();
        }

        @Test
        @DisplayName("restore raises no event: project.restored is not in the current catalogue")
        void restoreRaisesNoEvent() {
            Project project = newProject();
            project.archive(OWNER);
            project.pullDomainEvents();

            project.restore();

            assertThat(project.pullDomainEvents()).isEmpty();
        }

        @Test
        @DisplayName("a failed mutation raises no event")
        void failedMutationRaisesNoEvent() {
            Project project = newProject();
            project.pullDomainEvents();

            assertThatThrownBy(() -> project.rename("   "))
                    .isInstanceOf(ValidationException.class);

            assertThat(project.pullDomainEvents()).isEmpty();
        }
    }
}
