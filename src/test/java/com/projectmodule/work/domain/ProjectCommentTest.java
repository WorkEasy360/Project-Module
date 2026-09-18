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

class ProjectCommentTest {

    private static final UUID PROJECT_ID = UUID.randomUUID();
    private static final ExternalUserId AUTHOR = ExternalUserId.of(UUID.randomUUID());
    private static final ExternalUserId OTHER_USER = ExternalUserId.of(UUID.randomUUID());

    private static ProjectComment newComment() {
        return ProjectComment.create(PROJECT_ID, AUTHOR, "Looks good to me");
    }

    @Nested
    class Creation {

        @Test
        @DisplayName("starts unarchived, belonging to the given project and author")
        void startsUnarchived() {
            ProjectComment comment = newComment();

            assertThat(comment.isArchived()).isFalse();
            assertThat(comment.getProjectId()).isEqualTo(PROJECT_ID);
            assertThat(comment.getAuthorId()).isEqualTo(AUTHOR);
            assertThat(comment.getBody()).isEqualTo("Looks good to me");
        }

        @Test
        @DisplayName("rejects a blank body")
        void rejectsBlankBody() {
            assertThatThrownBy(() -> ProjectComment.create(PROJECT_ID, AUTHOR, "   "))
                    .isInstanceOf(ValidationException.class);
        }
    }

    @Nested
    class Authorship {

        @Test
        @DisplayName("isAuthoredBy is true for the author and false for anyone else")
        void identifiesAuthor() {
            ProjectComment comment = newComment();

            assertThat(comment.isAuthoredBy(AUTHOR)).isTrue();
            assertThat(comment.isAuthoredBy(OTHER_USER)).isFalse();
        }
    }

    @Nested
    class Editing {

        @Test
        @DisplayName("edit replaces the body")
        void editsBody() {
            ProjectComment comment = newComment();

            comment.edit("Actually, one concern");

            assertThat(comment.getBody()).isEqualTo("Actually, one concern");
        }

        @Test
        @DisplayName("rejects an edit to a blank body")
        void rejectsBlankEdit() {
            ProjectComment comment = newComment();

            assertThatThrownBy(() -> comment.edit("   "))
                    .isInstanceOf(ValidationException.class);
        }
    }

    @Nested
    class Archiving {

        @Test
        @DisplayName("archiving records who did it")
        void archiveRecordsActor() {
            ProjectComment comment = newComment();

            comment.archive(AUTHOR);

            assertThat(comment.isArchived()).isTrue();
            assertThat(comment.getArchivedBy()).contains(AUTHOR);
        }

        @Test
        @DisplayName("refuses to archive twice")
        void refusesDoubleArchive() {
            ProjectComment comment = newComment();
            comment.archive(AUTHOR);

            assertThatThrownBy(() -> comment.archive(AUTHOR))
                    .isInstanceOf(BusinessRuleViolationException.class);
        }

        @Test
        @DisplayName("an archived comment is read-only")
        void archivedCommentRejectsEdits() {
            ProjectComment comment = newComment();
            comment.archive(AUTHOR);

            assertThatThrownBy(() -> comment.edit("New body"))
                    .isInstanceOf(BusinessRuleViolationException.class);
        }
    }
}
