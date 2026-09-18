package com.projectmodule.project.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.projectmodule.collaboration.domain.ActivityType;
import com.projectmodule.collaboration.domain.ProjectActivity;
import com.projectmodule.collaboration.infrastructure.ProjectActivityRepository;
import com.projectmodule.common.context.ActorType;
import com.projectmodule.common.identity.ExternalUserId;
import com.projectmodule.common.identity.OrganizationId;
import com.projectmodule.events.domain.OutboxMessage;
import com.projectmodule.events.infrastructure.OutboxMessageRepository;
import com.projectmodule.project.domain.Project;
import com.projectmodule.project.domain.ProjectMember;
import com.projectmodule.project.domain.ProjectPriority;
import com.projectmodule.project.domain.ProjectRole;
import com.projectmodule.project.domain.ProjectStatus;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;

/**
 * Repository tests for the P0 persistence slice, run against a real PostgreSQL 17 instance.
 *
 * <p>Flyway builds the schema and Hibernate is set to validate rather than generate, so simply
 * starting this context proves the entity mappings match the migration. A mismatch fails here
 * rather than at deployment.
 *
 * <p>Skipped when {@code PROJECTMODULE_TEST_DB_URL} is not set, which keeps the build green on
 * a machine without a database. See {@code src/test/resources/application-test.yml}.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@EnabledIfEnvironmentVariable(named = "PROJECTMODULE_TEST_DB_URL", matches = ".+")
class ProjectPersistenceTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private ProjectRepository projectRepository;

    @Autowired
    private ProjectMemberRepository memberRepository;

    @Autowired
    private ProjectActivityRepository activityRepository;

    @Autowired
    private OutboxMessageRepository outboxRepository;

    private final OrganizationId organization = OrganizationId.of(UUID.randomUUID());
    private final ExternalUserId owner = ExternalUserId.of(UUID.randomUUID());

    private Project persistedProject(String name) {
        Project project = Project.create(organization, name, owner, ProjectPriority.MEDIUM);
        return projectRepository.saveAndFlush(project);
    }

    @Nested
    class ProjectPersistence {

        @Test
        @DisplayName("round-trips every column, including the application-generated identifier")
        void roundTripsProject() {
            Project project = Project.create(organization, "Apollo", owner, ProjectPriority.HIGH);
            project.describe("Lunar programme");
            project.schedule(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31));
            UUID id = project.getId();

            projectRepository.saveAndFlush(project);
            entityManager.clear();

            Project loaded = projectRepository.findById(id).orElseThrow();
            assertThat(loaded.getName()).isEqualTo("Apollo");
            assertThat(loaded.getDescription()).contains("Lunar programme");
            assertThat(loaded.getPriority()).isEqualTo(ProjectPriority.HIGH);
            assertThat(loaded.getStatus()).isEqualTo(ProjectStatus.PLANNING);
            assertThat(loaded.getOrganizationId()).isEqualTo(organization);
            assertThat(loaded.getOwnerId()).isEqualTo(owner);
            assertThat(loaded.getStartDate()).contains(LocalDate.of(2026, 1, 1));
            assertThat(loaded.getCreatedAt()).isNotNull();
            assertThat(loaded.getUpdatedAt()).isNotNull();
        }

        @Test
        @DisplayName("identifiers are version 7, so primary keys stay time-ordered")
        void generatesVersionSevenIdentifiers() {
            assertThat(persistedProject("Ordered").getId().version()).isEqualTo(7);
        }

        @Test
        @DisplayName("archived projects disappear from the active finders but remain stored")
        void softArchiveHidesWithoutDeleting() {
            Project project = persistedProject("Archivable");
            UUID id = project.getId();

            project.archive(owner);
            projectRepository.saveAndFlush(project);
            entityManager.clear();

            assertThat(projectRepository.findByIdAndArchivedAtIsNull(id)).isEmpty();
            assertThat(projectRepository.existsByIdAndArchivedAtIsNull(id)).isFalse();
            // The row itself is still there, with its archive detail intact.
            Project stored = projectRepository.findById(id).orElseThrow();
            assertThat(stored.isArchived()).isTrue();
            assertThat(stored.getArchivedBy()).contains(owner);
        }

        @Test
        @DisplayName("active listings are scoped to one organization")
        void listsActiveProjectsPerOrganization() {
            persistedProject("First");
            persistedProject("Second");
            Project archived = persistedProject("Third");
            archived.archive(owner);
            projectRepository.saveAndFlush(archived);

            OrganizationId otherOrg = OrganizationId.of(UUID.randomUUID());
            projectRepository.saveAndFlush(
                    Project.create(otherOrg, "Elsewhere", owner, ProjectPriority.LOW));
            entityManager.clear();

            assertThat(projectRepository.countByOrganizationIdAndArchivedAtIsNull(
                    organization.value())).isEqualTo(2);
            assertThat(projectRepository.findByOrganizationIdAndArchivedAtIsNull(
                    organization.value(), PageRequest.of(0, 10)).getTotalElements()).isEqualTo(2);
        }

        @Test
        @DisplayName("the version column advances on update, enabling optimistic locking")
        void incrementsVersionOnUpdate() {
            Project project = persistedProject("Versioned");
            long initial = project.getVersion();

            project.rename("Versioned again");
            projectRepository.saveAndFlush(project);

            assertThat(project.getVersion()).isGreaterThan(initial);
        }

        @Test
        @DisplayName("the database rejects a status the application does not define")
        void databaseRejectsUnknownStatus() {
            Project project = persistedProject("Checked");

            // Exercised through the raw EntityManager, not a repository method, so the
            // violation surfaces as Hibernate's own exception rather than being translated
            // to a Spring DataAccessException. See databaseRejectsReversedDates below.
            assertThatThrownBy(() -> {
                entityManager.getEntityManager()
                        .createNativeQuery("UPDATE projects SET status = 'NOT_A_STATUS' WHERE id = :id")
                        .setParameter("id", project.getId())
                        .executeUpdate();
                entityManager.flush();
            }).isInstanceOf(ConstraintViolationException.class);
        }

        @Test
        @DisplayName("the database rejects an end date before the start date")
        void databaseRejectsReversedDates() {
            Project project = persistedProject("Dated");

            // Spring's exception translation (-> DataIntegrityViolationException) is applied
            // by AOP advice around @Repository proxy methods. A manual entityManager.flush()
            // call never passes through that advice, so the native Hibernate exception
            // propagates unwrapped. Contrast with rejectsDuplicateMembership below, which goes
            // through memberRepository.saveAndFlush(...) and is translated as expected.
            assertThatThrownBy(() -> {
                entityManager.getEntityManager()
                        .createNativeQuery("UPDATE projects SET start_date = DATE '2026-05-01', "
                                + "target_end_date = DATE '2026-01-01' WHERE id = :id")
                        .setParameter("id", project.getId())
                        .executeUpdate();
                entityManager.flush();
            }).isInstanceOf(ConstraintViolationException.class);
        }

        @Test
        @DisplayName("the database rejects a half-archived row")
        void databaseRejectsPartialArchiveState() {
            Project project = persistedProject("HalfArchived");

            assertThatThrownBy(() -> {
                entityManager.getEntityManager()
                        .createNativeQuery("UPDATE projects SET archived_at = now() WHERE id = :id")
                        .setParameter("id", project.getId())
                        .executeUpdate();
                entityManager.flush();
            }).isInstanceOf(ConstraintViolationException.class);
        }
    }

    @Nested
    class MembershipPersistence {

        @Test
        @DisplayName("stores each of the four approved roles")
        void storesEveryRole() {
            Project project = persistedProject("Staffed");

            for (ProjectRole role : ProjectRole.values()) {
                memberRepository.saveAndFlush(ProjectMember.join(
                        project.getId(), ExternalUserId.of(UUID.randomUUID()), role));
            }
            entityManager.clear();

            assertThat(memberRepository.findByProjectId(project.getId()))
                    .hasSize(4)
                    .extracting(ProjectMember::getRole)
                    .containsExactlyInAnyOrder(ProjectRole.values());
        }

        @Test
        @DisplayName("finds the membership that authorization will read")
        void findsMembershipForAuthorization() {
            Project project = persistedProject("Guarded");
            ExternalUserId user = ExternalUserId.of(UUID.randomUUID());
            memberRepository.saveAndFlush(
                    ProjectMember.join(project.getId(), user, ProjectRole.MANAGER));
            entityManager.clear();

            assertThat(memberRepository.findByProjectIdAndUserId(project.getId(), user.value()))
                    .get()
                    .extracting(ProjectMember::getRole)
                    .isEqualTo(ProjectRole.MANAGER);
        }

        @Test
        @DisplayName("a user cannot hold two roles on the same project")
        void rejectsDuplicateMembership() {
            Project project = persistedProject("Unique");
            ExternalUserId user = ExternalUserId.of(UUID.randomUUID());
            memberRepository.saveAndFlush(
                    ProjectMember.join(project.getId(), user, ProjectRole.MEMBER));

            assertThatThrownBy(() -> memberRepository.saveAndFlush(
                    ProjectMember.join(project.getId(), user, ProjectRole.VIEWER)))
                    .isInstanceOf(DataIntegrityViolationException.class);
        }

        @Test
        @DisplayName("membership cannot reference a project that does not exist")
        void enforcesForeignKey() {
            assertThatThrownBy(() -> memberRepository.saveAndFlush(ProjectMember.join(
                    UUID.randomUUID(), owner, ProjectRole.MEMBER)))
                    .isInstanceOf(DataIntegrityViolationException.class);
        }

        @Test
        @DisplayName("lists the projects a user belongs to")
        void listsMembershipsForUser() {
            ExternalUserId user = ExternalUserId.of(UUID.randomUUID());
            memberRepository.saveAndFlush(ProjectMember.join(
                    persistedProject("One").getId(), user, ProjectRole.MEMBER));
            memberRepository.saveAndFlush(ProjectMember.join(
                    persistedProject("Two").getId(), user, ProjectRole.VIEWER));
            entityManager.clear();

            assertThat(memberRepository.findByUserId(user.value())).hasSize(2);
        }
    }

    @Nested
    class ActivityPersistence {

        @Test
        @DisplayName("stores an audit entry with a jsonb payload")
        void storesActivityWithJsonPayload() {
            Project project = persistedProject("Audited");
            String payload = "{\"field\":\"name\",\"from\":\"A\",\"to\":\"B\"}";

            activityRepository.saveAndFlush(ProjectActivity.record(
                    project.getId(), ActivityType.PROJECT_UPDATED, ActorType.HUMAN, owner,
                    "Renamed project", payload, "corr-7"));
            entityManager.clear();

            List<ProjectActivity> entries = activityRepository
                    .findByProjectIdOrderByOccurredAtDesc(project.getId(), PageRequest.of(0, 10))
                    .getContent();

            assertThat(entries).hasSize(1);
            assertThat(entries.get(0).getPayload()).isPresent();
            assertThat(entries.get(0).getCorrelationId()).contains("corr-7");
        }

        @Test
        @DisplayName("keeps human, AI and automation actors distinguishable")
        void distinguishesActorTypes() {
            Project project = persistedProject("MixedActors");

            activityRepository.saveAndFlush(ProjectActivity.record(project.getId(),
                    ActivityType.PROJECT_CREATED, ActorType.HUMAN, owner, "by a person", null, null));
            activityRepository.saveAndFlush(ProjectActivity.record(project.getId(),
                    ActivityType.PROJECT_UPDATED, ActorType.AI, owner, "by the AI", null, null));
            activityRepository.saveAndFlush(ProjectActivity.recordSystemAction(project.getId(),
                    ActivityType.PROJECT_ARCHIVED, "by the system", null));
            entityManager.clear();

            assertThat(activityRepository
                    .findByProjectIdOrderByOccurredAtDesc(project.getId(), PageRequest.of(0, 10))
                    .getContent())
                    .extracting(ProjectActivity::getActorType)
                    .containsExactlyInAnyOrder(ActorType.HUMAN, ActorType.AI, ActorType.SYSTEM);
        }

        @Test
        @DisplayName("the database refuses to delete a project whose audit trail survives")
        void auditTrailBlocksHardDelete() {
            Project project = persistedProject("Protected");
            activityRepository.saveAndFlush(ProjectActivity.record(project.getId(),
                    ActivityType.PROJECT_CREATED, ActorType.HUMAN, owner, "Created", null, null));

            // delete() only queues the removal; the DELETE statement and the RESTRICT
            // violation happen on the manual flush() below, which is not a repository proxy
            // call, so the exception is not translated.
            assertThatThrownBy(() -> {
                projectRepository.delete(project);
                entityManager.flush();
            }).isInstanceOf(ConstraintViolationException.class);
        }

        @Test
        @DisplayName("the database refuses an audit entry that names no actor for a human action")
        void databaseRequiresActorForHumanAction() {
            Project project = persistedProject("ActorChecked");

            assertThatThrownBy(() -> {
                entityManager.getEntityManager()
                        .createNativeQuery("INSERT INTO project_activity "
                                + "(id, project_id, activity_type, actor_type, summary, occurred_at) "
                                + "VALUES (:id, :projectId, 'PROJECT_CREATED', 'HUMAN', 'x', now())")
                        .setParameter("id", UUID.randomUUID())
                        .setParameter("projectId", project.getId())
                        .executeUpdate();
                entityManager.flush();
            }).isInstanceOf(ConstraintViolationException.class);
        }
    }

    @Nested
    class OutboxPersistence {

        @Test
        @DisplayName("round-trips an event with its jsonb payload")
        void roundTripsOutboxMessage() {
            OutboxMessage message = OutboxMessage.pending("Project", UUID.randomUUID(),
                    "project.created", "{\"name\":\"Apollo\"}", ActorType.HUMAN, owner, "corr-3");
            UUID id = message.getId();

            outboxRepository.saveAndFlush(message);
            entityManager.clear();

            OutboxMessage loaded = outboxRepository.findById(id).orElseThrow();
            assertThat(loaded.getEventType()).isEqualTo("project.created");
            assertThat(loaded.getPayload()).contains("Apollo");
            assertThat(loaded.getActorType()).contains(ActorType.HUMAN);
            assertThat(loaded.isPublished()).isFalse();
        }

        @Test
        @DisplayName("the publisher query returns unpublished events oldest first")
        void returnsUnpublishedOldestFirst() {
            UUID aggregateId = UUID.randomUUID();
            OutboxMessage first = OutboxMessage.pending("Project", aggregateId,
                    "project.created", "{}", ActorType.HUMAN, owner, null);
            outboxRepository.saveAndFlush(first);
            OutboxMessage second = OutboxMessage.pending("Project", aggregateId,
                    "project.updated", "{}", ActorType.HUMAN, owner, null);
            outboxRepository.saveAndFlush(second);

            first.markPublished();
            outboxRepository.saveAndFlush(first);
            entityManager.clear();

            // The publisher query polls unpublished rows across the whole table by design - a
            // real publisher processes every pending event, not one aggregate's. That means it
            // can also see genuinely unrelated unpublished rows other tests have committed for
            // real against this shared database (for example, the transactional-atomicity test,
            // which commits a real "project.created" event and only rolls back its later
            // update). So this asserts on the production query's actual behaviour - unpublished,
            // ordered oldest first - scoped to the rows this test itself created, rather than on
            // the table's total contents, which this test does not own.
            List<OutboxMessage> pendingForThisAggregate = outboxRepository
                    .findByPublishedAtIsNullOrderByOccurredAtAsc(PageRequest.of(0, 1000))
                    .stream()
                    .filter(message -> message.getAggregateId().equals(aggregateId))
                    .toList();

            assertThat(pendingForThisAggregate).extracting(OutboxMessage::getEventType)
                    .containsExactly("project.updated");
        }

        @Test
        @DisplayName("failed delivery attempts are recorded against the row")
        void recordsFailedAttempts() {
            OutboxMessage message = OutboxMessage.pending("Project", UUID.randomUUID(),
                    "project.archived", "{}", ActorType.SYSTEM, null, null);
            message.markFailed("broker unreachable");
            UUID id = message.getId();

            outboxRepository.saveAndFlush(message);
            entityManager.clear();

            OutboxMessage loaded = outboxRepository.findById(id).orElseThrow();
            assertThat(loaded.getAttemptCount()).isEqualTo(1);
            assertThat(loaded.getLastError()).contains("broker unreachable");
        }

        @Test
        @DisplayName("an event carries no foreign key, so it outlives the aggregate it describes")
        void hasNoForeignKeyToProjects() {
            UUID neverPersisted = UUID.randomUUID();

            outboxRepository.saveAndFlush(OutboxMessage.pending("Project", neverPersisted,
                    "project.created", "{}", ActorType.HUMAN, owner, null));

            assertThat(outboxRepository.findByAggregateIdOrderByOccurredAtAsc(neverPersisted))
                    .hasSize(1);
        }
    }
}
