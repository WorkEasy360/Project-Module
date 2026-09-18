package com.projectmodule.work.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.projectmodule.common.identity.ExternalUserId;
import com.projectmodule.common.identity.OrganizationId;
import com.projectmodule.project.domain.Project;
import com.projectmodule.project.domain.ProjectPriority;
import com.projectmodule.project.infrastructure.ProjectRepository;
import com.projectmodule.work.domain.CustomFieldValueType;
import com.projectmodule.work.domain.ProjectCustomField;
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
import org.springframework.test.context.ActiveProfiles;

/**
 * Repository tests for ProjectCustomField, run against a real PostgreSQL 17 instance — the same
 * conventions {@code DecisionPersistenceTest}/{@code ProjectCommentPersistenceTest} already
 * establish. Starting this context proves the entity mapping matches the {@code V9} migration,
 * including its database-level {@code CHECK} constraints and partial unique index.
 *
 * <p>Skipped when {@code PROJECTMODULE_TEST_DB_URL} is not set.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@EnabledIfEnvironmentVariable(named = "PROJECTMODULE_TEST_DB_URL", matches = ".+")
class ProjectCustomFieldPersistenceTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private ProjectRepository projectRepository;

    @Autowired
    private ProjectCustomFieldRepository customFieldRepository;

    private final OrganizationId organization = OrganizationId.of(UUID.randomUUID());
    private final ExternalUserId owner = ExternalUserId.of(UUID.randomUUID());

    private Project persistedProject(String name) {
        Project project = Project.create(organization, name, owner, ProjectPriority.MEDIUM);
        return projectRepository.saveAndFlush(project);
    }

    @Nested
    class CustomFieldPersistence {

        @Test
        @DisplayName("round-trips a custom field, including its type and value")
        void roundTripsCustomField() {
            Project project = persistedProject("Apollo");
            ProjectCustomField field = ProjectCustomField.create(
                    project.getId(), "Client Code", CustomFieldValueType.TEXT, "ACME-01");
            UUID id = field.getId();

            customFieldRepository.saveAndFlush(field);
            entityManager.clear();

            ProjectCustomField loaded = customFieldRepository.findById(id).orElseThrow();
            assertThat(loaded.getName()).isEqualTo("Client Code");
            assertThat(loaded.getProjectId()).isEqualTo(project.getId());
            assertThat(loaded.getValueType()).isEqualTo(CustomFieldValueType.TEXT);
            assertThat(loaded.getValue()).isEqualTo("ACME-01");
        }

        @Test
        @DisplayName("soft-archived custom fields are still stored, with their archive detail")
        void softArchiveRetainsRow() {
            Project project = persistedProject("Apollo");
            ProjectCustomField field = customFieldRepository.saveAndFlush(
                    ProjectCustomField.create(project.getId(), "Client Code", CustomFieldValueType.TEXT, "ACME-01"));

            field.archive(owner);
            customFieldRepository.saveAndFlush(field);
            entityManager.clear();

            ProjectCustomField reloaded = customFieldRepository.findById(field.getId()).orElseThrow();
            assertThat(reloaded.isArchived()).isTrue();
            assertThat(customFieldRepository.findByIdAndArchivedAtIsNull(field.getId())).isEmpty();
        }

        @Test
        @DisplayName("cannot reference a project that does not exist")
        void enforcesProjectForeignKey() {
            ProjectCustomField field = ProjectCustomField.create(
                    UUID.randomUUID(), "Orphan", CustomFieldValueType.TEXT, "value");

            assertThatThrownBy(() -> customFieldRepository.saveAndFlush(field))
                    .isInstanceOf(DataIntegrityViolationException.class);
        }

        @Test
        @DisplayName("the database rejects a value_type the application does not define")
        void databaseRejectsUnknownValueType() {
            Project project = persistedProject("Apollo");
            ProjectCustomField field = customFieldRepository.saveAndFlush(
                    ProjectCustomField.create(project.getId(), "Client Code", CustomFieldValueType.TEXT, "ACME-01"));

            assertThatThrownBy(() -> {
                entityManager.getEntityManager()
                        .createNativeQuery("UPDATE project_custom_fields SET value_type = 'NOT_A_TYPE' WHERE id = :id")
                        .setParameter("id", field.getId())
                        .executeUpdate();
                entityManager.flush();
            }).isInstanceOf(ConstraintViolationException.class);
        }

        @Test
        @DisplayName("the database rejects a blank value even if application checks are bypassed")
        void databaseRejectsBlankValue() {
            Project project = persistedProject("Apollo");
            ProjectCustomField field = customFieldRepository.saveAndFlush(
                    ProjectCustomField.create(project.getId(), "Client Code", CustomFieldValueType.TEXT, "ACME-01"));

            assertThatThrownBy(() -> {
                entityManager.getEntityManager()
                        .createNativeQuery("UPDATE project_custom_fields SET value = '   ' WHERE id = :id")
                        .setParameter("id", field.getId())
                        .executeUpdate();
                entityManager.flush();
            }).isInstanceOf(ConstraintViolationException.class);
        }

        @Test
        @DisplayName("the partial unique index rejects a second active field with the same name in the same project")
        void rejectsDuplicateActiveNameInSameProject() {
            Project project = persistedProject("Apollo");
            customFieldRepository.saveAndFlush(
                    ProjectCustomField.create(project.getId(), "Client Code", CustomFieldValueType.TEXT, "ACME-01"));
            ProjectCustomField duplicate = ProjectCustomField.create(
                    project.getId(), "Client Code", CustomFieldValueType.NUMBER, "42");

            assertThatThrownBy(() -> customFieldRepository.saveAndFlush(duplicate))
                    .isInstanceOf(DataIntegrityViolationException.class);
        }

        @Test
        @DisplayName("the same name is allowed again once the original is archived")
        void allowsNameReuseAfterArchive() {
            Project project = persistedProject("Apollo");
            ProjectCustomField original = customFieldRepository.saveAndFlush(
                    ProjectCustomField.create(project.getId(), "Client Code", CustomFieldValueType.TEXT, "ACME-01"));
            original.archive(owner);
            customFieldRepository.saveAndFlush(original);

            ProjectCustomField recreated = ProjectCustomField.create(
                    project.getId(), "Client Code", CustomFieldValueType.NUMBER, "42");

            customFieldRepository.saveAndFlush(recreated);
            entityManager.clear();

            assertThat(customFieldRepository.findById(recreated.getId())).isPresent();
        }

        @Test
        @DisplayName("the same name is allowed across different projects")
        void allowsSameNameAcrossDifferentProjects() {
            Project projectA = persistedProject("Apollo");
            Project projectB = persistedProject("Artemis");
            customFieldRepository.saveAndFlush(
                    ProjectCustomField.create(projectA.getId(), "Client Code", CustomFieldValueType.TEXT, "ACME-01"));
            ProjectCustomField fieldB = ProjectCustomField.create(
                    projectB.getId(), "Client Code", CustomFieldValueType.TEXT, "OTHER-01");

            customFieldRepository.saveAndFlush(fieldB);
            entityManager.clear();

            assertThat(customFieldRepository.findById(fieldB.getId())).isPresent();
        }

        @Test
        @DisplayName("lists a project's active custom fields, excluding archived ones")
        void listsActiveCustomFields() {
            Project project = persistedProject("Apollo");
            customFieldRepository.saveAndFlush(
                    ProjectCustomField.create(project.getId(), "Client Code", CustomFieldValueType.TEXT, "ACME-01"));
            ProjectCustomField archived = customFieldRepository.saveAndFlush(
                    ProjectCustomField.create(project.getId(), "Legacy Field", CustomFieldValueType.TEXT, "old"));
            archived.archive(owner);
            customFieldRepository.saveAndFlush(archived);
            entityManager.clear();

            assertThat(customFieldRepository.findByProjectIdAndArchivedAtIsNullOrderByCreatedAtAsc(project.getId()))
                    .extracting(ProjectCustomField::getName)
                    .containsExactly("Client Code");
        }
    }
}
