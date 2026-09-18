package com.projectmodule.work.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.projectmodule.common.exception.BusinessRuleViolationException;
import com.projectmodule.common.exception.ValidationException;
import com.projectmodule.common.identity.ExternalUserId;
import com.projectmodule.project.domain.ProjectPriority;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class IssueTest {

    private static final UUID PROJECT_ID = UUID.randomUUID();
    private static final ExternalUserId ACTOR = ExternalUserId.of(UUID.randomUUID());

    private static Issue newIssue() {
        return Issue.create(PROJECT_ID, "Payment gateway down", ProjectPriority.CRITICAL);
    }

    @Nested
    class Creation {

        @Test
        @DisplayName("starts unarchived, belonging to the given project")
        void startsUnarchived() {
            Issue issue = newIssue();

            assertThat(issue.isArchived()).isFalse();
            assertThat(issue.getProjectId()).isEqualTo(PROJECT_ID);
            assertThat(issue.getPriority()).isEqualTo(ProjectPriority.CRITICAL);
        }

        @Test
        @DisplayName("rejects a blank name")
        void rejectsBlankName() {
            assertThatThrownBy(() -> Issue.create(PROJECT_ID, "  ", ProjectPriority.CRITICAL))
                    .isInstanceOf(ValidationException.class);
        }
    }

    @Nested
    class PlainEdits {

        @Test
        @DisplayName("plain field mutators apply their changes")
        void appliesEdits() {
            Issue issue = newIssue();

            issue.rename("Gateway partially restored");
            issue.describe("New description");
            issue.reprioritize(ProjectPriority.MEDIUM);

            assertThat(issue.getName()).isEqualTo("Gateway partially restored");
            assertThat(issue.getDescription()).contains("New description");
            assertThat(issue.getPriority()).isEqualTo(ProjectPriority.MEDIUM);
        }
    }

    @Nested
    class Archiving {

        @Test
        @DisplayName("archiving records who did it")
        void archiveRecordsActor() {
            Issue issue = newIssue();

            issue.archive(ACTOR);

            assertThat(issue.isArchived()).isTrue();
            assertThat(issue.getArchivedBy()).contains(ACTOR);
        }

        @Test
        @DisplayName("refuses to archive twice")
        void refusesDoubleArchive() {
            Issue issue = newIssue();
            issue.archive(ACTOR);

            assertThatThrownBy(() -> issue.archive(ACTOR))
                    .isInstanceOf(BusinessRuleViolationException.class);
        }

        @Test
        @DisplayName("an archived issue is read-only")
        void archivedIssueRejectsEdits() {
            Issue issue = newIssue();
            issue.archive(ACTOR);

            assertThatThrownBy(() -> issue.rename("New name"))
                    .isInstanceOf(BusinessRuleViolationException.class);
            assertThatThrownBy(() -> issue.reprioritize(ProjectPriority.LOW))
                    .isInstanceOf(BusinessRuleViolationException.class);
        }
    }
}
