package com.projectmodule.work.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.projectmodule.common.identity.ExternalUserId;
import com.projectmodule.common.identity.OrganizationId;
import com.projectmodule.project.domain.Project;
import com.projectmodule.project.domain.ProjectPriority;
import com.projectmodule.project.infrastructure.ProjectRepository;
import com.projectmodule.work.domain.ProjectComment;
import java.util.UUID;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.test.context.ActiveProfiles;

/**
 * Repository tests for ProjectComment, run against a real PostgreSQL 17 instance — the same
 * conventions {@code DecisionPersistenceTest}/{@code IssuePersistenceTest} already establish.
 * Starting this context proves the entity mapping matches the {@code V8} migration.
 *
 * <p>Skipped when {@code PROJECTMODULE_TEST_DB_URL} is not set.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@EnabledIfEnvironmentVariable(named = "PROJECTMODULE_TEST_DB_URL", matches = ".+")
class ProjectCommentPersistenceTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private ProjectRepository projectRepository;

    @Autowired
    private ProjectCommentRepository commentRepository;

    private final OrganizationId organization = OrganizationId.of(UUID.randomUUID());
    private final ExternalUserId owner = ExternalUserId.of(UUID.randomUUID());
    private final ExternalUserId author = ExternalUserId.of(UUID.randomUUID());

    private Project persistedProject(String name) {
        Project project = Project.create(organization, name, owner, ProjectPriority.MEDIUM);
        return projectRepository.saveAndFlush(project);
    }

    @Nested
    class CommentPersistence {

        @Test
        @DisplayName("round-trips a comment, including its author")
        void roundTripsComment() {
            Project project = persistedProject("Apollo");
            ProjectComment comment = ProjectComment.create(project.getId(), author, "Looks good to me");
            UUID id = comment.getId();

            commentRepository.saveAndFlush(comment);
            entityManager.clear();

            ProjectComment loaded = commentRepository.findById(id).orElseThrow();
            assertThat(loaded.getBody()).isEqualTo("Looks good to me");
            assertThat(loaded.getProjectId()).isEqualTo(project.getId());
            assertThat(loaded.getAuthorId()).isEqualTo(author);
        }

        @Test
        @DisplayName("soft-archived comments are still stored, with their archive detail")
        void softArchiveRetainsRow() {
            Project project = persistedProject("Apollo");
            ProjectComment comment = commentRepository.saveAndFlush(
                    ProjectComment.create(project.getId(), author, "Looks good to me"));

            comment.archive(owner);
            commentRepository.saveAndFlush(comment);
            entityManager.clear();

            ProjectComment reloaded = commentRepository.findById(comment.getId()).orElseThrow();
            assertThat(reloaded.isArchived()).isTrue();
            assertThat(commentRepository.findByIdAndArchivedAtIsNull(comment.getId())).isEmpty();
        }

        @Test
        @DisplayName("cannot reference a project that does not exist")
        void enforcesProjectForeignKey() {
            ProjectComment comment = ProjectComment.create(UUID.randomUUID(), author, "Orphan");

            assertThatThrownBy(() -> commentRepository.saveAndFlush(comment))
                    .isInstanceOf(DataIntegrityViolationException.class);
        }

        @Test
        @DisplayName("the database rejects a blank body even if application checks are bypassed")
        void databaseRejectsBlankBody() {
            Project project = persistedProject("Apollo");
            ProjectComment comment = commentRepository.saveAndFlush(
                    ProjectComment.create(project.getId(), author, "Looks good to me"));

            assertThatThrownBy(() -> {
                entityManager.getEntityManager()
                        .createNativeQuery("UPDATE project_comments SET body = '   ' WHERE id = :id")
                        .setParameter("id", comment.getId())
                        .executeUpdate();
                entityManager.flush();
            }).isInstanceOf(ConstraintViolationException.class);
        }

        @Test
        @DisplayName("lists a project's active comments, oldest-first, excluding archived ones")
        void listsActiveCommentsOldestFirst() {
            Project project = persistedProject("Apollo");
            ProjectComment first = commentRepository.saveAndFlush(
                    ProjectComment.create(project.getId(), author, "First"));
            ProjectComment second = commentRepository.saveAndFlush(
                    ProjectComment.create(project.getId(), author, "Second"));
            ProjectComment archived = commentRepository.saveAndFlush(
                    ProjectComment.create(project.getId(), author, "Legacy"));
            archived.archive(owner);
            commentRepository.saveAndFlush(archived);
            entityManager.clear();

            Pageable pageable = PageRequest.of(0, 20, Sort.by(Sort.Direction.ASC, "createdAt"));
            var page = commentRepository.findByProjectIdAndArchivedAtIsNull(project.getId(), pageable);

            assertThat(page.getContent())
                    .extracting(ProjectComment::getBody)
                    .containsExactly("First", "Second");
            assertThat(page.getTotalElements()).isEqualTo(2);
        }
    }
}
