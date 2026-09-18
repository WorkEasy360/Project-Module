package com.projectmodule.project.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.projectmodule.common.context.ActorType;
import com.projectmodule.common.context.ImmutableRequestContext;
import com.projectmodule.common.context.RequestContext;
import com.projectmodule.common.exception.AuthorizationException;
import com.projectmodule.common.exception.ConflictException;
import com.projectmodule.common.exception.MissingIdentityException;
import com.projectmodule.common.exception.ResourceNotFoundException;
import com.projectmodule.common.identity.ExternalUserId;
import com.projectmodule.common.identity.OrganizationId;
import com.projectmodule.project.api.dto.CreateProjectRequest;
import com.projectmodule.project.api.dto.UpdateProjectRequest;
import com.projectmodule.project.application.authorization.ProjectAuthorizationService;
import com.projectmodule.project.application.authorization.ProjectPermission;
import com.projectmodule.project.domain.Project;
import com.projectmodule.project.domain.ProjectMember;
import com.projectmodule.project.domain.ProjectPriority;
import com.projectmodule.project.domain.ProjectRole;
import com.projectmodule.project.domain.ProjectStatus;
import com.projectmodule.project.domain.event.ProjectArchived;
import com.projectmodule.project.domain.event.ProjectCreated;
import com.projectmodule.project.domain.event.ProjectUpdated;
import com.projectmodule.project.infrastructure.ProjectMemberRepository;
import com.projectmodule.project.infrastructure.ProjectRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

@ExtendWith(MockitoExtension.class)
class ProjectApplicationServiceTest {

    private static final OrganizationId ORG = OrganizationId.of(UUID.randomUUID());
    private static final ExternalUserId USER = ExternalUserId.of(UUID.randomUUID());

    @Mock
    private ProjectRepository projectRepository;

    @Mock
    private ProjectMemberRepository projectMemberRepository;

    @Mock
    private ProjectAuthorizationService projectAuthorizationService;

    @Mock
    private ProjectEventRecorder projectEventRecorder;

    private ProjectApplicationService service;
    private RequestContext context;

    @BeforeEach
    void setUp() {
        service = new ProjectApplicationService(projectRepository, projectMemberRepository,
                projectAuthorizationService, projectEventRecorder);
        context = new ImmutableRequestContext(
                Optional.of(USER), Optional.of(ORG), "corr-1", ActorType.HUMAN);
    }

