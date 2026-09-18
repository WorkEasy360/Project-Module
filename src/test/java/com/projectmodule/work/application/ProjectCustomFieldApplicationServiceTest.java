package com.projectmodule.work.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.projectmodule.common.context.ActorType;
import com.projectmodule.common.context.ImmutableRequestContext;
import com.projectmodule.common.context.RequestContext;
import com.projectmodule.common.exception.AuthorizationException;
import com.projectmodule.common.exception.ConflictException;
import com.projectmodule.common.exception.ResourceNotFoundException;
import com.projectmodule.common.identity.ExternalUserId;
import com.projectmodule.common.identity.OrganizationId;
import com.projectmodule.project.application.ProjectApplicationService;
import com.projectmodule.project.application.authorization.ProjectAuthorizationService;
import com.projectmodule.project.application.authorization.ProjectPermission;
import com.projectmodule.project.domain.Project;
import com.projectmodule.project.domain.ProjectPriority;
import com.projectmodule.work.api.dto.CreateCustomFieldRequest;
import com.projectmodule.work.api.dto.UpdateCustomFieldRequest;
import com.projectmodule.work.domain.CustomFieldValueType;
import com.projectmodule.work.domain.ProjectCustomField;
import com.projectmodule.work.infrastructure.ProjectCustomFieldRepository;
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

@ExtendWith(MockitoExtension.class)
class ProjectCustomFieldApplicationServiceTest {

    private static final OrganizationId ORG = OrganizationId.of(UUID.randomUUID());
    private static final ExternalUserId USER = ExternalUserId.of(UUID.randomUUID());

    @Mock
    private ProjectCustomFieldRepository customFieldRepository;

    @Mock
    private ProjectApplicationService projectApplicationService;

    @Mock
    private ProjectAuthorizationService projectAuthorizationService;

    private ProjectCustomFieldApplicationService service;
    private RequestContext context;
    private Project project;

    @BeforeEach
    void setUp() {
        service = new ProjectCustomFieldApplicationService(
                customFieldRepository, projectApplicationService, projectAuthorizationService);
        context = new ImmutableRequestContext(
                Optional.of(USER), Optional.of(ORG), "corr-1", ActorType.HUMAN);
        project = Project.create(ORG, "Apollo", USER, ProjectPriority.MEDIUM);
    }

    @Nested
    class Create {

        @Test
        @DisplayName("creates a custom field")
        void createsCustomField() {
            when(projectApplicationService.findActiveProjectInCallerOrganization(context, project.getId()))
                    .thenReturn(project);
            when(customFieldRepository.existsByProjectIdAndNameAndArchivedAtIsNull(project.getId(), "Client Code"))
                    .thenReturn(false);
            when(customFieldRepository.save(any(ProjectCustomField.class))).thenAnswer(inv -> inv.getArgument(0));

            CreateCustomFieldRequest request = new CreateCustomFieldRequest(
                    "Client Code", CustomFieldValueType.TEXT, "ACME-01");
            ProjectCustomField created = service.createCustomField(context, project.getId(), request);

            assertThat(created.getName()).isEqualTo("Client Code");
            assertThat(created.getValueType()).isEqualTo(CustomFieldValueType.TEXT);
            assertThat(created.getValue()).isEqualTo("ACME-01");
        }

        @Test
        @DisplayName("denies creation without EDIT_PROJECT")
        void deniesWithoutPermission() {
            when(projectApplicationService.findActiveProjectInCallerOrganization(context, project.getId()))
                    .thenReturn(project);
            doThrow(new AuthorizationException("denied"))
                    .when(projectAuthorizationService)
                    .requirePermission(USER, project.getId(), ProjectPermission.EDIT_PROJECT);

            CreateCustomFieldRequest request = new CreateCustomFieldRequest(
                    "Client Code", CustomFieldValueType.TEXT, "ACME-01");

            assertThatThrownBy(() -> service.createCustomField(context, project.getId(), request))
                    .isInstanceOf(AuthorizationException.class);
            verify(customFieldRepository, never()).save(any());
        }

        @Test
        @DisplayName("denies creation across an organization boundary")
        void deniesCrossOrganization() {
            when(projectApplicationService.findActiveProjectInCallerOrganization(context, project.getId()))
                    .thenThrow(new ResourceNotFoundException("Project", project.getId()));

            CreateCustomFieldRequest request = new CreateCustomFieldRequest(
                    "Client Code", CustomFieldValueType.TEXT, "ACME-01");

            assertThatThrownBy(() -> service.createCustomField(context, project.getId(), request))
                    .isInstanceOf(ResourceNotFoundException.class);
            verify(customFieldRepository, never()).save(any());
        }

        @Test
        @DisplayName("rejects a duplicate active name within the same project")
        void rejectsDuplicateName() {
            when(projectApplicationService.findActiveProjectInCallerOrganization(context, project.getId()))
                    .thenReturn(project);
            when(customFieldRepository.existsByProjectIdAndNameAndArchivedAtIsNull(project.getId(), "Client Code"))
                    .thenReturn(true);

            CreateCustomFieldRequest request = new CreateCustomFieldRequest(
                    "Client Code", CustomFieldValueType.TEXT, "ACME-01");

            assertThatThrownBy(() -> service.createCustomField(context, project.getId(), request))
                    .isInstanceOf(ConflictException.class);
            verify(customFieldRepository, never()).save(any());
        }
    }

