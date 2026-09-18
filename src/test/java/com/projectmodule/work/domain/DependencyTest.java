package com.projectmodule.work.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.projectmodule.common.exception.BusinessRuleViolationException;
import com.projectmodule.common.exception.ValidationException;
import com.projectmodule.common.identity.ExternalUserId;
import com.projectmodule.work.domain.event.DependencyBroken;
import com.projectmodule.work.domain.event.DependencyCreated;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class DependencyTest {

    private static final UUID DEPENDENT_TASK_ID = UUID.randomUUID();
    private static final UUID PREREQUISITE_TASK_ID = UUID.randomUUID();
    private static final ExternalUserId ACTOR = ExternalUserId.of(UUID.randomUUID());

    private static Dependency newDependency() {
        return Dependency.create(DEPENDENT_TASK_ID, PREREQUISITE_TASK_ID);
    }

    @Nested
    class Creation {

        @Test
        @DisplayName("starts not broken, unarchived, referencing both tasks")
        void startsNotBroken() {
            Dependency dependency = newDependency();

            assertThat(dependency.isBroken()).isFalse();
            assertThat(dependency.isArchived()).isFalse();
            assertThat(dependency.getDependentTaskId()).isEqualTo(DEPENDENT_TASK_ID);
            assertThat(dependency.getPrerequisiteTaskId()).isEqualTo(PREREQUISITE_TASK_ID);
        }

        @Test
        @DisplayName("raises DependencyCreated")
        void raisesCreatedEvent() {
            Dependency dependency = newDependency();

            assertThat(dependency.pullDomainEvents())
                    .containsExactly(new DependencyCreated(dependency.getId()));
        }

        @Test
        @DisplayName("rejects a task depending on itself")
        void rejectsSelfDependency() {
            UUID taskId = UUID.randomUUID();

            assertThatThrownBy(() -> Dependency.create(taskId, taskId))
                    .isInstanceOf(ValidationException.class);
        }
    }

    @Nested
    class MarkingBroken {

        @Test
        @DisplayName("marks broken and raises DependencyBroken")
        void marksBroken() {
            Dependency dependency = newDependency();
            dependency.pullDomainEvents();

            dependency.markBroken();

            assertThat(dependency.isBroken()).isTrue();
            assertThat(dependency.pullDomainEvents())
                    .containsExactly(new DependencyBroken(dependency.getId()));
        }

        @Test
        @DisplayName("refuses to mark broken twice")
        void refusesDoubleBreak() {
            Dependency dependency = newDependency();
            dependency.markBroken();

            assertThatThrownBy(dependency::markBroken)
                    .isInstanceOf(BusinessRuleViolationException.class);
        }
    }

    @Nested
    class Archiving {

        @Test
        @DisplayName("archiving records who did it")
        void archiveRecordsActor() {
            Dependency dependency = newDependency();

            dependency.archive(ACTOR);

            assertThat(dependency.isArchived()).isTrue();
            assertThat(dependency.getArchivedBy()).contains(ACTOR);
        }

        @Test
        @DisplayName("refuses to archive twice")
        void refusesDoubleArchive() {
            Dependency dependency = newDependency();
            dependency.archive(ACTOR);

            assertThatThrownBy(() -> dependency.archive(ACTOR))
                    .isInstanceOf(BusinessRuleViolationException.class);
        }

        @Test
        @DisplayName("an archived dependency is read-only")
        void archivedDependencyRejectsEdits() {
            Dependency dependency = newDependency();
            dependency.archive(ACTOR);

            assertThatThrownBy(dependency::markBroken)
                    .isInstanceOf(BusinessRuleViolationException.class);
        }
    }
}