    private static CreateProjectRequest createRequest() {
        return new CreateProjectRequest("Apollo", "Lunar programme", ProjectPriority.HIGH,
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31));
    }

    @Nested
    class Create {

        @Test
        @DisplayName("builds the project through the domain factory and persists it")
        void createsProject() {
            when(projectRepository.save(any(Project.class))).thenAnswer(inv -> inv.getArgument(0));

            Project created = service.createProject(context, createRequest());

            assertThat(created.getName()).isEqualTo("Apollo");
            assertThat(created.getOrganizationId()).isEqualTo(ORG);
            assertThat(created.getOwnerId()).isEqualTo(USER);
            assertThat(created.getPriority()).isEqualTo(ProjectPriority.HIGH);
            assertThat(created.getStatus()).isEqualTo(ProjectStatus.PLANNING);
            assertThat(created.getStartDate()).contains(LocalDate.of(2026, 1, 1));
            verify(projectRepository).save(any(Project.class));
        }

        @Test
        @DisplayName("records a project.created event after the project is saved")
        void recordsCreatedEvent() {
            when(projectRepository.save(any(Project.class))).thenAnswer(inv -> inv.getArgument(0));

            Project created = service.createProject(context, createRequest());

            org.mockito.ArgumentCaptor<com.projectmodule.project.domain.event.ProjectDomainEvent> captor =
                    org.mockito.ArgumentCaptor.forClass(
                            com.projectmodule.project.domain.event.ProjectDomainEvent.class);
            verify(projectEventRecorder).record(eq(context), eq(created), captor.capture());

            assertThat(captor.getValue()).isInstanceOf(ProjectCreated.class);
            assertThat(captor.getValue().projectId()).isEqualTo(created.getId());
            // The event is pulled and cleared once recorded - it does not linger and get
            // re-recorded by a later operation on the same in-memory instance.
            assertThat(created.pullDomainEvents()).isEmpty();
        }

        @Test
        @DisplayName("also creates an OWNER membership for the creator")
        void createsOwnerMembership() {
            when(projectRepository.save(any(Project.class))).thenAnswer(inv -> inv.getArgument(0));

            Project created = service.createProject(context, createRequest());

            org.mockito.ArgumentCaptor<ProjectMember> captor =
                    org.mockito.ArgumentCaptor.forClass(ProjectMember.class);
            verify(projectMemberRepository).save(captor.capture());
            ProjectMember savedMember = captor.getValue();

            assertThat(savedMember.getProjectId()).isEqualTo(created.getId());
            assertThat(savedMember.getUserId()).isEqualTo(USER);
            assertThat(savedMember.getRole()).isEqualTo(ProjectRole.OWNER);
        }

        @Test
        @DisplayName("refuses to create without an organization in context")
        void requiresOrganization() {
            RequestContext anonymous = new ImmutableRequestContext(
                    Optional.of(USER), Optional.empty(), "corr-2", ActorType.HUMAN);

            assertThatThrownBy(() -> service.createProject(anonymous, createRequest()))
                    .isInstanceOf(MissingIdentityException.class);
            verify(projectRepository, never()).save(any());
            verify(projectMemberRepository, never()).save(any());
        }

        @Test
        @DisplayName("refuses to create without a caller identity")
        void requiresCaller() {
            RequestContext noUser = new ImmutableRequestContext(
                    Optional.empty(), Optional.of(ORG), "corr-3", ActorType.HUMAN);

            assertThatThrownBy(() -> service.createProject(noUser, createRequest()))
                    .isInstanceOf(MissingIdentityException.class);
        }

        @Test
        @DisplayName("rejects a blank name through the domain, not silently")
        void rejectsInvalidNameViaDomain() {
            CreateProjectRequest invalid = new CreateProjectRequest(
                    "   ", null, ProjectPriority.LOW, null, null);

            assertThatThrownBy(() -> service.createProject(context, invalid))
                    .isInstanceOf(com.projectmodule.common.exception.ValidationException.class);
            verify(projectRepository, never()).save(any());
            verify(projectEventRecorder, never()).record(any(), any(), any());
        }
    }

    @Nested
    class Get {

        @Test
        @DisplayName("returns a project the caller has VIEW_PROJECT on")
        void returnsOwnedProject() {
            Project project = Project.create(ORG, "Apollo", USER, ProjectPriority.MEDIUM);
            when(projectRepository.findByIdAndArchivedAtIsNull(project.getId()))
                    .thenReturn(Optional.of(project));

            assertThat(service.getProject(context, project.getId())).isEqualTo(project);
            verify(projectAuthorizationService).requirePermission(
                    USER, project.getId(), ProjectPermission.VIEW_PROJECT);
        }

        @Test
        @DisplayName("reports not found when the project does not exist")
        void reportsNotFound() {
            UUID id = UUID.randomUUID();
            when(projectRepository.findByIdAndArchivedAtIsNull(id)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.getProject(context, id))
                    .isInstanceOf(ResourceNotFoundException.class);
        }

        @Test
        @DisplayName("reports not found for a project in a different organization")
        void reportsNotFoundAcrossTenants() {
            OrganizationId otherOrg = OrganizationId.of(UUID.randomUUID());
            Project project = Project.create(otherOrg, "Elsewhere", USER, ProjectPriority.LOW);
            when(projectRepository.findByIdAndArchivedAtIsNull(project.getId()))
                    .thenReturn(Optional.of(project));

            assertThatThrownBy(() -> service.getProject(context, project.getId()))
                    .isInstanceOf(ResourceNotFoundException.class);
        }

        @Test
        @DisplayName("reports not found for an archived project, the same as a deleted one")
        void archivedProjectIsNotFound() {
            // The repository's own finder excludes archived rows; a mocked empty result is
            // exactly what it returns once a project has been archived.
            UUID id = UUID.randomUUID();
            when(projectRepository.findByIdAndArchivedAtIsNull(id)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.getProject(context, id))
                    .isInstanceOf(ResourceNotFoundException.class);
        }

        @Test
        @DisplayName("a caller in the right organization but without VIEW_PROJECT is forbidden, not 404")
        void deniesGetWithoutPermission() {
            Project project = Project.create(ORG, "Apollo", USER, ProjectPriority.MEDIUM);
            when(projectRepository.findByIdAndArchivedAtIsNull(project.getId()))
                    .thenReturn(Optional.of(project));
            doThrow(new AuthorizationException("denied"))
                    .when(projectAuthorizationService)
                    .requirePermission(USER, project.getId(), ProjectPermission.VIEW_PROJECT);

            assertThatThrownBy(() -> service.getProject(context, project.getId()))
                    .isInstanceOf(AuthorizationException.class);
        }
    }

    @Nested
    class List_ {

        @Test
        @DisplayName("lists only the caller's organization, paginated")
        void listsScopedToOrganization() {
            Project project = Project.create(ORG, "Apollo", USER, ProjectPriority.MEDIUM);
            Pageable pageable = PageRequest.of(0, 20);
            Page<Project> page = new PageImpl<>(List.of(project), pageable, 1);
            when(projectRepository.findByOrganizationIdAndArchivedAtIsNull(ORG.value(), pageable))
                    .thenReturn(page);

            Page<Project> result = service.listProjects(context, pageable);

            assertThat(result.getContent()).containsExactly(project);
            verify(projectRepository).findByOrganizationIdAndArchivedAtIsNull(eq(ORG.value()), eq(pageable));
        }

        @Test
        @DisplayName("refuses to list without an organization in context")
        void requiresOrganization() {
            RequestContext anonymous = new ImmutableRequestContext(
                    Optional.of(USER), Optional.empty(), "corr-4", ActorType.HUMAN);

            assertThatThrownBy(() -> service.listProjects(anonymous, PageRequest.of(0, 20)))
                    .isInstanceOf(MissingIdentityException.class);
        }
    }

    @Nested
    class Update {

        @Test
        @DisplayName("applies only the fields supplied, through domain behaviour")
        void appliesSuppliedFieldsOnly() {
            Project project = Project.create(ORG, "Apollo", USER, ProjectPriority.MEDIUM);
            project.describe("Original description");
            when(projectRepository.findByIdAndArchivedAtIsNull(project.getId()))
                    .thenReturn(Optional.of(project));
            when(projectRepository.save(any(Project.class))).thenAnswer(inv -> inv.getArgument(0));

            UpdateProjectRequest request = new UpdateProjectRequest(
                    "Apollo Renamed", null, null, null, null, null, project.getVersion());

            Project updated = service.updateProject(context, project.getId(), request);

            assertThat(updated.getName()).isEqualTo("Apollo Renamed");
            // Left untouched because the request supplied null for it.
            assertThat(updated.getDescription()).contains("Original description");
            assertThat(updated.getPriority()).isEqualTo(ProjectPriority.MEDIUM);
            verify(projectAuthorizationService).requirePermission(
                    USER, project.getId(), ProjectPermission.EDIT_PROJECT);
        }

        @Test
        @DisplayName("records exactly one project.updated event, even when several fields change")
        void recordsUpdatedEvent() {
            Project project = Project.create(ORG, "Apollo", USER, ProjectPriority.LOW);
            project.pullDomainEvents(); // discard the creation event: only update() is under test
            when(projectRepository.findByIdAndArchivedAtIsNull(project.getId()))
                    .thenReturn(Optional.of(project));
            when(projectRepository.save(any(Project.class))).thenAnswer(inv -> inv.getArgument(0));

            UpdateProjectRequest request = new UpdateProjectRequest(
                    "Apollo Renamed", "New description", ProjectStatus.ACTIVE,
                    ProjectPriority.CRITICAL, null, null, project.getVersion());

            Project updated = service.updateProject(context, project.getId(), request);

            org.mockito.ArgumentCaptor<com.projectmodule.project.domain.event.ProjectDomainEvent> captor =
                    org.mockito.ArgumentCaptor.forClass(
                            com.projectmodule.project.domain.event.ProjectDomainEvent.class);
            verify(projectEventRecorder, org.mockito.Mockito.times(1))
                    .record(eq(context), eq(updated), captor.capture());
            assertThat(captor.getValue()).isInstanceOf(ProjectUpdated.class);
            assertThat(captor.getValue().projectId()).isEqualTo(project.getId());
        }

        @Test
        @DisplayName("applies a status and priority change")
        void appliesStatusAndPriority() {
            Project project = Project.create(ORG, "Apollo", USER, ProjectPriority.LOW);
            when(projectRepository.findByIdAndArchivedAtIsNull(project.getId()))
                    .thenReturn(Optional.of(project));
            when(projectRepository.save(any(Project.class))).thenAnswer(inv -> inv.getArgument(0));

            UpdateProjectRequest request = new UpdateProjectRequest(
                    null, null, ProjectStatus.ACTIVE, ProjectPriority.CRITICAL, null, null,
                    project.getVersion());

            Project updated = service.updateProject(context, project.getId(), request);

            assertThat(updated.getStatus()).isEqualTo(ProjectStatus.ACTIVE);
            assertThat(updated.getPriority()).isEqualTo(ProjectPriority.CRITICAL);
        }

        @Test
        @DisplayName("changing only the start date preserves the existing end date")
        void preservesUnsuppliedDateField() {
            Project project = Project.create(ORG, "Apollo", USER, ProjectPriority.MEDIUM);
            project.schedule(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 6, 30));
            when(projectRepository.findByIdAndArchivedAtIsNull(project.getId()))
                    .thenReturn(Optional.of(project));
            when(projectRepository.save(any(Project.class))).thenAnswer(inv -> inv.getArgument(0));

            UpdateProjectRequest request = new UpdateProjectRequest(
                    null, null, null, null, LocalDate.of(2026, 2, 1), null, project.getVersion());

            Project updated = service.updateProject(context, project.getId(), request);

            assertThat(updated.getStartDate()).contains(LocalDate.of(2026, 2, 1));
            assertThat(updated.getTargetEndDate()).contains(LocalDate.of(2026, 6, 30));
        }

        @Test
        @DisplayName("rejects an update against a stale version")
        void rejectsStaleVersion() {
            Project project = Project.create(ORG, "Apollo", USER, ProjectPriority.MEDIUM);
            when(projectRepository.findByIdAndArchivedAtIsNull(project.getId()))
                    .thenReturn(Optional.of(project));

            UpdateProjectRequest request = new UpdateProjectRequest(
                    "New name", null, null, null, null, null, project.getVersion() + 1);

            assertThatThrownBy(() -> service.updateProject(context, project.getId(), request))
                    .isInstanceOf(ConflictException.class);
            verify(projectRepository, never()).save(any());
            verify(projectEventRecorder, never()).record(any(), any(), any());
        }

        @Test
        @DisplayName("reports not found rather than applying an update to a missing project")
        void reportsNotFoundOnMissingProject() {
            UUID id = UUID.randomUUID();
            when(projectRepository.findByIdAndArchivedAtIsNull(id)).thenReturn(Optional.empty());

            UpdateProjectRequest request = new UpdateProjectRequest(
                    "New name", null, null, null, null, null, 0L);

            assertThatThrownBy(() -> service.updateProject(context, id, request))
                    .isInstanceOf(ResourceNotFoundException.class);
            verify(projectEventRecorder, never()).record(any(), any(), any());
        }

        @Test
        @DisplayName("a VIEWER-level caller (no EDIT_PROJECT) is forbidden from updating")
        void deniesUpdateWithoutPermission() {
            Project project = Project.create(ORG, "Apollo", USER, ProjectPriority.MEDIUM);
            when(projectRepository.findByIdAndArchivedAtIsNull(project.getId()))
                    .thenReturn(Optional.of(project));
            doThrow(new AuthorizationException("denied"))
                    .when(projectAuthorizationService)
                    .requirePermission(USER, project.getId(), ProjectPermission.EDIT_PROJECT);

            UpdateProjectRequest request = new UpdateProjectRequest(
                    "New name", null, null, null, null, null, project.getVersion());

            assertThatThrownBy(() -> service.updateProject(context, project.getId(), request))
                    .isInstanceOf(AuthorizationException.class);
            verify(projectRepository, never()).save(any());
            verify(projectEventRecorder, never()).record(any(), any(), any());
        }
    }

    @Nested
    class Archive {

        @Test
        @DisplayName("archives the project and records who did it")
        void archivesProject() {
            Project project = Project.create(ORG, "Apollo", USER, ProjectPriority.MEDIUM);
            when(projectRepository.findByIdAndArchivedAtIsNull(project.getId()))
                    .thenReturn(Optional.of(project));
            when(projectRepository.save(any(Project.class))).thenAnswer(inv -> inv.getArgument(0));

            service.archiveProject(context, project.getId());

            assertThat(project.isArchived()).isTrue();
            assertThat(project.getArchivedBy()).contains(USER);
            verify(projectRepository).save(project);
            verify(projectAuthorizationService).requirePermission(
                    USER, project.getId(), ProjectPermission.ARCHIVE_PROJECT);
        }

        @Test
        @DisplayName("records a project.archived event after the project is saved")
        void recordsArchivedEvent() {
            Project project = Project.create(ORG, "Apollo", USER, ProjectPriority.MEDIUM);
            project.pullDomainEvents(); // discard the creation event: only archive() is under test
            when(projectRepository.findByIdAndArchivedAtIsNull(project.getId()))
                    .thenReturn(Optional.of(project));
            when(projectRepository.save(any(Project.class))).thenAnswer(inv -> inv.getArgument(0));

            service.archiveProject(context, project.getId());

            org.mockito.ArgumentCaptor<com.projectmodule.project.domain.event.ProjectDomainEvent> captor =
                    org.mockito.ArgumentCaptor.forClass(
                            com.projectmodule.project.domain.event.ProjectDomainEvent.class);
            verify(projectEventRecorder).record(eq(context), eq(project), captor.capture());
            assertThat(captor.getValue()).isInstanceOf(ProjectArchived.class);
            assertThat(captor.getValue().projectId()).isEqualTo(project.getId());
        }

        @Test
        @DisplayName("archiving an already-archived project reports not found, not a conflict")
        void archivingMissingProjectReportsNotFound() {
            // Once archived, the repository's own finder no longer returns the row - the same
            // path a caller hits after a genuine double DELETE.
            UUID id = UUID.randomUUID();
            when(projectRepository.findByIdAndArchivedAtIsNull(id)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.archiveProject(context, id))
                    .isInstanceOf(ResourceNotFoundException.class);
            verify(projectEventRecorder, never()).record(any(), any(), any());
        }

        @Test
        @DisplayName("a MANAGER (no ARCHIVE_PROJECT) is forbidden from archiving")
        void deniesArchiveWithoutPermission() {
            Project project = Project.create(ORG, "Apollo", USER, ProjectPriority.MEDIUM);
            when(projectRepository.findByIdAndArchivedAtIsNull(project.getId()))
                    .thenReturn(Optional.of(project));
            doThrow(new AuthorizationException("denied"))
                    .when(projectAuthorizationService)
                    .requirePermission(USER, project.getId(), ProjectPermission.ARCHIVE_PROJECT);

            assertThatThrownBy(() -> service.archiveProject(context, project.getId()))
                    .isInstanceOf(AuthorizationException.class);
            assertThat(project.isArchived()).isFalse();
            verify(projectRepository, never()).save(any());
            verify(projectEventRecorder, never()).record(any(), any(), any());
        }
    }
}
