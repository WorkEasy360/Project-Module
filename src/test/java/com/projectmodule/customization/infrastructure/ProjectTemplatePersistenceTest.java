package com.projectmodule.customization.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.projectmodule.common.identity.ExternalUserId;
import com.projectmodule.common.identity.OrganizationId;
import com.projectmodule.customization.domain.ProjectTemplate;
import com.projectmodule.project.domain.ProjectPriority;
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
import org.springframework.test.context.ActiveProfiles;

/**
 * Repository tests for ProjectTemplate, run against a real PostgreSQL 17 instance — the same
 * conventions {@code DecisionPersistenceTest}/{@code ProjectCustomFieldPersistenceTest} already
 * establish. Starting this context proves the entity mapping matches the {@code V10} migration,
 * including its database-level {@code CHECK} constraints and partial unique index.
 *
 * <p>Skipped when {@code PROJECTMODULE_TEST_DB_URL} is not set. Note: {@code project_templates}
 * has no foreign key to {@code projects} (it is organization-scoped, not project-scoped), so
 * unlike every prior persistence test in this module, no {@code Project} row needs to be
 * persisted first.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@EnabledIfEnvironmentVariable(named = "PROJECTMODULE_TEST_DB_URL", matches = ".+")
class ProjectTemplatePersistenceTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private ProjectTemplateRepository templateRepository;

    private final OrganizationId organization = OrganizationId.of(UUID.randomUUID());
    private final ExternalUserId owner = ExternalUserId.of(UUID.randomUUID());

    @Nested
    class TemplatePersistence {

        @Test
        @DisplayName("round-trips a template, including its default priority")
        void roundTripsTemplate() {
            ProjectTemplate template = ProjectTemplate.create(organization, "Standard Sprint", ProjectPriority.MEDIUM);
            UUID id = template.getId();

            templateRepository.saveAndFlush(template);
            entityManager.clear();

            ProjectTemplate loaded = templateRepository.findById(id).orElseThrow();
            assertThat(loaded.getName()).isEqualTo("Standard Sprint");
            assertThat(loaded.getOrganizationId()).isEqualTo(organization);
            assertThat(loaded.getDefaultPriority()).contains(ProjectPriority.MEDIUM);
        }

        @Test
        @DisplayName("a template may be persisted with no defaultPriority at all")
        void persistsWithoutDefaultPriority() {
            ProjectTemplate template = ProjectTemplate.create(organization, "Bare Preset", null);

            templateRepository.saveAndFlush(template);
            entityManager.clear();

            assertThat(templateRepository.findById(template.getId()).orElseThrow().getDefaultPriority()).isEmpty();
        }

        @Test
        @DisplayName("soft-archived templates are still stored, with their archive detail")
        void softArchiveRetainsRow() {
            ProjectTemplate template = templateRepository.saveAndFlush(
                    ProjectTemplate.create(organization, "Standard Sprint", ProjectPriority.MEDIUM));

            template.archive(owner);
            templateRepository.saveAndFlush(template);
            entityManager.clear();

            ProjectTemplate reloaded = templateRepository.findById(template.getId()).orElseThrow();
            assertThat(reloaded.isArchived()).isTrue();
            assertThat(templateRepository.findByIdAndArchivedAtIsNull(template.getId())).isEmpty();
        }

        @Test
        @DisplayName("the database rejects a default_priority the application does not define")
        void databaseRejectsUnknownDefaultPriority() {
            ProjectTemplate template = templateRepository.saveAndFlush(
                    ProjectTemplate.create(organization, "Standard Sprint", ProjectPriority.MEDIUM));

            assertThatThrownBy(() -> {
                entityManager.getEntityManager()
                        .createNativeQuery("UPDATE project_templates SET default_priority = 'NOT_A_PRIORITY' WHERE id = :id")
                        .setParameter("id", template.getId())
                        .executeUpdate();
                entityManager.flush();
            }).isInstanceOf(ConstraintViolationException.class);
        }

        @Test
        @DisplayName("the database rejects a blank name even if application checks are bypassed")
        void databaseRejectsBlankName() {
            ProjectTemplate template = templateRepository.saveAndFlush(
                    ProjectTemplate.create(organization, "Standard Sprint", ProjectPriority.MEDIUM));

            assertThatThrownBy(() -> {
                entityManager.getEntityManager()
                        .createNativeQuery("UPDATE project_templates SET name = '   ' WHERE id = :id")
                        .setParameter("id", template.getId())
                        .executeUpdate();
                entityManager.flush();
            }).isInstanceOf(ConstraintViolationException.class);
        }

        @Test
        @DisplayName("the partial unique index rejects a second active template with the same name in the same organization")
        void rejectsDuplicateActiveNameInSameOrganization() {
            templateRepository.saveAndFlush(
                    ProjectTemplate.create(organization, "Standard Sprint", ProjectPriority.MEDIUM));
            ProjectTemplate duplicate = ProjectTemplate.create(organization, "Standard Sprint", ProjectPriority.HIGH);

            assertThatThrownBy(() -> templateRepository.saveAndFlush(duplicate))
                    .isInstanceOf(DataIntegrityViolationException.class);
        }

        @Test
        @DisplayName("the same name is allowed again once the original is archived")
        void allowsNameReuseAfterArchive() {
            ProjectTemplate original = templateRepository.saveAndFlush(
                    ProjectTemplate.create(organization, "Standard Sprint", ProjectPriority.MEDIUM));
            original.archive(owner);
            templateRepository.saveAndFlush(original);

            ProjectTemplate recreated = ProjectTemplate.create(organization, "Standard Sprint", ProjectPriority.HIGH);

            templateRepository.saveAndFlush(recreated);
            entityManager.clear();

            assertThat(templateRepository.findById(recreated.getId())).isPresent();
        }

        @Test
        @DisplayName("the same name is allowed across different organizations")
        void allowsSameNameAcrossDifferentOrganizations() {
            OrganizationId otherOrg = OrganizationId.of(UUID.randomUUID());
            templateRepository.saveAndFlush(
                    ProjectTemplate.create(organization, "Standard Sprint", ProjectPriority.MEDIUM));
            ProjectTemplate otherOrgTemplate = ProjectTemplate.create(otherOrg, "Standard Sprint", ProjectPriority.LOW);

            templateRepository.saveAndFlush(otherOrgTemplate);
            entityManager.clear();

            assertThat(templateRepository.findById(otherOrgTemplate.getId())).isPresent();
        }

        @Test
        @DisplayName("lists a page of an organization's active templates, excluding archived ones")
        void listsActiveTemplates() {
            templateRepository.saveAndFlush(
                    ProjectTemplate.create(organization, "Standard Sprint", ProjectPriority.MEDIUM));
            ProjectTemplate archived = templateRepository.saveAndFlush(
                    ProjectTemplate.create(organization, "Legacy Preset", ProjectPriority.LOW));
            archived.archive(owner);
            templateRepository.saveAndFlush(archived);
            entityManager.clear();

            Pageable pageable = PageRequest.of(0, 20);
            assertThat(templateRepository.findByOrganizationIdAndArchivedAtIsNull(organization.value(), pageable)
                    .getContent())
                    .extracting(ProjectTemplate::getName)
                    .containsExactly("Standard Sprint");
        }
    }
}