    @Nested
    class List_ {

        @Test
        @DisplayName("lists custom fields for the project, requiring VIEW_PROJECT")
        void listsCustomFields() {
            ProjectCustomField field = ProjectCustomField.create(
                    project.getId(), "Client Code", CustomFieldValueType.TEXT, "ACME-01");
            when(projectApplicationService.findActiveProjectInCallerOrganization(context, project.getId()))
                    .thenReturn(project);
            when(customFieldRepository.findByProjectIdAndArchivedAtIsNullOrderByCreatedAtAsc(project.getId()))
                    .thenReturn(List.of(field));

            assertThat(service.listCustomFields(context, project.getId())).containsExactly(field);
            verify(projectAuthorizationService).requirePermission(
                    USER, project.getId(), ProjectPermission.VIEW_PROJECT);
        }
    }

    @Nested
    class Update {

        @Test
        @DisplayName("applies a plain field edit")
        void appliesPlainEdit() {
            ProjectCustomField field = ProjectCustomField.create(
                    project.getId(), "Client Code", CustomFieldValueType.TEXT, "ACME-01");
            when(customFieldRepository.findByIdAndArchivedAtIsNull(field.getId())).thenReturn(Optional.of(field));
            when(projectApplicationService.findActiveProjectInCallerOrganization(context, project.getId()))
                    .thenReturn(project);
            when(customFieldRepository.save(any(ProjectCustomField.class))).thenAnswer(inv -> inv.getArgument(0));

            UpdateCustomFieldRequest request = new UpdateCustomFieldRequest(
                    "Client Reference", "ACME-02", field.getVersion());
            ProjectCustomField updated = service.updateCustomField(context, field.getId(), request);

            assertThat(updated.getName()).isEqualTo("Client Reference");
            assertThat(updated.getValue()).isEqualTo("ACME-02");
        }

        @Test
        @DisplayName("rejects an update against a stale version")
        void rejectsStaleVersion() {
            ProjectCustomField field = ProjectCustomField.create(
                    project.getId(), "Client Code", CustomFieldValueType.TEXT, "ACME-01");
            when(customFieldRepository.findByIdAndArchivedAtIsNull(field.getId())).thenReturn(Optional.of(field));
            when(projectApplicationService.findActiveProjectInCallerOrganization(context, project.getId()))
                    .thenReturn(project);

            UpdateCustomFieldRequest request = new UpdateCustomFieldRequest(
                    "Renamed", null, field.getVersion() + 1);

            assertThatThrownBy(() -> service.updateCustomField(context, field.getId(), request))
                    .isInstanceOf(ConflictException.class);
            verify(customFieldRepository, never()).save(any());
        }

        @Test
        @DisplayName("reports not found for a missing custom field")
        void reportsNotFound() {
            UUID id = UUID.randomUUID();
            when(customFieldRepository.findByIdAndArchivedAtIsNull(id)).thenReturn(Optional.empty());

            UpdateCustomFieldRequest request = new UpdateCustomFieldRequest(null, null, 0L);

            assertThatThrownBy(() -> service.updateCustomField(context, id, request))
                    .isInstanceOf(ResourceNotFoundException.class);
        }

        @Test
        @DisplayName("denies update without EDIT_PROJECT")
        void deniesUpdateWithoutPermission() {
            ProjectCustomField field = ProjectCustomField.create(
                    project.getId(), "Client Code", CustomFieldValueType.TEXT, "ACME-01");
            when(customFieldRepository.findByIdAndArchivedAtIsNull(field.getId())).thenReturn(Optional.of(field));
            when(projectApplicationService.findActiveProjectInCallerOrganization(context, project.getId()))
                    .thenReturn(project);
            doThrow(new AuthorizationException("denied"))
                    .when(projectAuthorizationService)
                    .requirePermission(USER, project.getId(), ProjectPermission.EDIT_PROJECT);

            UpdateCustomFieldRequest request = new UpdateCustomFieldRequest("Renamed", null, field.getVersion());

            assertThatThrownBy(() -> service.updateCustomField(context, field.getId(), request))
                    .isInstanceOf(AuthorizationException.class);
            verify(customFieldRepository, never()).save(any());
        }
    }

    @Nested
    class Archive {

        @Test
        @DisplayName("archives the custom field")
        void archivesCustomField() {
            ProjectCustomField field = ProjectCustomField.create(
                    project.getId(), "Client Code", CustomFieldValueType.TEXT, "ACME-01");
            when(customFieldRepository.findByIdAndArchivedAtIsNull(field.getId())).thenReturn(Optional.of(field));
            when(projectApplicationService.findActiveProjectInCallerOrganization(context, project.getId()))
                    .thenReturn(project);
            when(customFieldRepository.save(any(ProjectCustomField.class))).thenAnswer(inv -> inv.getArgument(0));

            service.archiveCustomField(context, field.getId());

            assertThat(field.isArchived()).isTrue();
        }
    }
}
