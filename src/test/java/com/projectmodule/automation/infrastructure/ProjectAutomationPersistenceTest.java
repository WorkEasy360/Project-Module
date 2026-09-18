package com.projectmodule.automation.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.projectmodule.automation.domain.AutomationActionType;
import com.projectmodule.automation.domain.AutomationRun;
import com.projectmodule.automation.domain.ProjectAutomation;
import com.projectmodule.common.identity.ExternalUserId;
import com.projectmodule.common.identity.OrganizationId;
import com.projectmodule.project.domain.Project;
import com.projectmodule.project.domain.ProjectPriority;
import com.projectmodule.project.infrastructure.ProjectRepository;
import java.util.UUID;
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
 * Repository tests for ProjectAutomation and AutomationRun, run against a real PostgreSQL 17
 * instance. Starting this context proves the entity mappings match the {@code V12} migration.
 *
 * <p>Skipped when {@code PROJECTMODULE_TEST_DB_URL} is not set.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@EnabledIfEnvironmentVariable(named = "PROJECTMODULE_TEST_DB_URL", matches = ".+")
class ProjectAutomationPersistenceTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private ProjectRepository projectRepository;

    @Autowired
    private ProjectAutomationRepository projectAutomationRepository;

    @Autowired
    private AutomationRunRepository automationRunRepository;

    private final OrganizationId organization = OrganizationId.of(UUID.randomUUID());
    private final ExternalUserId owner = ExternalUserId.of(UUID.randomUUID());

    private Project persistedProject(String name) {
        Project project = Project.create(organization, name, owner, ProjectPriority.MEDIUM);
        return projectRepository.saveAndFlush(project);
    }

    @Nested
    class ProjectAutomationPersistence {

        @Test
        @DisplayName("round-trips a NOTIFY automation")
        void roundTripsNotifyAutomation() {
            Project project = persistedProject("Apollo");
            UUID recipientId = UUID.randomUUID();
            ProjectAutomation automation = ProjectAutomation.create(project.getId(), "Notify on overdue",
                    "task.overdue", AutomationActionType.NOTIFY, recipientId, null, "A task is overdue");
            UUID id = automation.getId();

            projectAutomationRepository.saveAndFlush(automation);
            entityManager.clear();

            ProjectAutomation loaded = projectAutomationRepository.findById(id).orElseThrow();
            assertThat(loaded.getName()).isEqualTo("Notify on overdue");
            assertThat(loaded.getTriggerEvent()).isEqualTo("task.overdue");
            assertThat(loaded.getActionType()).isEqualTo(AutomationActionType.NOTIFY);
            assertThat(loaded.getActionRecipientId()).contains(ExternalUserId.of(recipientId));
            assertThat(loaded.isEnabled()).isTrue();
        }

        @Test
        @DisplayName("round-trips a CHAT_MESSAGE automation")
        void roundTripsChatMessageAutomation() {
            Project project = persistedProject("Apollo");
            ProjectAutomation automation = ProjectAutomation.create(project.getId(), "Notify chat",
                    "risk.created", AutomationActionType.CHAT_MESSAGE, null, "#risks", "A new risk was created");

            projectAutomationRepository.saveAndFlush(automation);
            entityManager.clear();

            ProjectAutomation loaded = projectAutomationRepository.findById(automation.getId()).orElseThrow();
            assertThat(loaded.getActionChannelReference()).contains("#risks");
        }

        @Test
        @DisplayName("soft-archived automations are still stored, with their archive detail")
        void softArchiveRetainsRow() {
            Project project = persistedProject("Apollo");
            ProjectAutomation automation = projectAutomationRepository.saveAndFlush(
                    ProjectAutomation.create(project.getId(), "Notify on overdue", "task.overdue",
                            AutomationActionType.NOTIFY, UUID.randomUUID(), null, "message"));

            automation.archive(owner);
            projectAutomationRepository.saveAndFlush(automation);
            entityManager.clear();

            ProjectAutomation reloaded = projectAutomationRepository.findById(automation.getId()).orElseThrow();
            assertThat(reloaded.isArchived()).isTrue();
            assertThat(projectAutomationRepository.findByIdAndArchivedAtIsNull(automation.getId())).isEmpty();
        }

        @Test
        @DisplayName("cannot reference a project that does not exist")
        void enforcesProjectForeignKey() {
            ProjectAutomation orphan = ProjectAutomation.create(UUID.randomUUID(), "Orphan", "task.overdue",
                    AutomationActionType.NOTIFY, UUID.randomUUID(), null, "message");

            assertThatThrownBy(() -> projectAutomationRepository.saveAndFlush(orphan))
                    .isInstanceOf(DataIntegrityViolationException.class);
        }

        @Test
        @DisplayName("the dispatcher's lookup query matches enabled, non-archived rules by project and trigger")
        void dispatcherLookupMatchesEnabledActiveRules() {
            Project project = persistedProject("Apollo");
            ProjectAutomation matching = projectAutomationRepository.saveAndFlush(
                    ProjectAutomation.create(project.getId(), "Match", "task.overdue",
                            AutomationActionType.NOTIFY, UUID.randomUUID(), null, "m"));
            ProjectAutomation disabled = ProjectAutomation.create(project.getId(), "Disabled", "task.overdue",
                    AutomationActionType.NOTIFY, UUID.randomUUID(), null, "m");
            disabled.disable();
            projectAutomationRepository.saveAndFlush(disabled);
            ProjectAutomation differentTrigger = projectAutomationRepository.saveAndFlush(
                    ProjectAutomation.create(project.getId(), "Different trigger", "task.blocked",
                            AutomationActionType.NOTIFY, UUID.randomUUID(), null, "m"));
            entityManager.clear();

            assertThat(projectAutomationRepository
                            .findByProjectIdAndTriggerEventAndEnabledIsTrueAndArchivedAtIsNull(
                                    project.getId(), "task.overdue"))
                    .extracting(ProjectAutomation::getName)
                    .containsExactly("Match");
        }
    }

    @Nested
    class AutomationRunPersistence {

        @Test
        @DisplayName("round-trips a SUCCEEDED run")
        void roundTripsSucceededRun() {
            Project project = persistedProject("Apollo");
            ProjectAutomation automation = projectAutomationRepository.saveAndFlush(
                    ProjectAutomation.create(project.getId(), "Notify on overdue", "task.overdue",
                            AutomationActionType.NOTIFY, UUID.randomUUID(), null, "message"));

            AutomationRun run = automationRunRepository.saveAndFlush(
                    AutomationRun.succeeded(automation.getId(), project.getId(), "task.overdue"));
            entityManager.clear();

            AutomationRun loaded = automationRunRepository.findById(run.getId()).orElseThrow();
            assertThat(loaded.getStatus().name()).isEqualTo("SUCCEEDED");
            assertThat(loaded.getErrorMessage()).isEmpty();
        }

        @Test
        @DisplayName("round-trips a FAILED run with its error message")
        void roundTripsFailedRun() {
            Project project = persistedProject("Apollo");
            ProjectAutomation automation = projectAutomationRepository.saveAndFlush(
                    ProjectAutomation.create(project.getId(), "Notify on overdue", "task.overdue",
                            AutomationActionType.NOTIFY, UUID.randomUUID(), null, "message"));

            AutomationRun run = automationRunRepository.saveAndFlush(
                    AutomationRun.failed(automation.getId(), project.getId(), "task.overdue", "boom"));
            entityManager.clear();

            AutomationRun loaded = automationRunRepository.findById(run.getId()).orElseThrow();
            assertThat(loaded.getErrorMessage()).contains("boom");
        }

        @Test
        @DisplayName("cannot reference an automation that does not exist")
        void enforcesAutomationForeignKey() {
            AutomationRun orphan = AutomationRun.succeeded(UUID.randomUUID(), UUID.randomUUID(), "task.overdue");

            assertThatThrownBy(() -> automationRunRepository.saveAndFlush(orphan))
                    .isInstanceOf(DataIntegrityViolationException.class);
        }

        @Test
        @DisplayName("lists an automation's runs, most recent first")
        void listsRunsMostRecentFirst() throws InterruptedException {
            Project project = persistedProject("Apollo");
            ProjectAutomation automation = projectAutomationRepository.saveAndFlush(
                    ProjectAutomation.create(project.getId(), "Notify on overdue", "task.overdue",
                            AutomationActionType.NOTIFY, UUID.randomUUID(), null, "message"));

            automationRunRepository.saveAndFlush(
                    AutomationRun.succeeded(automation.getId(), project.getId(), "task.overdue"));
            Thread.sleep(5);
            AutomationRun second = automationRunRepository.saveAndFlush(
                    AutomationRun.failed(automation.getId(), project.getId(), "task.overdue", "boom"));
            entityManager.clear();

            assertThat(automationRunRepository.findByAutomationIdOrderByExecutedAtDesc(automation.getId()))
                    .extracting(AutomationRun::getId)
                    .first()
                    .isEqualTo(second.getId());
        }
    }
}
